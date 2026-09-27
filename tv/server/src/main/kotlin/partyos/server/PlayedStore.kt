package partyos.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Every question and prompt played, oldest first, kept in a small JSON file so games skip them on later nights too.
 * Delete the file to start fresh. Writes go to a temporary file first and then replace the old one, so a crash
 * mid-write can't leave it half written; an unreadable file is set aside as `<name>.bad` rather than overwritten.
 * Saves are serialised: the devserver's shutdown hook saves while the throttled save may still be running.
 */
class PlayedStore(private val file: File, private val onError: (String) -> Unit = {}) {
    @Serializable
    private data class Saved(val version: Int = 1, val played: List<String> = emptyList())

    @Synchronized
    fun load(): List<String> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString(Saved.serializer(), file.readText()).played }.getOrElse {
            val bad = File(file.path + ".bad")
            onError("couldn't read ${file.name} (${it.message}); set aside as ${bad.name}")
            runCatching { Files.move(file.toPath(), bad.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            emptyList()
        }
    }

    @Synchronized
    fun save(played: List<String>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(Saved.serializer(), Saved(played = played)))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { onError("couldn't save ${file.name}: ${it.message}") }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
