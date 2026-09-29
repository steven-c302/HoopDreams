package partyos.server

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import partyos.engine.DoodleTv
import partyos.engine.GameRegistry
import partyos.engine.HostCmd
import partyos.engine.PartyEngine
import partyos.engine.PlayerId
import partyos.engine.Screen
import partyos.engine.SecureEntropy
import partyos.engine.SystemClock
import partyos.engine.games.doodle.Doodle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InkRoutesTest {
    private fun doodleHost(): PartyHost {
        val engine = PartyEngine(SystemClock, SecureEntropy(), GameRegistry(listOf(Doodle())))
        engine.setPin("1234")
        return PartyHost(engine, SystemClock, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    private class Party(val host: PartyHost, val tokens: Map<PlayerId, String>) {
        val tv get() = host.tv.value.stage!!.game as DoodleTv
        val round get() = host.tv.value.stage!!.phaseSeq
        val drawer get() = tv.drawer!!
        val guesser get() = tokens.keys.first { it != drawer }
    }

    /** Three joined, present players; Doodle started, tutorial skipped, the drawer has picked and the draw is on. */
    private suspend fun ApplicationTestBuilder.drawing(host: PartyHost): Party {
        serve(host)
        val room = host.tv.value.roomCode
        val tokens = (1..3).associate { i ->
            val token = tokenOf(client.join(room, "P$i").second)
            host.read { resolve(token)!! } to token
        }
        tokens.keys.forEach { host.connected(it) }
        host.hostCommand(null, HostCmd.StartGame("doodle", mapOf("rounds" to 3)))
        host.hostCommand(null, HostCmd.SkipPhase)
        val party = Party(host, tokens)
        val drawerView = host.read { phoneState(party.drawer).screen }
        val pick = assertIs<Screen.ChoiceList>(drawerView).options.first()
        host.mutate {
            action(party.drawer, "pick1", party.round, buildJsonObject { put("kind", JsonPrimitive("pick")); put("option", JsonPrimitive(pick.id)) })
        }
        assertEquals("draw", party.tv.phase)
        return party
    }

    private val stroke = listOf(InkOp.Start(1, 0, 0, 10, 10, 50), InkOp.Pts(1, listOf(20, 20, 50)), InkOp.End(1))

    @Test fun theDrawersStrokesReachTheTvAndOnlyTheirs() = testApplication {
        val host = doodleHost()
        val p = drawing(host)
        val c = createClient { install(WebSockets) }
        val hostToken = host.issueHostToken()
        c.webSocket("/ws?host=$hostToken") {
            val tvSocket: DefaultClientWebSocketSession = this
            assertEquals(emptyList(), nextOf<ServerMsg.InkSync>().turns)
            c.webSocket("/ws?token=${p.tokens.getValue(p.guesser)}") {
                nextOf<ServerMsg.View>()
                sendMsg(ClientMsg.Ink(p.round, stroke)) // not the drawer: dropped
            }
            c.webSocket("/ws?token=${p.tokens.getValue(p.drawer)}") {
                nextOf<ServerMsg.View>()
                sendMsg(ClientMsg.Ink(p.round - 1, stroke)) // an old phase: dropped
                sendMsg(ClientMsg.Ink(p.round, stroke))
                val ink = tvSocket.nextOf<ServerMsg.Ink>()
                assertEquals(1, ink.turn)
                assertEquals(1, ink.n) // the two dropped batches used no number
                assertEquals(stroke, ink.ops)
                // Phones never get ink, not even their own.
                assertNull(withTimeoutOrNull(300) { nextOf<ServerMsg.Ink>() })
            }
        }
    }

    @Test fun aTvThatConnectsLateGetsTheDrawingSoFar() = testApplication {
        val host = doodleHost()
        val p = drawing(host)
        val c = createClient { install(WebSockets) }
        c.webSocket("/ws?token=${p.tokens.getValue(p.drawer)}") {
            nextOf<ServerMsg.View>()
            sendMsg(ClientMsg.Ink(p.round, stroke))
            withTimeoutOrNull(3_000) { while (host.inkSync().upTo < 1) kotlinx.coroutines.delay(20) }
        }
        c.webSocket("/ws?host=${host.issueHostToken()}") {
            val sync = nextOf<ServerMsg.InkSync>()
            assertEquals(1, sync.upTo)
            assertEquals(listOf(InkStroke(1, 0, 0, listOf(10, 10, 50, 20, 20, 50), open = false)), sync.turns.single().strokes)
        }
    }

    @Test fun inkDoesNotChangeGameStateOrPushViews() = testApplication {
        val host = doodleHost()
        val p = drawing(host)
        val before = host.version.value
        host.ink(p.drawer, p.round, stroke)
        assertEquals(before, host.version.value)
    }

    @Test fun aFloodOfInkIsCappedByTheRateLimit() = testApplication {
        val host = doodleHost()
        val p = drawing(host)
        createClient { install(WebSockets) }.webSocket("/ws?token=${p.tokens.getValue(p.drawer)}") {
            nextOf<ServerMsg.View>()
            repeat(100) { i -> sendMsg(ClientMsg.Ink(p.round, listOf(InkOp.Start(i + 1, 0, 0, 10, 10, 50)))) }
            kotlinx.coroutines.delay(600)
        }
        val strokes = host.inkSync().turns.single().strokes.size
        assertTrue(strokes in 30..60, "$strokes of 100 batches got through") // a burst of 40, then 25 a second
    }

    @Test fun theDrawingsGoWhenTheGameEnds() = testApplication {
        val host = doodleHost()
        val p = drawing(host)
        host.ink(p.drawer, p.round, stroke)
        assertTrue(host.inkSync().turns.isNotEmpty())
        host.hostCommand(null, HostCmd.EndGame)
        assertTrue(host.inkSync().turns.isEmpty())
    }
}
