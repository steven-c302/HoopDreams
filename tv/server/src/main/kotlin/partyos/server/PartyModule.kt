package partyos.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import partyos.engine.ActionResult
import partyos.engine.Clock
import partyos.engine.JoinError
import partyos.engine.JoinResult
import partyos.engine.PlayerId
import partyos.engine.Role
import partyos.engine.SecureEntropy
import partyos.engine.SystemClock
import partyos.engine.sha256
import java.util.concurrent.ConcurrentHashMap

data class ServerConfig(
    val clock: Clock = SystemClock,
    val pingTimeoutMs: Long = 10_000,
    val actionsPerSecond: Int = 20,
    val maxFrameBytes: Long = 64 * 1024,
    val pinMaxFailures: Int = 5,
    val pinLockMs: Long = 60_000,
)

private const val MAX_BODY = 4_096L

/** Refuses any request that does not come from a private (LAN/loopback) address. */
val LanOnly = createApplicationPlugin("LanOnly") {
    onCall { call ->
        if (!isPrivateAddress(call.request.origin.remoteAddress)) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("LAN_ONLY"))
        }
    }
}

fun Application.partyModule(host: PartyHost, static: StaticFiles, cfg: ServerConfig = ServerConfig()) {
    val lockout = PinLockout(cfg.pinMaxFailures, cfg.pinLockMs, cfg.clock::now)
    val hostTokens = ConcurrentHashMap.newKeySet<String>()
    val entropy = SecureEntropy()

    install(ContentNegotiation) { json(PartyJson) }
    install(WebSockets) {
        maxFrameSize = cfg.maxFrameBytes
    }
    install(LanOnly)

    routing {
        get("/healthz") { call.respondText("ok") }
        get("/api/games") { call.respond(host.games) }

        post("/api/join") {
            val req = call.receiveSmall<JoinRequest>() ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST"))
            val role = if (req.spectator) Role.SPECTATOR else Role.PLAYER
            when (val r = host.mutate { join(req.room, req.name, req.avatar, role) }) {
                is JoinResult.Joined -> call.respond(JoinResponse(r.player.id, r.token))
                is JoinResult.Failed -> call.respond(
                    when (r.error) {
                        JoinError.WRONG_ROOM, JoinError.NAME_TAKEN -> HttpStatusCode.Conflict
                        JoinError.FULL -> HttpStatusCode.Forbidden
                        JoinError.BAD_NAME -> HttpStatusCode.BadRequest
                    },
                    ErrorResponse(r.error.name),
                )
            }
        }

        post("/api/host/login") {
            val ip = call.request.origin.remoteAddress
            if (lockout.locked(ip)) {
                return@post call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("LOCKED", lockout.retryAfterSec(ip).toInt()))
            }
            val req = call.receiveSmall<PinRequest>() ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST"))
            if (host.read { checkPin(req.pin) }) {
                lockout.succeed(ip)
                val token = entropy.token()
                hostTokens += sha256(token)
                call.respond(HostLoginResponse(token))
            } else {
                lockout.fail(ip)
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("BAD_PIN"))
            }
        }

        webSocket("/ws") {
            val pid = call.request.queryParameters["token"]?.let { t -> host.read { resolve(t) } }
            val isHost = call.request.queryParameters["host"]?.let { sha256(it) in hostTokens } == true
            if (pid == null && !isHost) {
                send(ServerMsg.Bye("BAD_TOKEN"))
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "BAD_TOKEN"))
                return@webSocket
            }
            PartySession(this, host, cfg, pid, isHost).run()
        }

        get("/assets/{path...}") {
            val segments = call.parameters.getAll("path").orEmpty()
            if (segments.isEmpty() || segments.any { it == ".." || it.contains('/') || it.contains('\\') }) {
                return@get call.respond(HttpStatusCode.NotFound)
            }
            val path = "assets/" + segments.joinToString("/")
            val bytes = static.read(path) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respondBytes(bytes, contentTypeFor(path))
        }

        get("/{...}") {
            val bytes = static.read("index.html") ?: return@get call.respondText("Controller not bundled", status = HttpStatusCode.NotFound)
            call.respondBytes(bytes, contentTypeFor("index.html"))
        }
    }
}

