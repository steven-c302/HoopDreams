package partyos.server

import kotlinx.serialization.json.Json
import partyos.engine.PartySnapshot
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The party in progress (roster, sign-ins, settings, the game on screen), saved after every change so a restarted
 * server picks up where it left off: same room code, and phones rejoin on their own. Only a recent party is resumed
 * ([load]'s maxAgeMs); an older one is a different night and starts fresh. Writes go to a temporary file that then
 * replaces the old one, and are serialised (the shutdown hook saves while a throttled save may be running); an
 * unreadable file is set aside as `<name>.bad`.
 */
class PartyFile(private val file: File, private val onError: (String) -> Unit = {}) {
    /** The saved party if it was last saved within [maxAgeMs] of [now], else null (missing, too old or unreadable). */
    @Synchronized
    fun load(maxAgeMs: Long, now: Long = System.currentTimeMillis()): PartySnapshot? {
        if (!file.exists()) return null
        if (now - file.lastModified() > maxAgeMs) return null
        return runCatching { json.decodeFromString(PartySnapshot.serializer(), file.readText()) }.getOrElse {
            val bad = File(file.path + ".bad")
            onError("couldn't read ${file.name} (${it.message}); set aside as ${bad.name}")
            runCatching { Files.move(file.toPath(), bad.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            null
        }
    }

    @Synchronized
    fun save(snapshot: PartySnapshot) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(PartySnapshot.serializer(), snapshot))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { onError("couldn't save ${file.name}: ${it.message}") }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
