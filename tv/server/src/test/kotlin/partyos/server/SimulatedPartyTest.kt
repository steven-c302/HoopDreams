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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import partyos.engine.BluffTv
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.PartyEngine
import partyos.engine.PhoneState
import partyos.engine.Screen
import partyos.engine.SecureEntropy
import partyos.engine.SystemClock
import partyos.engine.games.bluff.BluffBattle
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sixteen bot phones play Bluff Battle against a real server on a real port, dropping and rejoining at random. */
class SimulatedPartyTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = PartyEngine(SystemClock, SecureEntropy(), GameRegistry(listOf(BluffBattle())))
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

    private inner class Bot(val name: String, val token: String, @Volatile var dropRate: Double, seed: Int, val acts: Boolean = true) {
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
                            is Screen.TextEntry -> if (acts && s.value == null) obj("kind" to "write", "text" to "$name says ${rnd.nextInt(1000)}") else null
                            is Screen.ChoiceList -> if (acts && s.selected == null) obj("kind" to "pick", "option" to s.options.random(rnd).id) else null
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

    private fun obj(vararg kv: Pair<String, String>) = JsonObject(kv.associate { it.first to JsonPrimitive(it.second) })

    @Test fun sixteenFlakyPhonesFinishAGameWithConsistentScores() = runBlocking {
        val bots = (1..16).map { Bot("Bot$it", join("Bot$it"), dropRate = 0.2, seed = it) }
        val jobs = bots.map { b -> scope.async { b.run() } }
        withTimeout(20_000) { host.tv.first { it.players.count { p -> p.connected } == 16 } }

        // Reveal-time record of who picked what, per round, to recompute scores independently.
        val reveals = ConcurrentHashMap<Int, BluffTv>()
        val driver = scope.launch {
            var skipped = -1
            host.tv.collect { tv ->
                val st = tv.stage ?: return@collect
                val g = st.game as? BluffTv
                if (g?.phase == "reveal") reveals[g.round] = g
                if (st.paused && tv.players.count { it.connected } >= 2) host.mutate { host(HostCmd.Resume) }
                if (g != null && g.phase in setOf("reveal", "scores", "podium") && st.phaseSeq != skipped) {
                    skipped = st.phaseSeq
                    delay(30)
                    host.mutate { host(HostCmd.SkipPhase) }
                }
            }
        }
        assertEquals(partyos.engine.ActionResult.Ack, host.mutate { host(HostCmd.StartGame("bluff", mapOf("rounds" to 3))) })

        val result = withTimeout(90_000) { host.tv.first { it.lastResult != null }.lastResult!! }
        driver.cancel()
        bots.forEach { it.dropRate = 0.0 }

        // Every bot, once reconnected, converges on the same final scoreboard.
        withTimeout(20_000) {
            while (bots.any { b -> b.last?.let { it.gameId == null && it.scores == result.standings } != true }) delay(50)
        }
        bots.forEach { it.stop = true }
        assertTrue(bots.sumOf { it.sessions } > 16, "expected some reconnects")

        // Scores equal a recomputation from what the reveal showed.
        assertEquals(3, reveals.size)
        val expected = HashMap<String, Int>()
        for ((round, r) in reveals) {
            val mult = if (round == 3) 2 else 1
            for (item in r.reveal) {
                if (item.kind == "truth") item.fooled.forEach { expected.merge(it, 1000 * mult, Int::plus) }
                else item.authors.forEach { a -> expected.merge(a, 500 * mult * item.fooled.size, Int::plus) }
            }
        }
        assertEquals(expected.filterValues { it > 0 }, result.standings.associate { it.name to it.score }.filterValues { it > 0 })
        jobs.forEach { it.cancel() }
    }

    @Test fun lastSurvivorEndsThePhaseAndTheGameAutoPauses() = runBlocking {
        // Only Bot1 writes; the other fifteen ack the tutorial and then sit idle.
        val bots = (1..16).map { Bot("Bot$it", join("Bot$it"), dropRate = 0.0, seed = it, acts = it == 1) }
        val jobs = bots.map { b -> scope.async { b.run() } }
        withTimeout(20_000) { host.tv.first { it.players.count { p -> p.connected } == 16 } }
        host.mutate { host(HostCmd.StartGame("bluff", mapOf("rounds" to 3))) }
        withTimeout(20_000) { host.tv.first { (it.stage?.game as? BluffTv)?.let { g -> g.phase == "write" && g.submitted >= 1 } == true } }
        val survivor = bots[0]
        withTimeout(10_000) { while ((survivor.last?.screen as? Screen.TextEntry)?.value == null) delay(20) }
        bots.drop(1).forEach { it.stop = true }
        coroutineScope {
            jobs.drop(1).map { j -> async { j.cancel() } }.awaitAll()
        }
        val tv = withTimeout(20_000) { host.tv.first { it.players.count { p -> p.connected } == 1 && it.stage?.paused == true } }
        assertEquals("pick", (tv.stage!!.game as BluffTv).phase)
        assertEquals("WAITING_FOR_PLAYERS", tv.stage!!.pauseReason)
        jobs.forEach { it.cancel() }
    }
}
