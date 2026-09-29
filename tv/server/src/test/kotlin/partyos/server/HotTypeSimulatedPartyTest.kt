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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.HotTypeTv
import partyos.engine.PartyEngine
import partyos.engine.PhoneState
import partyos.engine.Screen
import partyos.engine.SecureEntropy
import partyos.engine.SystemClock
import partyos.engine.games.hottype.HotType
import partyos.engine.games.hottype.HotTypeDictionary
import partyos.engine.games.hottype.HotTypeSolver
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Eight bot phones play three rounds of Hot Type against a real server, dropping and rejoining at random. */
class HotTypeSimulatedPartyTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = PartyEngine(SystemClock, SecureEntropy(), GameRegistry(listOf(HotType())))
    private val host = PartyHost(engine, SystemClock, scope)
    private val server = PartyServer.start(host, MemoryStatic, ports = 0..0, bindHost = "127.0.0.1")
    private val base = "http://127.0.0.1:${server.port}"
    private val http = HttpClient(CIO) { install(WebSockets) }
    private val solver = HotTypeSolver(HotTypeDictionary.core)
    private val solutions = ConcurrentHashMap<List<String>, Map<String, List<Int>>>()

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
                            if (v.gameId != null && rnd.nextDouble() < dropRate) break
                        }
                        val s = v.screen
                        val payload: JsonObject? = when {
                            s is Screen.Tutorial -> if (!s.acknowledged) obj("kind" to "ack") else null
                            s is Screen.Hunt && s.phase == "hunt" && s.tiles.isNotEmpty() && s.found.size < 6 -> {
                                val words = solutions.getOrPut(s.tiles) { solver.solve(s.tiles, s.size) }
                                val mine = s.found.map { it.word }.toSet()
                                val pick = words.entries.filter { it.key !in mine }.randomOrNull(rnd)
                                pick?.let { JsonObject(mapOf("kind" to JsonPrimitive("word"), "path" to JsonArray(it.value.map(::JsonPrimitive)))) }
                            }
                            else -> null
                        }
                        if (payload != null && !v.paused) {
                            send(Frame.Text(PartyJson.encodeToString(ClientMsg.serializer(), ClientMsg.Action("$name-${n++}", v.round, payload))))
                        }
                    }
                }
                if (!stop) delay(50L + rnd.nextLong(150))
            }
        }
    }

    @Test fun eightFlakyPhonesFinishAGameWithConsistentScores() = runBlocking {
        val bots = (1..8).map { Bot("Bot$it", join("Bot$it"), dropRate = 0.2, seed = it) }
        bots.map { b -> scope.async { b.run() } }
        withTimeout(20_000) { host.tv.first { it.players.count { p -> p.connected } == 8 } }

        val recaps = ConcurrentHashMap<Int, HotTypeTv>()
        val driver = scope.launch {
            var skipped = -1
            var huntSince = 0L
            host.tv.collect { tv ->
                val st = tv.stage ?: return@collect
                val g = st.game as? HotTypeTv
                if (g != null && g.phase == "scores") recaps[g.round] = g
                if (st.paused && tv.players.count { it.connected } >= 2) host.mutate { host(HostCmd.Resume) }
                if (g == null) return@collect
                if (g.phase == "hunt" && huntSince == 0L) huntSince = System.currentTimeMillis()
                val enough = g.phase == "hunt" && g.rail.sumOf { it.count } >= 16
                val waited = g.phase == "hunt" && System.currentTimeMillis() - huntSince > 6_000
                val done = g.phase != "hunt" || enough || waited
                if (done && st.phaseSeq != skipped) {
                    skipped = st.phaseSeq
                    if (g.phase != "hunt") huntSince = 0L
                    delay(30)
                    host.mutate { host(HostCmd.SkipPhase) }
                }
            }
        }
        assertEquals(partyos.engine.ActionResult.Ack, host.mutate { host(HostCmd.StartGame("hottype", mapOf("rounds" to 3))) })

        val result = withTimeout(150_000) { host.tv.first { it.lastResult != null }.lastResult!! }
        driver.cancel()
        bots.forEach { it.dropRate = 0.0 }
        withTimeout(20_000) {
            while (bots.any { b -> b.last?.let { it.gameId == null && it.scores == result.standings } != true }) delay(50)
        }
        bots.forEach { it.stop = true }
        assertTrue(bots.sumOf { it.sessions } > 8, "expected some reconnects")

        // Every round's recap parts add up, and the standings are the sum of each round's totals.
        assertEquals(setOf(1, 2, 3), recaps.keys)
        val expected = HashMap<String, Int>()
        for ((_, r) in recaps) for (d in r.deltas) {
            assertEquals(d.base + d.unique + d.longest, d.total)
            expected.merge(d.id.v, d.total, Int::plus)
        }
        val actual = result.standings.associate { it.id.v to it.score }.filterValues { it != 0 }
        assertEquals(expected.filterValues { it != 0 }, actual)
    }
}
