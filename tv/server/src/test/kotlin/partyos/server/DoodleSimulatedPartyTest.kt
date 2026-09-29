package partyos.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import partyos.engine.ActionResult
import partyos.engine.DoodleDelta
import partyos.engine.DoodleTv
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.PartyEngine
import partyos.engine.PhoneState
import partyos.engine.Screen
import partyos.engine.SecureEntropy
import partyos.engine.SystemClock
import partyos.engine.games.doodle.Doodle
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sixteen bot phones play three turns of Doodle Dash against a real server on a real port: each turn one bot draws real
 * strokes over the ink channel while the others guess, and bots drop and rejoin at random. A TV socket listens to the ink.
 */
class DoodleSimulatedPartyTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = PartyEngine(SystemClock, SecureEntropy(), GameRegistry(listOf(Doodle())))
    private val host = PartyHost(engine, SystemClock, scope)
    private val server = PartyServer.start(host, MemoryStatic, ports = 0..0, bindHost = "127.0.0.1")
    private val base = "http://127.0.0.1:${server.port}"
    private val http = HttpClient(CIO) { install(WebSockets) }

    @AfterTest fun stop() {
        http.close(); server.stop()
    }

    private suspend fun join(name: String): String = tokenOf(
        http.post("$base/api/join") {
            contentType(ContentType.Application.Json)
            setBody("""{"room":"${host.tv.value.roomCode}","name":"$name","avatar":{"emoji":"🤖","color":"#445566"}}""")
        }.bodyAsText(),
    )

    private fun obj(vararg kv: Pair<String, String>) = JsonObject(kv.associate { it.first to JsonPrimitive(it.second) })

    /** The word the latest drawer was given: stands in for a guesser who can read a drawing. */
    @Volatile private var secret: String? = null
    private val inked = ConcurrentHashMap.newKeySet<Int>()

    private inner class Bot(val name: String, val token: String, @Volatile var dropRate: Double, seed: Int) {
        private val rnd = Random(seed)
        private var n = 0
        @Volatile var last: PhoneState? = null
        @Volatile var stop = false
        @Volatile var sessions = 0

        private suspend fun DefaultClientWebSocketSession.action(round: Int, payload: JsonObject) =
            send(Frame.Text(PartyJson.encodeToString(ClientMsg.serializer(), ClientMsg.Action("$name-${n++}", round, payload))))

        private suspend fun DefaultClientWebSocketSession.draw(round: Int) {
            val ops = listOf(
                InkOp.Start(1, 0, 1, 100, 100, 50), InkOp.Pts(1, listOf(150, 140, 50, 200, 180, 50)), InkOp.End(1),
                InkOp.Start(2, 1, 1, 300, 300, 50), InkOp.Pts(2, listOf(350, 340, 50)), InkOp.End(2),
                InkOp.Start(3, 2, 2, 500, 200, 50), InkOp.Pts(3, listOf(520, 260, 50, 560, 300, 50)), InkOp.End(3),
            )
            send(Frame.Text(PartyJson.encodeToString(ClientMsg.serializer(), ClientMsg.Ink(round, ops))))
            inked += round
        }

        suspend fun run() {
            while (!stop) {
                sessions++
                try {
                    http.webSocket("ws://127.0.0.1:${server.port}/ws?token=$token") {
                        var dropped = false
                        while (!stop && !dropped) {
                            val frame = withTimeoutOrNull(150) { incoming.receive() }
                            if (frame is Frame.Text) {
                                val m = PartyJson.decodeFromString(ServerMsg.serializer(), frame.readText())
                                if (m is ServerMsg.View) last = m.view
                            }
                            val v = last ?: continue
                            if (v.gameId == null || v.paused) continue
                            if (rnd.nextDouble() < dropRate) { dropped = true; continue } // drop the connection mid-turn
                            when (val s = v.screen) {
                                is Screen.Tutorial -> if (!s.acknowledged) action(v.round, obj("kind" to "ack"))
                                is Screen.ChoiceList -> if (s.kind == "pick" && s.selected == null) action(v.round, obj("kind" to "pick", "option" to s.options.random(rnd).id))
                                is Screen.Draw -> {
                                    secret = s.word
                                    if (v.round !in inked) draw(v.round)
                                }
                                is Screen.Guess -> if (!s.solved && rnd.nextDouble() < 0.3) {
                                    val text = secret?.takeIf { rnd.nextDouble() < 0.5 } ?: "nope${rnd.nextInt(1000)}"
                                    action(v.round, obj("kind" to s.kind, "text" to text))
                                }
                                else -> Unit
                            }
                        }
                    }
                } catch (_: Exception) {
                    // the socket closed under us: reconnect
                }
                if (!stop) delay(50L + rnd.nextLong(150))
            }
        }
    }

    @Test fun sixteenFlakyPhonesFinishAGameWithConsistentScoresAndInk() = runBlocking {
        val bots = (1..16).map { Bot("Bot$it", join("Bot$it"), dropRate = 0.004, seed = it) }
        bots.map { b -> scope.async { b.run() } }
        withTimeout(20_000) { host.tv.first { it.players.count { p -> p.connected } == 16 } }

        // A TV socket: it must see every turn's ink arrive, in order.
        val tvInk = ConcurrentLinkedQueue<ServerMsg.Ink>()
        val hostToken = host.issueHostToken()
        val tvSocket = scope.launch {
            http.webSocket("ws://127.0.0.1:${server.port}/ws?host=$hostToken") {
                for (frame in incoming) {
                    val m = PartyJson.decodeFromString(ServerMsg.serializer(), (frame as Frame.Text).readText())
                    if (m is ServerMsg.Ink) tvInk += m
                }
            }
        }

        // What the TV showed: each turn's deltas (from the scores screen) and how many strokes had arrived by the reveal.
        val deltas = ConcurrentHashMap<Int, List<DoodleDelta>>()
        val strokesAtReveal = ConcurrentHashMap<Int, Int>()
        val driver = scope.launch {
            val skipped = HashSet<Int>()
            var drawTurn = -1
            var drawSince = 0L
            while (isActive) {
                delay(100)
                val tv = host.tv.value
                val st = tv.stage ?: continue
                if (st.paused && tv.players.count { it.connected } >= 2) { host.mutate { host(HostCmd.Resume) }; continue }
                val g = st.game as? DoodleTv ?: continue
                suspend fun strokes() = host.inkSync().turns.firstOrNull { it.turn == g.turn }?.strokes?.size ?: 0
                if (g.phase == "scores") deltas[g.turn] = g.deltas
                if (g.phase == "reveal") strokesAtReveal[g.turn] = strokes()
                when (g.phase) {
                    "reveal", "scores", "podium" -> if (skipped.add(st.phaseSeq)) { delay(30); host.mutate { host(HostCmd.SkipPhase) } }
                    "draw" -> {
                        if (drawTurn != g.turn) { drawTurn = g.turn; drawSince = System.currentTimeMillis() }
                        val waited = System.currentTimeMillis() - drawSince
                        // Let the drawing land, then walk the hint stages quickly; each skip is one stage.
                        if ((strokes() >= 3 && waited > 1_500) || waited > 12_000) {
                            host.mutate { host(HostCmd.SkipPhase) }
                            drawSince = System.currentTimeMillis()
                        }
                    }
                }
            }
        }
        assertEquals(ActionResult.Ack, host.mutate { host(HostCmd.StartGame("doodle", mapOf("rounds" to 3))) })

        val result = withTimeout(180_000) { host.tv.first { it.lastResult != null }.lastResult!! }
        driver.cancel()
        bots.forEach { it.dropRate = 0.0 }

        // Every bot, once reconnected, converges on the same final scoreboard.
        withTimeout(20_000) {
            while (bots.any { b -> b.last?.let { it.gameId == null && it.scores == result.standings } != true }) delay(50)
        }
        bots.forEach { it.stop = true }
        tvSocket.cancel()
        assertTrue(bots.sumOf { it.sessions } > 16, "expected some reconnects")

        // Scores equal a recomputation from what the TV showed: every point is in a turn's deltas.
        assertEquals(setOf(1, 2, 3), deltas.keys)
        val expected = HashMap<String, Int>()
        for (turn in deltas.values) for (d in turn) expected.merge(d.id.v, d.points, Int::plus)
        val actual = result.standings.associate { it.id.v to it.score }.filterValues { it != 0 }
        assertEquals(expected.filterValues { it != 0 }, actual)

        // The ink: each played turn's three strokes reached the server's board, and the TV socket saw them in order.
        assertEquals(setOf(1, 2, 3), strokesAtReveal.keys)
        assertTrue(strokesAtReveal.values.all { it == 3 }, "strokes per turn: $strokesAtReveal")
        val seen = tvInk.toList()
        assertEquals(setOf(1, 2, 3), seen.map { it.turn }.toSet())
        assertEquals(seen.map { it.n }, seen.map { it.n }.sorted())
        assertEquals(seen.size, seen.map { it.n }.toSet().size)
    }
}
