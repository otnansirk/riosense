package id.bangsadigital.riosense.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class MapTileKey(
    val z: Int,
    val x: Int,
    val y: Int
)

/**
 * Lightweight, zero-SDK-overhead Dark Map Tile loader & local disk cache.
 * Uses free CartoDB Dark Matter tiles (no API key required) and caches every tile in
 * `cacheDir/map_tiles` so previously viewed routes render 100% offline instantly.
 */
object MapTileCache {

    private val subdomains = arrayOf("a", "b", "c", "d")

    suspend fun loadTile(context: Context, key: MapTileKey): ImageBitmap? = withContext(Dispatchers.IO) {
        val cacheDir = File(context.applicationContext.cacheDir, "map_tiles").apply { mkdirs() }
        val tileFile = File(cacheDir, "${key.z}_${key.x}_${key.y}.png")

        if (tileFile.exists() && tileFile.length() > 100L) {
            decodeFile(tileFile)?.let { return@withContext it }
        }

        // Download CartoDB Dark Matter tile (crisp @2x dark road map matching Obsidian theme)
        val sub = subdomains[(key.x + key.y).and(3)]
        val urlStr = "https://$sub.basemaps.cartocdn.com/dark_all/${key.z}/${key.x}/${key.y}@2x.png"

        runCatching {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4500
                readTimeout = 4500
                setRequestProperty("User-Agent", "RioSense-Automotive-HUD/1.0")
                requestMethod = "GET"
            }
            try {
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.size > 100) {
                        val tmp = File(cacheDir, "${key.z}_${key.x}_${key.y}.tmp")
                        FileOutputStream(tmp).use { it.write(bytes) }
                        if (tileFile.exists()) tileFile.delete()
                        tmp.renameTo(tileFile)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } else null
                } else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun decodeFile(file: File): ImageBitmap? = runCatching {
        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
    }.getOrNull()
}
