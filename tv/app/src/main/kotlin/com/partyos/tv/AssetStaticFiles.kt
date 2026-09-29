package com.partyos.tv

import android.content.res.AssetManager
import partyos.server.StaticFiles

/** Serves the phone controller bundled under assets/controller by the syncControllerAssets Gradle task. */
class AssetStaticFiles(private val assets: AssetManager) : StaticFiles {
    override fun read(path: String): ByteArray? {
        if (path.split('/').any { it == ".." || it.isEmpty() }) return null
        return runCatching { assets.open("controller/$path").use { it.readBytes() } }.getOrNull()
    }
}
