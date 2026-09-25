package partyos.server

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import partyos.engine.PlayerId
import partyos.engine.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServerTest {
    @Test fun joinReturnsTokenOrTheRightError() = testApplication {
        val host = newHost(); serve(host)
        val room = host.tv.value.roomCode
        val (ok, body) = client.join(room, "Sam")
        assertEquals(200, ok); assertTrue(tokenOf(body).length >= 32)
        assertEquals(409 to "WRONG_ROOM", client.join("ZZZZ".takeIf { it != room } ?: "YYYY", "Al").let { it.first to err(it.second) })
        assertEquals(409 to "NAME_TAKEN", client.join(room, "sam").let { it.first to err(it.second) })
        assertEquals(400 to "BAD_NAME", client.join(room, "   ").let { it.first to err(it.second) })
        repeat(15) { client.join(room, "P$it") }
        assertEquals(403 to "FULL", client.join(room, "P99").let { it.first to err(it.second) })
    }

    @Test fun controllerIsServedForSpaRoutesAndAssets() = testApplication {
        serve(newHost())
        assertEquals("<html>controller</html>", client.get("/j/ABCD").bodyAsText())
        assertEquals("<html>controller</html>", client.get("/").bodyAsText())
        assertEquals("console.log(1)", client.get("/assets/app.js").bodyAsText())
        assertEquals(404, client.get("/assets/nope.js").status.value)
        assertEquals(404, client.get("/assets/..%2F..%2Fetc/passwd").status.value)
        assertEquals("ok", client.get("/healthz").bodyAsText())
    }

    @Test fun socketGetsWelcomeThenView() = testApplication {
        val host = newHost(); serve(host)
        val token = tokenOf(client.join(host.tv.value.roomCode, "Sam").second)
        createClient { install(WebSockets) }.webSocket("/ws?token=$token") {
            val w = assertIs<ServerMsg.Welcome>(next())
            assertNotNull(w.playerId)
            val v = nextOf<ServerMsg.View>()
            assertEquals("Sam", v.view.me.name)
            assertIs<Screen.Waiting>(v.view.screen)
        }
    }

    @Test fun reconnectWithSameTokenResumesSamePlayer() = testApplication {
        val host = newHost(); serve(host)
        val token = tokenOf(client.join(host.tv.value.roomCode, "Sam").second)
        val ws = createClient { install(WebSockets) }
        var first: PlayerId? = null
        ws.webSocket("/ws?token=$token") { first = assertIs<ServerMsg.Welcome>(next()).playerId }
        ws.webSocket("/ws?token=$token") { assertEquals(first, assertIs<ServerMsg.Welcome>(next()).playerId) }
        assertEquals(1, host.tv.value.players.size)
    }

    @Test fun presenceFollowsTheSocket() = testApplication {
        val host = newHost(); serve(host)
        val token = tokenOf(client.join(host.tv.value.roomCode, "Sam").second)
        createClient { install(WebSockets) }.webSocket("/ws?token=$token") {
            nextOf<ServerMsg.View>()
            assertTrue(host.tv.value.players.single().connected)
        }
        withTimeout(3_000) { while (host.tv.value.players.single().connected) kotlinx.coroutines.delay(20) }
    }

    @Test fun badTokenGetsByeAndClose() = testApplication {
        serve(newHost())
        createClient { install(WebSockets) }.webSocket("/ws?token=nope") {
            assertEquals("BAD_TOKEN", assertIs<ServerMsg.Bye>(next()).reason)
        }
    }

    @Test fun spectatorActionsAreRejected() = testApplication {
        val host = newHost(); serve(host)
        val token = tokenOf(client.join(host.tv.value.roomCode, "Watcher", spectator = true).second)
        createClient { install(WebSockets) }.webSocket("/ws?token=$token") {
            nextOf<ServerMsg.View>()
            sendMsg(ClientMsg.Action("a1", 1, JsonObject(mapOf("kind" to JsonPrimitive("tap")))))
            assertEquals(ServerMsg.Reject("a1", "SPECTATOR"), nextOf<ServerMsg.Reject>())
        }
    }

    @Test fun pinLocksOutAfterFiveFailures() = testApplication {
        serve(newHost("4321"))
        suspend fun login(pin: String) = client.post("/api/host/login") {
            contentType(ContentType.Application.Json); setBody("""{"pin":"$pin"}""")
        }
        repeat(5) { assertEquals(401, login("0000").status.value) }
        assertEquals(429, login("4321").status.value)
    }

    @Test fun hostSocketControlsTheGameAndPlayersSeeStaleRejects() = testApplication {
        val host = newHost("4321"); serve(host)
        val room = host.tv.value.roomCode
        val tokens = listOf("A", "B", "C").map { tokenOf(client.join(room, it).second) }
        val hostToken = PartyJson.parseToJsonElement(client.post("/api/host/login") {
            contentType(ContentType.Application.Json); setBody("""{"pin":"4321"}""")
        }.bodyAsText()).jsonObject["hostToken"]!!.jsonPrimitive.content
        val ws = createClient { install(WebSockets) }
        ws.webSocket("/ws?token=${tokens[0]}") {
            val a = this
            nextOf<ServerMsg.View>()
            ws.webSocket("/ws?token=${tokens[1]}") {
                nextOf<ServerMsg.View>()
                ws.webSocket("/ws?token=${tokens[2]}") {
                    nextOf<ServerMsg.View>()
                    ws.webSocket("/ws?host=$hostToken") {
                        assertTrue(assertIs<ServerMsg.Welcome>(next()).host)
                        sendMsg(ClientMsg.Host("h1", HostCommand.Start("bluff", rounds = 3)))
                        assertEquals(ServerMsg.Ack("h1"), nextOf<ServerMsg.Ack>())
                        sendMsg(ClientMsg.Host("h2", HostCommand.Skip))
                        nextOf<ServerMsg.Ack>()
                        assertEquals("bluff", host.tv.value.stage?.gameId)

                        var v = a.nextOf<ServerMsg.View>().view
                        while (v.screen !is Screen.TextEntry) v = a.nextOf<ServerMsg.View>().view
                        val write = JsonObject(mapOf("kind" to JsonPrimitive("write"), "text" to JsonPrimitive("fake")))
                        a.sendMsg(ClientMsg.Action("late", v.round - 1, write))
                        assertEquals(ServerMsg.Reject("late", "STALE"), a.nextOf<ServerMsg.Reject>())
                        a.sendMsg(ClientMsg.Action("ok", v.round, write))
                        assertEquals(ServerMsg.Ack("ok"), a.nextOf<ServerMsg.Ack>())
                    }
                }
            }
        }
    }

    @Test fun playerSocketCannotSendHostCommands() = testApplication {
        val host = newHost(); serve(host)
        val token = tokenOf(client.join(host.tv.value.roomCode, "Sam").second)
        createClient { install(WebSockets) }.webSocket("/ws?token=$token") {
            sendMsg(ClientMsg.Host("h", HostCommand.Pause))
            assertEquals(ServerMsg.Reject("h", "NOT_HOST"), nextOf<ServerMsg.Reject>())
        }
    }

    @Test fun kickSendsByeAndRevokesToken() = testApplication {
        val host = newHost(); serve(host)
        val token = tokenOf(client.join(host.tv.value.roomCode, "Sam").second)
        val ws = createClient { install(WebSockets) }
        ws.webSocket("/ws?token=$token") {
            val id = assertIs<ServerMsg.Welcome>(next()).playerId!!
            host.mutate { host(partyos.engine.HostCmd.Kick(id)) }
            assertEquals("KICKED", nextOf<ServerMsg.Bye>().reason)
        }
        ws.webSocket("/ws?token=$token") { assertEquals("BAD_TOKEN", assertIs<ServerMsg.Bye>(next()).reason) }
    }

    @Test fun oversizedFrameClosesTheSocket() = testApplication {
        val host = newHost(); serve(host)
        val token = tokenOf(client.join(host.tv.value.roomCode, "Sam").second)
        createClient { install(WebSockets) }.webSocket("/ws?token=$token") {
            nextOf<ServerMsg.View>()
            send(Frame.Text("x".repeat(70 * 1024)))
            val closed = runCatching { withTimeout(5_000) { while (true) incoming.receive() } }
            assertIs<ClosedReceiveChannelException>(closed.exceptionOrNull())
            assertEquals(CloseReason.Codes.TOO_BIG.code, closeReason.await()?.code)
        }
    }

    @Test fun malformedMessagesAreRejectedNotFatal() = testApplication {
        val host = newHost(); serve(host)
        val token = tokenOf(client.join(host.tv.value.roomCode, "Sam").second)
        createClient { install(WebSockets) }.webSocket("/ws?token=$token") {
            send(Frame.Text("{not json"))
            assertEquals("BAD_MESSAGE", nextOf<ServerMsg.Reject>().code)
            sendMsg(ClientMsg.Ping)
            nextOf<ServerMsg.Pong>()
        }
    }

    private fun err(body: String) = PartyJson.parseToJsonElement(body).jsonObject["error"]!!.jsonPrimitive.content
}

