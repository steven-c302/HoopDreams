package partyos.server

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Selfies and photos players use as their face (avatar face `i:<id>`). The phone crops and shrinks the picture to a
 * small square JPEG before uploading it; the id is a content hash, so the same photo twice is stored once. Only the
 * newest [maxPhotos] are kept (oldest out first). With a [dir], each photo is also written there as `<id>.jpg` and read
 * back when the server starts, so a restarted party keeps its faces; without one they last as long as the server. A
 * face whose photo is gone falls back to a drawn preset on every screen.
 */
class PhotoStore(
    private val maxPhotos: Int = 200,
    private val dir: File? = null,
    private val onError: (String) -> Unit = {},
) {
    private val photos = LinkedHashMap<String, ByteArray>()

    /** Last-modified stamp given to the newest file; the next write gets a strictly larger one (see [put]). */
    private var lastStamp = 0L

    init {
        dir?.listFiles { f -> f.isFile && f.name.endsWith(".jpg") && ID.matches(f.name.removeSuffix(".jpg")) }
            ?.sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })
            ?.forEach { f ->
                lastStamp = maxOf(lastStamp, f.lastModified())
                runCatching { f.readBytes() }.getOrNull()?.takeIf { it.size <= MAX_BYTES && looksLikeJpeg(it) }
                    ?.let { photos[f.name.removeSuffix(".jpg")] = it }
            }
        trim()
    }

    /** Stores [jpeg] and returns its id. */
    @Synchronized
    fun put(jpeg: ByteArray): String {
        val id = idOf(jpeg)
        photos.remove(id)
        photos[id] = jpeg
        dir?.let { d ->
            runCatching {
                d.mkdirs()
                val tmp = File(d, "$id.tmp")
                tmp.writeBytes(jpeg)
                // Age is the file's last-modified time when the folder is reloaded, and writes made in the same tick would
                // tie, so each one is stamped strictly after the last (also after a clock that ran ahead or went back).
                lastStamp = maxOf(System.currentTimeMillis(), lastStamp + 1)
                tmp.setLastModified(lastStamp)
                Files.move(tmp.toPath(), File(d, "$id.jpg").toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.onFailure { onError("couldn't save photo $id: ${it.message}") }
        }
        trim()
        return id
    }

    @Synchronized
    fun get(id: String): ByteArray? = photos[id]

    private fun trim() {
        while (photos.size > maxPhotos) {
            val oldest = photos.keys.first()
            photos.remove(oldest)
            dir?.let { File(it, "$oldest.jpg").delete() }
        }
    }

    companion object {
        /** Well above a 256 px phone crop (~20-30 KB) and well below anything worth holding onto. */
        const val MAX_BYTES = 96 * 1024
        val ID = Regex("[0-9a-f]{16}")

        /** Starts like a JPEG (SOI then a marker). The server never decodes it; browsers do, as an image only. */
        fun looksLikeJpeg(b: ByteArray) = b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()

        private fun idOf(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).take(8).joinToString("") { "%02x".format(it) }
    }
}
