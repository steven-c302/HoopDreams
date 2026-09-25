package partyos.server

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import partyos.engine.GameRegistry
import partyos.engine.PartyEngine
import partyos.engine.SecureEntropy
import partyos.engine.SystemClock
import partyos.engine.games.bluff.BluffBattle

object MemoryStatic : StaticFiles {
    private val files = mapOf("index.html" to "<html>controller</html>", "assets/app.js" to "console.log(1)")
    override fun read(path: String) = files[path]?.toByteArray()
}

fun newHost(pin: String = "1234"): PartyHost {
    val engine = PartyEngine(SystemClock, SecureEntropy(), GameRegistry(listOf(BluffBattle())))
    engine.setPin(pin)
    return PartyHost(engine, SystemClock, CoroutineScope(SupervisorJob() + Dispatchers.Default))
}

fun ApplicationTestBuilder.serve(host: PartyHost, cfg: ServerConfig = ServerConfig()) {
    application { partyModule(host, MemoryStatic, cfg) }
}

suspend fun HttpClient.join(room: String, name: String, spectator: Boolean = false): Pair<Int, String> {
    val r = post("/api/join") {
        contentType(ContentType.Application.Json)
        setBody("""{"room":"$room","name":"$name","avatar":{"emoji":"🙂","color":"#112233"},"spectator":$spectator}""")
    }
    return r.status.value to r.bodyAsText()
}

fun tokenOf(body: String) = PartyJson.parseToJsonElement(body).jsonObject["token"]!!.jsonPrimitive.content

suspend fun DefaultClientWebSocketSession.next(): ServerMsg = withTimeout(5_000) {
    val f = incoming.receive() as Frame.Text
    PartyJson.decodeFromString(ServerMsg.serializer(), f.readText())
}

suspend inline fun <reified T : ServerMsg> DefaultClientWebSocketSession.nextOf(): T = withTimeout(5_000) {
    var m = next()
    while (m !is T) m = next()
    m
}

suspend fun DefaultClientWebSocketSession.sendMsg(m: ClientMsg) =
    send(Frame.Text(PartyJson.encodeToString(ClientMsg.serializer(), m)))
