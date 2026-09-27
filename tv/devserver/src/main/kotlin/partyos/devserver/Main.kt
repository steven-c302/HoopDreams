package partyos.devserver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import partyos.engine.GameRegistry
import partyos.engine.PartyEngine
import partyos.engine.SecureEntropy
import partyos.engine.SystemClock
import partyos.engine.games.blackjack.DrunkBlackjack
import partyos.engine.games.trivia.BrainDrain
import partyos.engine.games.bluff.BluffBattle
import partyos.server.DirectoryStaticFiles
import partyos.server.OpenTriviaFeed
import partyos.server.PartyHost
import partyos.server.PartyServer
import partyos.server.ServerConfig
import partyos.server.SpotifyMac
import partyos.server.lanAddresses
import java.io.File

/**
 * Runs the PARTY OS engine + server on a Mac/PC (no TV UI) for phone-controller development and e2e tests.
 * Usage: devserver [--port 8080] [--pin 1234] [--static ../controller/dist] [--bind 0.0.0.0] [--live-trivia off] [--spotify off]
 * Brain Drain tops up from Open Trivia DB once its bundled questions run out; `--live-trivia off` keeps it offline.
 */
fun main(args: Array<String>) {
    val opts = args.toList().chunked(2).filter { it.size == 2 }.associate { it[0].removePrefix("--") to it[1] }
    val port = opts["port"]?.toInt() ?: 8080
    val pin = opts["pin"] ?: (1000..9999).random().toString()
    val staticDir = File(opts["static"] ?: "../controller/dist")
    val bind = opts["bind"] ?: "0.0.0.0"

    val liveTrivia = opts["live-trivia"] != "off"
    val feed = if (liveTrivia) {
        OpenTriviaFeed(CoroutineScope(SupervisorJob() + Dispatchers.IO), onError = { System.err.println("live trivia: ${it.message}") })
    } else {
        null
    }
    val engine = PartyEngine(SystemClock, SecureEntropy(), GameRegistry(listOf(BrainDrain(feed = feed), BluffBattle(), DrunkBlackjack())))
    engine.setPin(pin)
    val host = PartyHost(engine, SystemClock, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    val music = if (opts["spotify"] == "off") null else SpotifyMac.detect()
    val server = PartyServer.start(host, DirectoryStaticFiles(staticDir), ServerConfig(music = music), ports = port..port, bindHost = bind)

    val room = host.tv.value.roomCode
    println("PARTY OS dev server")
    println("  room: $room   host PIN: $pin   static: ${staticDir.absolutePath}")
    lanAddresses().ifEmpty { listOf("127.0.0.1") }.forEach { println("  join: http://$it:${server.port}/j/$room") }
    println("  host: http://127.0.0.1:${server.port}/host")
    println("  TV:   http://127.0.0.1:${server.port}/tv   (open on this machine, full screen)")
    println("  live questions: " + if (liveTrivia) "Open Trivia DB once the bundled ones run out (--live-trivia off to disable)" else "off")
    println("  music: " + if (music != null) "Spotify on this Mac can play under the show (TV: Esc → Music; --spotify off to disable)" else "the show's own score")
    Runtime.getRuntime().addShutdownHook(Thread { server.stop() })
    Thread.currentThread().join()
}
