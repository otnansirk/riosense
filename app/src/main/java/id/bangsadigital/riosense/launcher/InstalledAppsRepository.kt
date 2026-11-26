package id.bangsadigital.riosense.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import id.bangsadigital.riosense.model.InstalledAppInfo
import id.bangsadigital.riosense.model.LauncherSettings

/**
 * Fast, memory-bounded PackageManager repository and icon cache for the App Drawer and Global Dock.
 */
object InstalledAppsRepository {

    private val iconCache = object : LruCache<String, Bitmap>(64) {}

    private val priorityAutomotivePackages = listOf(
        "com.google.android.apps.maps",
        "com.waze",
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "com.google.android.youtube",
        "com.android.chrome"
    )

    private val automotiveKeywords = listOf(
        "radio", "dsp", "music", "zlink", "carlink", "bt", "bluetooth", "phone", "settings"
    )

    fun loadInstalledApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val selfPkg = context.packageName
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = runCatching {
            pm.queryIntentActivities(intent, 0)
        }.getOrDefault(emptyList())

        val byPackage = LinkedHashMap<String, InstalledAppInfo>()
        for (info in resolved) {
            val actInfo = info.activityInfo ?: continue
            val pkg = actInfo.packageName ?: continue
            if (pkg == selfPkg || byPackage.containsKey(pkg)) continue

            val label = runCatching {
                info.loadLabel(pm).toString().trim()
            }.getOrDefault(pkg).ifEmpty { pkg }

            val isSys = (actInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            byPackage[pkg] = InstalledAppInfo(
                packageName = pkg,
                label = label,
                isSystemApp = isSys
            )
        }

        return byPackage.values.sortedWith(
            compareBy<InstalledAppInfo> { it.label.lowercase() }.thenBy { it.packageName }
        )
    }

    fun loadDefaultPinnedPackages(context: Context): List<String> {
        val installed = loadInstalledApps(context)
        if (installed.isEmpty()) return emptyList()

        val installedByPkg = installed.associateBy { it.packageName }
        val result = LinkedHashSet<String>()

        for (pkg in priorityAutomotivePackages) {
            if (installedByPkg.containsKey(pkg)) {
                result.add(pkg)
                if (result.size >= 5) return result.toList()
            }
        }

        for (app in installed) {
            val lowerPkg = app.packageName.lowercase()
            val lowerLabel = app.label.lowercase()
            if (automotiveKeywords.any { kw -> lowerPkg.contains(kw) || lowerLabel.contains(kw) }) {
                result.add(app.packageName)
                if (result.size >= 5) return result.toList()
            }
        }

        for (app in installed) {
            result.add(app.packageName)
            if (result.size >= 5) break
        }
        return result.take(LauncherSettings.MAX_PINNED_APPS)
    }

    fun getAppIconBitmap(context: Context, packageName: String, sizePx: Int = 128): Bitmap? {
        val cacheKey = "${packageName}_$sizePx"
        synchronized(iconCache) {
            iconCache.get(cacheKey)?.let { if (!it.isRecycled) return it }
        }
        val bmp = runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            bitmap
        }.getOrNull() ?: return null

        synchronized(iconCache) {
            iconCache.put(cacheKey, bmp)
        }
        return bmp
    }

    fun launchApp(context: Context, packageName: String): Boolean {
        return runCatching {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
                ?: return false
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    fun isRioSenseDefaultLauncher(context: Context): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = runCatching {
            context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    fun openHomeLauncherSettings(context: Context) {
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return
        }
    }

    fun openOverlayPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }
}
