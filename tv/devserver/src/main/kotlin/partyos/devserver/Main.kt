package partyos.devserver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import partyos.engine.GameRegistry
import partyos.engine.PartyEngine
import partyos.engine.SecureEntropy
import partyos.engine.SystemClock
import partyos.engine.games.blackjack.DrunkBlackjack
import partyos.engine.games.trivia.BrainDrain
import partyos.engine.games.bluff.BluffBattle
import partyos.engine.games.doodle.Doodle
import partyos.engine.games.imposter.Imposter
import partyos.engine.games.hottype.HotType
import partyos.engine.games.jeopardy.Jeopardy
import partyos.engine.games.sprawl.Sprawl
import partyos.engine.games.turf.HomeTurf
import partyos.server.DirectoryStaticFiles
import partyos.server.OpenTriviaFeed
import partyos.server.PartyFile
import partyos.server.PartyHost
import partyos.server.PartyServer
import partyos.server.PhotoStore
import partyos.server.PlayedStore
import partyos.server.ServerConfig
import partyos.server.SpotifyMac
import partyos.server.lanAddresses
import java.io.File

/**
 * Runs the PARTY OS engine + server on a Mac/PC (no TV UI) for phone-controller development and e2e tests.
 * Usage: devserver [--port 8080] [--pin 1234] [--static ../controller/dist] [--bind 0.0.0.0] [--live-trivia off] [--spotify off]
 *   [--played ~/Library/Application Support/PartyOS/played-questions.json]
 *   [--party ~/Library/Application Support/PartyOS/party.json] [--photos ~/Library/Application Support/PartyOS/photos]
 * Brain Drain tops up from Open Trivia DB once its bundled questions run out; `--live-trivia off` keeps it offline.
 * With `--party`, a restart within [RESUME_WITHIN_MS] of the last change picks the party back up (same room code, the
 * game paused, phones rejoin on their own); `--photos` keeps players' photo faces across restarts.
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
    val games = GameRegistry(listOf(BrainDrain(feed = feed), BrainDrain(feed = feed, mode = BrainDrain.Mode.WRITE), BluffBattle(), DrunkBlackjack(), HomeTurf(), Sprawl(), Jeopardy(), Imposter(), Doodle(), HotType()))
    val partyFile = opts["party"]?.let { PartyFile(File(it), onError = { msg -> System.err.println("party: $msg") }) }
    val resumed = partyFile?.load(RESUME_WITHIN_MS)
    val engine = resumed?.let { PartyEngine.restore(it, SystemClock, SecureEntropy(), games) } ?: PartyEngine(SystemClock, SecureEntropy(), games)
    // Always this run's PIN: co-host sign-ins don't survive a restart anyway.
    engine.setPin(pin)
    // Played questions survive restarts when --played names a file: games skip them on later nights too.
    val played = opts["played"]?.let { PlayedStore(File(it), onError = { msg -> System.err.println("played questions: $msg") }) }
    played?.let { engine.rememberPlayed(it.load()) }
    var saved = engine.snapshot().usedContent
    val host = PartyHost(engine, SystemClock, CoroutineScope(SupervisorJob() + Dispatchers.Default), onCommit = { snap ->
        partyFile?.save(snap)
        if (played != null && snap.usedContent != saved) {
            played.save(snap.usedContent)
            saved = snap.usedContent
        }
    })
    val music = if (opts["spotify"] == "off") null else SpotifyMac.detect()
    val photos = PhotoStore(dir = opts["photos"]?.let(::File), onError = { msg -> System.err.println("photos: $msg") })
    val server = PartyServer.start(host, DirectoryStaticFiles(staticDir), ServerConfig(music = music, photos = photos), ports = port..port, bindHost = bind)

    val room = host.tv.value.roomCode
    println("PARTY OS dev server")
    if (resumed != null) println("  resumed the party from ${opts["party"]}, players: ${resumed.players.size} (delete it or PARTYOS_FRESH=1 to start over)")
    println("  room: $room   host PIN: $pin   static: ${staticDir.absolutePath}")
    lanAddresses().ifEmpty { listOf("127.0.0.1") }.forEach { println("  join: http://$it:${server.port}/j/$room") }
    println("  host: http://127.0.0.1:${server.port}/host")
    println("  TV:   http://127.0.0.1:${server.port}/tv   (open on this machine, full screen)")
    println("  live questions: " + if (liveTrivia) "Open Trivia DB once the bundled ones run out (--live-trivia off to disable)" else "off")
    println("  music: " + if (music != null) "Spotify on this Mac can play under the show (TV: Esc → Music; --spotify off to disable)" else "the show's own score")
    println("  played questions: " + if (played != null) "${saved.size} remembered in ${opts["played"]} (delete it to replay everything)" else "forgotten when this stops")
    Runtime.getRuntime().addShutdownHook(Thread {
        // Saves are throttled; make sure a question started just before Ctrl+C is on disk.
        val last = runBlocking { host.read { snapshot() } }
        played?.save(last.usedContent)
        partyFile?.save(last)
        server.stop()
    })
    Thread.currentThread().join()
}

/** A restart within this long of the last change is the same night; after that the party starts fresh. */
private const val RESUME_WITHIN_MS = 6 * 60 * 60 * 1000L
