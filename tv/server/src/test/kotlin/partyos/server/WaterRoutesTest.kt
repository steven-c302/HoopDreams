package partyos.server

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class WaterRoutesTest {
    @Test fun aPlayerSwitchesWaterOnAndOffWithTheirOwnToken() = testApplication {
        val host = newHost()
        serve(host)
        val room = host.tv.value.roomCode
        val joined = client.post("/api/join") {
            contentType(ContentType.Application.Json)
            setBody("""{"room":"$room","name":"Ava","avatar":{"face":"p:01","color":"#112233"},"water":true}""")
        }
        assertEquals(200, joined.status.value)
        assertEquals(true, host.tv.value.players.single().water)
        val token = tokenOf(joined.bodyAsText())
        suspend fun set(t: String, on: Boolean) = client.post("/api/water") {
            contentType(ContentType.Application.Json)
            setBody("""{"token":"$t","water":$on}""")
        }.status.value
        assertEquals(200, set(token, false))
        assertEquals(false, host.tv.value.players.single().water)
        assertEquals(401, set("not-a-token", true))
    }
}
