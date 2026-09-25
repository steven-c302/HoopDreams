package partyos.devserver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import partyos.engine.GameRegistry
import partyos.engine.PartyEngine
import partyos.engine.SecureEntropy
import partyos.engine.SystemClock
import partyos.engine.games.bluff.BluffBattle
import partyos.server.DirectoryStaticFiles
import partyos.server.PartyHost
import partyos.server.PartyServer
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Runs the PARTY OS engine + server on a Mac/PC (no TV UI) for phone-controller development and e2e tests.
 * Usage: devserver [--port 8080] [--pin 1234] [--static ../controller/dist] [--bind 0.0.0.0]
 */
fun main(args: Array<String>) {
    val opts = args.toList().chunked(2).filter { it.size == 2 }.associate { it[0].removePrefix("--") to it[1] }
    val port = opts["port"]?.toInt() ?: 8080
    val pin = opts["pin"] ?: (1000..9999).random().toString()
    val staticDir = File(opts["static"] ?: "../controller/dist")
    val bind = opts["bind"] ?: "0.0.0.0"

    val engine = PartyEngine(SystemClock, SecureEntropy(), GameRegistry(listOf(BluffBattle())))
    engine.setPin(pin)
    val host = PartyHost(engine, SystemClock, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    val server = PartyServer.start(host, DirectoryStaticFiles(staticDir), ports = port..port, bindHost = bind)

    val room = host.tv.value.roomCode
    println("PARTY OS dev server")
    println("  room: $room   host PIN: $pin   static: ${staticDir.absolutePath}")
    lanAddresses().ifEmpty { listOf("127.0.0.1") }.forEach { println("  join: http://$it:${server.port}/j/$room") }
    println("  host: http://127.0.0.1:${server.port}/host")
    Runtime.getRuntime().addShutdownHook(Thread { server.stop() })
    Thread.currentThread().join()
}

private fun lanAddresses(): List<String> = NetworkInterface.getNetworkInterfaces().toList()
    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
    .flatMap { it.inetAddresses.toList() }
    .filterIsInstance<Inet4Address>()
    .filter { it.isSiteLocalAddress }
    .map { it.hostAddress }
