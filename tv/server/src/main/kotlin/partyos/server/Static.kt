package partyos.server

import io.ktor.http.ContentType
import java.io.File

/** Where the phone controller's built files come from (APK assets on the TV, a directory on the Mac). */
fun interface StaticFiles {
    /** Returns the file at a relative path such as "index.html" or "assets/app.js", or null. */
    fun read(path: String): ByteArray?
}

class DirectoryStaticFiles(root: File) : StaticFiles {
    private val base = root.canonicalFile
    override fun read(path: String): ByteArray? {
        val f = File(base, path).canonicalFile
        if (!f.path.startsWith(base.path + File.separator) || !f.isFile) return null
        return f.readBytes()
    }
}

internal fun contentTypeFor(path: String): ContentType = when (path.substringAfterLast('.', "").lowercase()) {
    "html" -> ContentType.Text.Html
    "js", "mjs" -> ContentType.Application.JavaScript
    "css" -> ContentType.Text.CSS
    "svg" -> ContentType.Image.SVG
    "png" -> ContentType.Image.PNG
    "json", "webmanifest" -> ContentType.Application.Json
    "woff2" -> ContentType("font", "woff2")
    "ico" -> ContentType("image", "x-icon")
    else -> ContentType.Application.OctetStream
}