private suspend inline fun <reified T : Any> RoutingCall.receiveSmall(): T? {
    if ((request.contentLength() ?: 0) > MAX_BODY) return null
    return runCatching { receive<T>() }.getOrNull()
}

internal suspend fun DefaultWebSocketServerSession.send(m: ServerMsg) =
    outgoing.send(Frame.Text(PartyJson.encodeToString(ServerMsg.serializer(), m)))

/** One phone or host socket: pushes views on every commit, applies actions, and watches the heartbeat. */
private class PartySession(
    private val ws: DefaultWebSocketServerSession,
    private val host: PartyHost,
    private val cfg: ServerConfig,
    private val pid: PlayerId?,
    private val isHost: Boolean,
) {
    @Volatile private var lastSeen = cfg.clock.now()
    private val bucket = TokenBucket(cfg.actionsPerSecond, cfg.actionsPerSecond.toDouble(), cfg.clock::now)

    suspend fun run() {
        val role = pid?.let { id -> host.read { player(id)?.role } }
        ws.send(ServerMsg.Welcome(pid, role, isHost))
        pid?.let { host.connected(it) }
        val sender = ws.launch { host.version.collect { seq -> push(seq) } }
        val watchdog = ws.launch {
            while (isActive) {
                delay(1_000)
                if (cfg.clock.now() - lastSeen > cfg.pingTimeoutMs) {
                    ws.close(CloseReason(CloseReason.Codes.GOING_AWAY, "TIMEOUT"))
                    break
                }
            }
        }
        try {
            for (frame in ws.incoming) {
                if (frame !is Frame.Text) continue
                lastSeen = cfg.clock.now()
                val msg = runCatching { PartyJson.decodeFromString(ClientMsg.serializer(), frame.readText()) }.getOrNull()
                if (msg == null) {
                    ws.send(ServerMsg.Reject("", "BAD_MESSAGE"))
                    continue
                }
                handle(msg)
            }
        } finally {
            sender.cancel()
            watchdog.cancel()
            pid?.let { withContext(NonCancellable) { host.disconnected(it) } }
        }
    }

    private suspend fun push(seq: Long) {
        if (pid != null) {
            val view = host.read { if (player(pid) == null) null else phoneState(pid) }
            if (view == null) {
                ws.send(ServerMsg.Bye("KICKED"))
                ws.close(CloseReason(CloseReason.Codes.NORMAL, "KICKED"))
                return
            }
            ws.send(ServerMsg.View(seq, view))
        }
        if (isHost) ws.send(ServerMsg.Tv(seq, host.tv.value))
    }

    private suspend fun handle(msg: ClientMsg) {
        when (msg) {
            ClientMsg.Ping -> ws.send(ServerMsg.Pong)
            is ClientMsg.Hello -> Unit
            is ClientMsg.Action -> reply(msg.id) {
                if (pid == null) ActionResult.Rejected("NOT_PLAYER")
                else host.mutate { action(pid, msg.id, msg.round, msg.payload) }
            }
            is ClientMsg.Host -> reply(msg.id) {
                if (!isHost) ActionResult.Rejected("NOT_HOST") else host.mutate { host(msg.cmd.toCmd()) }
            }
        }
    }

    private suspend fun reply(id: String, block: suspend () -> ActionResult) {
        val result = if (!bucket.tryTake()) ActionResult.Rejected("RATE_LIMIT") else block()
        ws.send(
            when (result) {
                ActionResult.Ack -> ServerMsg.Ack(id)
                is ActionResult.Rejected -> ServerMsg.Reject(id, result.code)
            },
        )
    }
}
