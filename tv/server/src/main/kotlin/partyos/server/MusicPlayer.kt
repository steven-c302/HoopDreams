package partyos.server

import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.TimeUnit

/** What the TV shows about the music app playing under the show. */
@Serializable
data class MusicStatus(
    /** The app is installed and can be controlled from this machine. */
    val available: Boolean,
    val running: Boolean = false,
    val playing: Boolean = false,
    val track: String? = null,
    val artist: String? = null,
)

/** A music app on the host machine the TV can steer (play, pause, skip). Calls may block briefly: run them off the main thread. */
interface MusicPlayer {
    fun status(): MusicStatus
    /** One of [COMMANDS]; false if the app didn't take it. */
    fun command(cmd: String): Boolean

    companion object {
        val COMMANDS = setOf("play", "pause", "playpause", "next", "previous")
    }
}

/**
 * The Spotify desktop app on a Mac, driven through AppleScript. The first call makes macOS ask whether the party
 * server may control Spotify; until someone clicks OK, commands just fail and the TV says so.
 */
class SpotifyMac private constructor() : MusicPlayer {
    override fun status(): MusicStatus {
        val out = osa(
            """
            if application "Spotify" is running then
              tell application "Spotify"
                set s to player state as string
                set t to ""
                set a to ""
                try
                  set t to name of current track
                  set a to artist of current track
                end try
                return s & linefeed & t & linefeed & a
              end tell
            else
              return "closed"
            end if
            """.trimIndent(),
        ) ?: return MusicStatus(available = true)
        val lines = out.lines()
        if (lines.first() == "closed") return MusicStatus(available = true)
        return MusicStatus(
            available = true, running = true, playing = lines.first() == "playing",
            track = lines.getOrNull(1)?.takeIf { it.isNotBlank() }, artist = lines.getOrNull(2)?.takeIf { it.isNotBlank() },
        )
    }

    override fun command(cmd: String): Boolean {
        val verb = when (cmd) {
            "play" -> "play"
            "pause" -> "pause"
            "playpause" -> "playpause"
            "next" -> "next track"
            "previous" -> "previous track"
            else -> return false
        }
        // "play" opens Spotify if it's closed (picking up where it left off); the others need it running.
        val script = if (cmd == "play") "tell application \"Spotify\" to $verb"
        else "if application \"Spotify\" is running then tell application \"Spotify\" to $verb"
        return osa(script) != null
    }

    private fun osa(script: String): String? = runCatching {
        val p = ProcessBuilder("osascript", "-e", script).redirectErrorStream(false).start()
        if (!p.waitFor(TIMEOUT_S, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            return null
        }
        if (p.exitValue() != 0) return null
        p.inputStream.bufferedReader().use { it.readText() }.trimEnd('\n')
    }.getOrNull()

    companion object {
        private const val TIMEOUT_S = 4L

        /** Spotify on this Mac, or null anywhere else (another OS, or Spotify isn't installed). */
        fun detect(): MusicPlayer? {
            if (!System.getProperty("os.name").orEmpty().startsWith("Mac")) return null
            val home = System.getProperty("user.home")
            val installed = listOf("/Applications/Spotify.app", "$home/Applications/Spotify.app").any { File(it).isDirectory }
            return if (installed) SpotifyMac() else null
        }
    }
}
