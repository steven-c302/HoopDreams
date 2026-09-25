package partyos.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.ServerSocket

/** The running HTTP + WebSocket server. */
class PartyServer private constructor(private val server: EmbeddedServer<*, *>, val port: Int) {
    fun stop() = server.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)

    companion object {
        /** Binds the first free port in [ports] (0 = any free port) on all interfaces. */
        fun start(
            host: PartyHost,
            static: StaticFiles,
            cfg: ServerConfig = ServerConfig(),
            ports: IntRange = 8080..8089,
            bindHost: String = "0.0.0.0",
        ): PartyServer {
            for (p in ports) {
                if (p != 0 && !isFree(bindHost, p)) continue
                val s = runCatching {
                    embeddedServer(CIO, port = p, host = bindHost) { partyModule(host, static, cfg) }.start(wait = false)
                }.getOrNull() ?: continue
                val bound = runBlocking { s.engine.resolvedConnectors().first().port }
                return PartyServer(s, bound)
            }
            error("No free port in $ports")
        }

        private fun isFree(host: String, port: Int) = runCatching {
            ServerSocket().use { it.reuseAddress = true; it.bind(InetSocketAddress(host, port)) }
        }.isSuccess
    }
}
