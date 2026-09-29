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
import partyos.engine.ImposterTv
import partyos.engine.PartyEngine
import partyos.engine.PhoneState
import partyos.engine.Screen
import partyos.engine.SecureEntropy
import partyos.engine.SystemClock
import partyos.engine.games.imposter.Imposter
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sixteen bot phones play three rounds of Imposter against a real server on a real port, dropping and rejoining at random. */
class ImposterSimulatedPartyTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = PartyEngine(SystemClock, SecureEntropy(), GameRegistry(listOf(Imposter())))
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
                            is Screen.Secret -> {
                                val input = s.input
                                val kind = s.kind
                                when {
                                    input != null -> if (input.value == null) obj("kind" to input.kind, "text" to "clue${rnd.nextInt(100000)}") else null
                                    kind != null && !s.acknowledged -> obj("kind" to kind)
                                    else -> null
                                }
                            }
                            is Screen.ChoiceList -> if (s.selected == null) obj("kind" to s.kind, "option" to s.options.random(rnd).id) else null
                            is Screen.TextEntry -> if (s.value == null) obj("kind" to s.kind, "text" to "guess${rnd.nextInt(1000)}") else null
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

    @Test fun sixteenFlakyPhonesFinishAGameWithConsistentScores() = runBlocking {
        val bots = (1..16).map { Bot("Bot$it", join("Bot$it"), dropRate = 0.2, seed = it) }
        bots.map { b -> scope.async { b.run() } }
        withTimeout(20_000) { host.tv.first { it.players.count { p -> p.connected } == 16 } }

        // What the TV showed, per round: the vote and its outcome, and the recap with the guesses.
        val outcomes = ConcurrentHashMap<Int, ImposterTv>()
        val recaps = ConcurrentHashMap<Int, ImposterTv>()
        val driver = scope.launch {
            var skipped = -1
            host.tv.collect { tv ->
                val st = tv.stage ?: return@collect
                val g = st.game as? ImposterTv
                if (g != null && g.phase in setOf("result", "guess", "scores")) outcomes[g.round] = g
                if (g != null && g.phase == "scores") recaps[g.round] = g
                if (st.paused && tv.players.count { it.connected } >= 2) host.mutate { host(HostCmd.Resume) }
                if (g != null && g.phase in setOf("discuss", "result", "scores", "podium") && st.phaseSeq != skipped) {
                    skipped = st.phaseSeq
                    delay(30)
                    host.mutate { host(HostCmd.SkipPhase) }
                }
            }
        }
        assertEquals(partyos.engine.ActionResult.Ack, host.mutate { host(HostCmd.StartGame("imposter", mapOf("rounds" to 3))) })

        val result = withTimeout(150_000) { host.tv.first { it.lastResult != null }.lastResult!! }
        driver.cancel()
        bots.forEach { it.dropRate = 0.0 }

        // Every bot, once reconnected, converges on the same final scoreboard.
        withTimeout(20_000) {
            while (bots.any { b -> b.last?.let { it.gameId == null && it.scores == result.standings } != true }) delay(50)
        }
        bots.forEach { it.stop = true }
        assertTrue(bots.sumOf { it.sessions } > 16, "expected some reconnects")

        // Scores equal a recomputation from what the TV showed: crew who named an imposter, imposters nobody accused,
        // and accused imposters who guessed the word; the last round counts double.
        assertEquals(setOf(1, 2, 3), outcomes.keys)
        assertEquals(setOf(1, 2, 3), recaps.keys)
        val expected = HashMap<String, Int>()
        for ((round, r) in outcomes) {
            val mult = if (r.finalRound) 2 else 1
            val imposters = r.imposters.map { it.v }.toSet()
            val accused = r.accused.map { it.v }.toSet()
            for (vote in r.votes) if (vote.voter.v !in imposters && vote.suspect.v in imposters) expected.merge(vote.voter.v, 1000 * mult, Int::plus)
            for (i in imposters) if (i !in accused) expected.merge(i, 1500 * mult, Int::plus)
            recaps.getValue(round).guesses.filter { it.right }.forEach { expected.merge(it.id.v, 1000 * mult, Int::plus) }
        }
        val actual = result.standings.associate { it.id.v to it.score }.filterValues { it != 0 }
        assertEquals(expected.filterValues { it != 0 }, actual)
    }
}
