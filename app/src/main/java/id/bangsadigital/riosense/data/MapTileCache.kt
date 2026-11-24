package id.bangsadigital.riosense.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class MapTileStyle {
    /** Pure dark Obsidian grid background (100% offline, zero tiles). */
    GRID,
    /** Google-style dark road map rendered from free OpenStreetMap tiles (no API key). */
    ROAD,
    /** Real satellite imagery rendered from free Esri World Imagery tiles (no API key). */
    SATELLITE
}

data class MapTileKey(
    val z: Int,
    val x: Int,
    val y: Int,
    val style: MapTileStyle = MapTileStyle.ROAD
)

/**
 * Lightweight, zero-SDK-overhead Dark Map Tile loader & local disk cache.
 * Supports free OpenStreetMap road tiles (Obsidian Dark ColorMatrix) and free
 * Esri World Imagery satellite tiles — zero API key required, cached locally for offline reuse.
 */
object MapTileCache {

    suspend fun loadTile(context: Context, key: MapTileKey): ImageBitmap? = withContext(Dispatchers.IO) {
        if (key.style == MapTileStyle.GRID) return@withContext null

        val dirName = when (key.style) {
            MapTileStyle.ROAD -> "map_tiles_v2"
            MapTileStyle.SATELLITE -> "map_tiles_sat_v1"
            MapTileStyle.GRID -> return@withContext null
        }
        val cacheDir = File(context.applicationContext.cacheDir, dirName).apply { mkdirs() }
        val tileFile = File(cacheDir, "${key.z}_${key.x}_${key.y}.png")

        if (tileFile.exists() && tileFile.length() > 100L) {
            decodeFile(tileFile)?.let { return@withContext it }
        }

        val urlStr = when (key.style) {
            MapTileStyle.ROAD -> "https://tile.openstreetmap.org/${key.z}/${key.x}/${key.y}.png"
            MapTileStyle.SATELLITE ->
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/${key.z}/${key.y}/${key.x}"
            MapTileStyle.GRID -> return@withContext null
        }

        runCatching {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "RioSense-Automotive-HUD/1.0 (https://bangsadigital.id)")
                requestMethod = "GET"
            }
            try {
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.size > 100) {
                        val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
                        val styledBitmap = when (key.style) {
                            MapTileStyle.ROAD -> applyObsidianDarkStyle(rawBitmap)
                            MapTileStyle.SATELLITE -> applySatelliteNightTone(rawBitmap)
                            MapTileStyle.GRID -> rawBitmap
                        }
                        if (styledBitmap !== rawBitmap) rawBitmap.recycle()

                        val tmp = File(cacheDir, "${key.z}_${key.x}_${key.y}.tmp")
                        FileOutputStream(tmp).use { out ->
                            styledBitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                        }
                        if (tileFile.exists()) tileFile.delete()
                        tmp.renameTo(tileFile)
                        styledBitmap.asImageBitmap()
                    } else null
                } else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /**
     * Converts a light OpenStreetMap tile into a low-glare Obsidian automotive dark road map:
     * 1. Invert luminance while preserving semantic hues (invert + 180 deg hue-rotate).
     * 2. Desaturate to calm obsidian slate so the Cobalt route polyline stands out crisply.
     * 3. Lift shadows slightly so dark road surfaces remain distinguishable from terrain.
     */
    private fun applyObsidianDarkStyle(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val invertHueMatrix = ColorMatrix(
            floatArrayOf(
                0.574f, -1.430f, -0.144f, 0f, 255f,
                -0.426f, -0.430f, -0.144f, 0f, 255f,
                -0.426f, -1.430f, 0.856f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val satMatrix = ColorMatrix().apply { setSaturation(0.45f) }
        val toneMatrix = ColorMatrix(
            floatArrayOf(
                0.86f, 0f, 0f, 0f, 12f,
                0f, 0.88f, 0f, 0f, 15f,
                0f, 0f, 0.94f, 0f, 22f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val combined = ColorMatrix().apply {
            postConcat(invertHueMatrix)
            postConcat(satMatrix)
            postConcat(toneMatrix)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(combined)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    /**
     * Gently dims daylight satellite imagery so it fits a dark automotive cabin without glare
     * and keeps the bright route polyline effortless to read.
     */
    private fun applySatelliteNightTone(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val dimMatrix = ColorMatrix(
            floatArrayOf(
                0.82f, 0f, 0f, 0f, 0f,
                0f, 0.84f, 0f, 0f, 0f,
                0f, 0f, 0.88f, 0f, 4f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(dimMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun decodeFile(file: File): ImageBitmap? = runCatching {
        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
    }.getOrNull()
}
