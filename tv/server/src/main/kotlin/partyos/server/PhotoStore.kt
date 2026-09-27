package partyos.server

import java.security.MessageDigest

/**
 * Selfies and photos players use as their face (avatar face `i:<id>`). The phone crops and shrinks the picture to a
 * small square JPEG before uploading it; the id is a content hash, so the same photo twice is stored once. Photos live
 * in memory for the life of the server, capped at [maxPhotos] (oldest out first). A face whose photo is gone falls
 * back to a drawn preset on every screen.
 */
class PhotoStore(private val maxPhotos: Int = 200) {
    private val photos = LinkedHashMap<String, ByteArray>()

    /** Stores [jpeg] and returns its id. */
    @Synchronized
    fun put(jpeg: ByteArray): String {
        val id = idOf(jpeg)
        photos.remove(id)
        photos[id] = jpeg
        while (photos.size > maxPhotos) photos.remove(photos.keys.first())
        return id
    }

    @Synchronized
    fun get(id: String): ByteArray? = photos[id]

    companion object {
        /** Well above a 256 px phone crop (~20-30 KB) and well below anything worth holding onto. */
        const val MAX_BYTES = 96 * 1024
        val ID = Regex("[0-9a-f]{16}")

        /** Starts like a JPEG (SOI then a marker). The server never decodes it; browsers do, as an image only. */
        fun looksLikeJpeg(b: ByteArray) = b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()

        private fun idOf(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).take(8).joinToString("") { "%02x".format(it) }
    }
}
