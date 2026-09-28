package partyos.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.flow.first
import io.ktor.server.response.header
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
import kotlinx.coroutines.Dispatchers
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
import partyos.engine.SystemClock

data class ServerConfig(
    val clock: Clock = SystemClock,
    val pingTimeoutMs: Long = 10_000,
    val actionsPerSecond: Int = 20,
    val maxFrameBytes: Long = 64 * 1024,
    val pinMaxFailures: Int = 5,
    val pinLockMs: Long = 60_000,
    /** A music app the TV can steer (Spotify on the Mac running the show); null where there isn't one. */
    val music: MusicPlayer? = null,
    /** Selfies and photos players picked as their face. */
    val photos: PhotoStore = PhotoStore(),
)

/** The caller is this machine (the browser TV), never a phone on the Wi-Fi. */
private fun RoutingCall.fromLoopback(): Boolean {
    val remote = request.origin.remoteAddress
    return remote == "localhost" || runCatching { java.net.InetAddress.getByName(remote).isLoopbackAddress }.getOrDefault(false)
}

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
            when (val r = host.mutate { join(req.room, req.name, req.avatar, role, req.water) }) {
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

        // A selfie or photo for a face, uploaded before joining: the phone gets an id and joins with face "i:<id>".
        post("/api/avatar") {
            if ((call.request.contentLength() ?: 0) > PhotoStore.MAX_BYTES) {
                return@post call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("PHOTO_TOO_BIG"))
            }
            val bytes = call.receiveChannel().readRemaining(PhotoStore.MAX_BYTES + 1L).readByteArray()
            when {
                bytes.size > PhotoStore.MAX_BYTES -> call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("PHOTO_TOO_BIG"))
                !PhotoStore.looksLikeJpeg(bytes) -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("NOT_A_PHOTO"))
                else -> call.respond(PhotoResponse(cfg.photos.put(bytes)))
            }
        }

        get("/api/avatar/{file}") {
            val id = call.parameters["file"]?.removeSuffix(".jpg")?.takeIf { PhotoStore.ID.matches(it) }
            val bytes = id?.let { cfg.photos.get(it) } ?: return@get call.respond(HttpStatusCode.NotFound)
            // Content-addressed, so a URL's bytes never change.
            call.response.header("Cache-Control", "public, max-age=31536000, immutable")
            call.response.header("X-Content-Type-Options", "nosniff")
            call.respondBytes(bytes, ContentType.Image.JPEG)
        }

        // Water tonight: a player's own switch, any time.
        post("/api/water") {
            val req = call.receiveSmall<WaterRequest>() ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST"))
            val ok = host.mutate { resolve(req.token)?.let { setWater(it, req.water) } ?: false }
            if (ok) call.respond(HttpStatusCode.OK, ErrorResponse("OK")) else call.respond(HttpStatusCode.Unauthorized, ErrorResponse("BAD_TOKEN"))
        }

        post("/api/role") {
            val req = call.receiveSmall<RoleRequest>() ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST"))
            val result = host.mutate {
                val id = resolve(req.token) ?: return@mutate "BAD_TOKEN"
                setRole(id, req.role)
            }
            when (result) {
                null -> call.respond(HttpStatusCode.OK, ErrorResponse("OK"))
                "BAD_TOKEN" -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse(result))
                "FULL" -> call.respond(HttpStatusCode.Forbidden, ErrorResponse(result))
                else -> call.respond(HttpStatusCode.Conflict, ErrorResponse(result))
            }
        }

        post("/api/host/login") {
            val ip = call.request.origin.remoteAddress
            if (!lockout.tryAttempt(ip)) {
                return@post call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("LOCKED", lockout.retryAfterSec(ip).toInt()))
            }
            val req = call.receiveSmall<PinRequest>() ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST"))
            if (host.read { checkPin(req.pin) }) {
                lockout.succeed(ip)
                call.respond(HostLoginResponse(host.issueHostToken()))
            } else {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("BAD_PIN"))
            }
        }

        // A browser TV running on the host machine (the Mac mirrored to a TV) gets host rights without a PIN.
        // Only loopback callers qualify, so phones on the Wi-Fi can never use it.
        get("/api/tv/session") {
            if (!call.fromLoopback()) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("LOCAL_ONLY"))
            val room = host.tv.value.roomCode
            val joinUrl = lanAddresses().firstOrNull()?.let { "http://$it:${call.request.local.localPort}/j/$room" }
            call.respond(TvSessionResponse(host.issueHostToken(), room, joinUrl))
        }

        // Music under the show (Spotify on the Mac). Like the TV session, only the machine running the show may steer it.
        get("/api/music") {
            if (!call.fromLoopback()) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("LOCAL_ONLY"))
            val player = cfg.music ?: return@get call.respond(MusicStatus(available = false))
            call.respond(withContext(Dispatchers.IO) { player.status() })
        }
        post("/api/music/{cmd}") {
            if (!call.fromLoopback()) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("LOCAL_ONLY"))
            val player = cfg.music ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("NO_MUSIC"))
            val cmd = call.parameters["cmd"]?.takeIf { it in MusicPlayer.COMMANDS }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_COMMAND"))
            val ok = withContext(Dispatchers.IO) { player.command(cmd) }
            if (!ok) return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("MUSIC_FAILED"))
            call.respond(withContext(Dispatchers.IO) { player.status() })
        }

        webSocket("/ws") {
            val pid = call.request.queryParameters["token"]?.let { t -> host.read { resolve(t) } }
            val isHost = call.request.queryParameters["host"]?.let { host.isHostToken(it) } == true
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

/** Reads at most [MAX_BODY] bytes whether or not the client sent a Content-Length, then decodes JSON. */
private suspend inline fun <reified T : Any> RoutingCall.receiveSmall(): T? {
    if ((request.contentLength() ?: 0) > MAX_BODY) return null
    val bytes = receiveChannel().readRemaining(MAX_BODY + 1).readByteArray()
    if (bytes.size > MAX_BODY) return null
    return runCatching { PartyJson.decodeFromString<T>(bytes.decodeToString()) }.getOrNull()
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
        val pinWatch = if (!isHost) null else ws.launch {
            val gen = host.pinGeneration.value
            host.pinGeneration.first { it != gen }
            ws.send(ServerMsg.Bye("PIN_CHANGED"))
            ws.close(CloseReason(CloseReason.Codes.NORMAL, "PIN_CHANGED"))
        }
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
            pinWatch?.cancel()
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
        val id = when (msg) {
            is ClientMsg.Action -> msg.id
            is ClientMsg.Host -> msg.id
            else -> null
        }
        if (id != null && !isValidMessageId(id)) {
            ws.send(ServerMsg.Reject(id.take(64), "BAD_ID"))
            return
        }
        when (msg) {
            ClientMsg.Ping -> ws.send(ServerMsg.Pong)
            is ClientMsg.Hello -> Unit
            is ClientMsg.Action -> reply(msg.id) {
                if (pid == null) ActionResult.Rejected("NOT_PLAYER")
                else host.mutate { action(pid, msg.id, msg.round, msg.payload) }
            }
            is ClientMsg.Host -> reply(msg.id) {
                when {
                    isHost -> host.hostCommand(msg.id, msg.cmd.toCmd())
                    // Phones may run the show only while they hold the crown; the engine checks and limits it.
                    pid != null -> host.captainCommand(msg.id, pid, msg.cmd.toCmd())
                    else -> ActionResult.Rejected("NOT_HOST")
                }
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

/** This machine's private IPv4 addresses, most likely Wi-Fi first. */
fun lanAddresses(): List<String> = runCatching {
    java.net.NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .sortedBy { if (it.name.startsWith("en") || it.name.startsWith("wlan")) 0 else 1 }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<java.net.Inet4Address>()
        .filter { it.isSiteLocalAddress }
        .map { it.hostAddress }
}.getOrDefault(emptyList())
