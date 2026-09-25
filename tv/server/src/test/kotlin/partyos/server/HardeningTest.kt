package partyos.server

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import partyos.engine.HostCmd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Review fixes: replayed host commands, oversized ids, unbounded bodies, PIN races, PIN rotation. */
class HardeningTest {
    private suspend fun io.ktor.client.HttpClient.hostLogin(pin: String): String = PartyJson.parseToJsonElement(
        post("/api/host/login") { contentType(ContentType.Application.Json); setBody("""{"pin":"$pin"}""") }.bodyAsText(),
    ).jsonObject["hostToken"]!!.jsonPrimitive.content

    @Test fun aReplayedHostCommandIsAppliedOnce() = testApplication {
        val host = newHost("4321"); serve(host)
        val room = host.tv.value.roomCode
        val tokens = listOf("A", "B", "C").map { tokenOf(client.join(room, it).second) }
        val ht = client.hostLogin("4321")
        val ws = createClient { install(WebSockets) }
        ws.webSocket("/ws?token=${tokens[0]}") {
            nextOf<ServerMsg.View>()
            ws.webSocket("/ws?token=${tokens[1]}") {
                nextOf<ServerMsg.View>()
                ws.webSocket("/ws?token=${tokens[2]}") {
                    nextOf<ServerMsg.View>()
                    ws.webSocket("/ws?host=$ht") {
                        sendMsg(ClientMsg.Host("start", HostCommand.Start("bluff", 3)))
                        assertEquals(ServerMsg.Ack("start"), nextOf<ServerMsg.Ack>())
                        val before = host.tv.value.stage!!.phaseSeq
                        sendMsg(ClientMsg.Host("skip-1", HostCommand.Skip))
                        assertEquals(ServerMsg.Ack("skip-1"), nextOf<ServerMsg.Ack>())
                        sendMsg(ClientMsg.Host("skip-1", HostCommand.Skip))
                        assertEquals(ServerMsg.Ack("skip-1"), nextOf<ServerMsg.Ack>())
                        assertEquals(before + 1, host.tv.value.stage!!.phaseSeq)
                    }
                }
            }
        }
    }

    @Test fun oversizedOrOddActionIdsAreRejected() = testApplication {
        val host = newHost(); serve(host)
        val token = tokenOf(client.join(host.tv.value.roomCode, "Sam").second)
        createClient { install(WebSockets) }.webSocket("/ws?token=$token") {
            nextOf<ServerMsg.View>()
            sendMsg(ClientMsg.Action("x".repeat(100), 1, JsonObject(mapOf("kind" to JsonPrimitive("ack")))))
            assertEquals("BAD_ID", nextOf<ServerMsg.Reject>().code)
            sendMsg(ClientMsg.Action("bad id!", 1, JsonObject(mapOf("kind" to JsonPrimitive("ack")))))
            assertEquals("BAD_ID", nextOf<ServerMsg.Reject>().code)
        }
    }

    @Test fun chunkedOversizedBodyIsRefused() = testApplication {
        val host = newHost(); serve(host)
        val r = client.post("/api/join") {
            contentType(ContentType.Application.Json)
            setBody(object : OutgoingContent.WriteChannelContent() {
                override val contentType = ContentType.Application.Json
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    channel.writeFully("{\"room\":\"".toByteArray())
                    repeat(200) { channel.writeFully(ByteArray(1024) { 'a'.code.toByte() }) }
                }
            })
        }
        assertEquals(400, r.status.value)
    }

    @Test fun parallelPinGuessesAreCappedAtFive() = runBlocking {
        val l = PinLockout(5, 60_000) { 0L }
        val allowed = (1..50).map { async(Dispatchers.Default) { l.tryAttempt("1.2.3.4") } }.awaitAll().count { it }
        assertEquals(5, allowed)
    }

    @Test fun changingThePinSignsOutCoHosts() = testApplication {
        val host = newHost("4321"); serve(host)
        val ht = client.hostLogin("4321")
        val ws = createClient { install(WebSockets) }
        ws.webSocket("/ws?host=$ht") {
            assertTrue(assertIs<ServerMsg.Welcome>(next()).host)
            host.changePin("9999")
            assertEquals("PIN_CHANGED", nextOf<ServerMsg.Bye>().reason)
        }
        ws.webSocket("/ws?host=$ht") { assertEquals("BAD_TOKEN", assertIs<ServerMsg.Bye>(next()).reason) }
        assertEquals(401, client.post("/api/host/login") { contentType(ContentType.Application.Json); setBody("""{"pin":"4321"}""") }.status.value)
        client.hostLogin("9999")
    }

    @Test fun closingAHostStopsItsDeadlineTimer() = runBlocking {
        val host = newHost()
        val room = host.tv.value.roomCode
        repeat(3) { i ->
            val j = host.mutate { join(room, "P$i", partyos.engine.Avatar("🙂", "#112233"), partyos.engine.Role.PLAYER) } as partyos.engine.JoinResult.Joined
            host.connected(j.player.id)
        }
        host.mutate { host(HostCmd.StartGame("bluff")) }
        host.close()
        assertEquals(null, host.pendingDeadline())
    }
}