class UnitsTest {
    @Test fun privateAddressTable() {
        val ok = listOf("10.0.0.5", "172.16.3.4", "172.31.255.1", "192.168.1.20", "127.0.0.1", "169.254.1.1", "::1", "0:0:0:0:0:0:0:1", "fe80::1", "fd12::3", "localhost")
        val no = listOf("8.8.8.8", "172.32.0.1", "11.0.0.1", "192.169.0.1", "2001:4860::8888", "evil.com", "")
        ok.forEach { assertTrue(isPrivateAddress(it), it) }
        no.forEach { assertFalse(isPrivateAddress(it), it) }
    }

    @Test fun tokenBucketAllowsTwentyPerSecond() {
        var now = 0L
        val b = TokenBucket(20, 20.0) { now }
        assertEquals(20, (1..30).count { b.tryTake() })
        now += 500
        assertEquals(10, (1..30).count { b.tryTake() })
    }

    @Test fun lockoutExpiresAfterSixtySeconds() {
        var now = 0L
        val l = PinLockout(5, 60_000) { now }
        repeat(5) { l.tryAttempt("1.2.3.4") }
        assertTrue(l.locked("1.2.3.4")); assertFalse(l.locked("5.6.7.8"))
        now += 60_001
        assertFalse(l.locked("1.2.3.4"))
    }
}
