package partyos.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.JeopardyFinalStep
import partyos.engine.JeopardyTv
import partyos.engine.PartyEngine
import partyos.engine.PhoneState
import partyos.engine.Screen
import partyos.engine.SecureEntropy
import partyos.engine.SystemClock
import partyos.engine.games.jeopardy.Jeopardy
import partyos.engine.games.jeopardy.JeopardyPack
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sixteen bot phones play three rounds of Imposter against a real server on a real port, dropping and rejoining at random. */
class JeopardySimulatedPartyTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = PartyEngine(SystemClock, SecureEntropy(), GameRegistry(listOf(Jeopardy())))
    private val host = PartyHost(engine, SystemClock, scope)
    private val server = PartyServer.start(host, MemoryStatic, ports = 0..0, bindHost = "127.0.0.1")
    private val base = "http://127.0.0.1:${server.port}"
    private val http = HttpClient(CIO) { install(WebSockets) }

    /** Every clue text in the pack mapped to its answer, so bots can "know" the right answer. */
    private val answers: Map<String, String> = JeopardyPack.core().let { p ->
        p.categories.flatMap { it.clues }.associate { it.clue to it.answer } + p.finals.associate { it.clue to it.answer }
    }

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

    private inner class Bot(val name: String, val token: String, @Volatile var dropRate: Double, seed: Int) {
        private val rnd = Random(seed)
        var last: PhoneState? = null
        @Volatile var stop = false
        @Volatile var sessions = 0

        suspend fun run() {
            var n = 0
            while (!stop) {
                sessions++
                http.webSocket("ws://127.0.0.1:${server.port}/ws?token=$token") {
                    var seenRound = -1
                    for (frame in incoming) {
                        val msg = PartyJson.decodeFromString(ServerMsg.serializer(), (frame as Frame.Text).readText())
                        if (msg !is ServerMsg.View) continue
                        val v = msg.view.also { last = it }
                        if (stop) break
                        if (v.round != seenRound) {
                            seenRound = v.round
                            if (v.gameId != null && rnd.nextDouble() < dropRate) break // drop the connection mid-phase
                        }
                        val payload: JsonObject? = when (val s = v.screen) {
                            is Screen.Tutorial -> if (!s.acknowledged) obj("kind" to "ack") else null
                            is Screen.Board -> if (s.canPick) {
                                val open = s.cells.filter { !it.used }
                                if (open.isEmpty()) null else obj("kind" to "pick", "cell" to open.random(rnd).id)
                            } else null
                            is Screen.Buzzer -> if (s.state == "open" && rnd.nextDouble() < 0.67) obj("kind" to "buzz") else null
                            is Screen.NumberEntry -> if (s.value == null) JsonObject(mapOf("kind" to JsonPrimitive(s.kind), "value" to JsonPrimitive(100 * (1 + rnd.nextInt(5))))) else null
                            is Screen.TextEntry -> if (s.value == null) {
                                val known = answers[s.prompt]
                                val text = if (known != null && rnd.nextDouble() < 0.6) "What is $known?" else "nonsense${rnd.nextInt(1000)}"
                                obj("kind" to s.kind, "text" to text)
                            } else null
                            else -> null
                        }
                        if (payload != null && !v.paused) {
                            val m = ClientMsg.Action("$name-${n++}", v.round, payload)
                            send(Frame.Text(PartyJson.encodeToString(ClientMsg.serializer(), m)))
                        }
                    }
                }
                if (!stop) delay(50L + rnd.nextLong(150))
            }
        }
    }

    @Test fun sixteenFlakyPhonesFinishAShortShowWithConsistentScores() = runBlocking {
        val bots = (1..16).map { Bot("Bot$it", join("Bot$it"), dropRate = 0.2, seed = it) }
        bots.map { b -> scope.async { b.run() } }
        withTimeout(20_000) { host.tv.first { it.players.count { p -> p.connected } == 16 } }

        // What the TV showed: each clue's reveal (once, by phase sequence) and each Final Jeopardy step (once, by player).
        val reveals = ConcurrentHashMap<Int, JeopardyTv>()
        val finalSteps = ConcurrentHashMap<String, JeopardyFinalStep>()
        val driver = scope.launch {
            var skipped = -1
            host.tv.collect { tv ->
                val st = tv.stage ?: return@collect
                val g = st.game as? JeopardyTv
                if (g != null && g.phase == "reveal") reveals[st.phaseSeq] = g
                if (g != null && g.phase == "final_reveal") g.final?.steps?.forEach { finalSteps[it.id.v] = it }
                if (st.paused && tv.players.count { it.connected } >= 2) host.mutate { host(HostCmd.Resume) }
                // Speed the show up: the intro, the category card and the reveal steps do not need their full time.
                if (g != null && g.phase in setOf("intro", "clue", "reveal", "break", "final_category", "final_reveal", "podium") && st.phaseSeq != skipped) {
                    skipped = st.phaseSeq
                    delay(30)
                    host.mutate { host(HostCmd.SkipPhase) }
                }
                // Bots ring in within a moment or not at all: do not wait out the whole buzz window.
                if (g != null && g.phase == "buzz" && st.phaseSeq != skipped) {
                    val seq = st.phaseSeq
                    skipped = seq
                    scope.launch { delay(1_500); if (host.tv.value.stage?.phaseSeq == seq) host.mutate { host(HostCmd.SkipPhase) } }
                }
            }
        }
        assertEquals(partyos.engine.ActionResult.Ack, host.mutate { host(HostCmd.StartGame("jeopardy", mapOf("show" to 0))) })

        val result = withTimeout(300_000) { host.tv.first { it.lastResult != null }.lastResult!! }
        driver.cancel()
        bots.forEach { it.dropRate = 0.0 }

        // Every bot, once reconnected, converges on the same final scoreboard.
        withTimeout(20_000) {
            while (bots.any { b -> b.last?.let { it.gameId == null && it.scores == result.standings } != true }) delay(50)
        }
        bots.forEach { it.stop = true }
        assertTrue(bots.sumOf { it.sessions } > 16, "expected some reconnects")

        // Scores equal a recomputation from what the TV showed: every clue's deltas plus every Final Jeopardy delta.
        assertTrue(reveals.size >= 10, "the show should have played at least ten clues, saw ${reveals.size}")
        val expected = HashMap<String, Int>()
        reveals.values.forEach { r -> r.deltas.forEach { d -> expected.merge(d.id.v, d.points, Int::plus) } }
        finalSteps.values.forEach { expected.merge(it.id.v, it.delta, Int::plus) }
        val actual = result.standings.associate { it.id.v to it.score }.filterValues { it != 0 }
        assertEquals(expected.filterValues { it != 0 }, actual)
    }
}
