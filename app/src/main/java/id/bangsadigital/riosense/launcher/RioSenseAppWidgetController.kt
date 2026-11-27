package id.bangsadigital.riosense.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

const val RIOSENSE_APPWIDGET_HOST_ID = 0x52494F

data class AvailableAppWidget(
    val providerInfo: AppWidgetProviderInfo,
    val appPackage: String,
    val appLabel: String,
    val widgetLabel: String,
    val defaultColSpan: Int,
    val defaultRowSpan: Int
)

/**
 * Manages real third-party Android home-screen widgets via [AppWidgetHost] & [AppWidgetManager].
 * Must be constructed during `ComponentActivity.onCreate` so permission/configuration
 * activity result launchers are registered before `STARTED`.
 */
class RioSenseAppWidgetController(private val activity: ComponentActivity) {

    private val appContext = activity.applicationContext
    private val appWidgetManager: AppWidgetManager? = runCatching {
        AppWidgetManager.getInstance(appContext)
    }.getOrNull()
    private val appWidgetHost: AppWidgetHost? = runCatching {
        AppWidgetHost(appContext, RIOSENSE_APPWIDGET_HOST_ID)
    }.getOrNull()

    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingProvider: AppWidgetProviderInfo? = null
    private var pendingSpan: Pair<Int, Int> = 2 to 2
    private var pendingCallback: ((Int, AppWidgetProviderInfo, Pair<Int, Int>) -> Unit)? = null

    private val bindLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingWidgetId
        val info = pendingProvider
        val span = pendingSpan
        val cb = pendingCallback
        if (result.resultCode == Activity.RESULT_OK && id > 0 && info != null && cb != null) {
            continueAfterBind(id, info, span, cb)
        } else {
            if (id > 0) deleteWidgetId(id)
            clearPending()
        }
    }

    private val configureLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingWidgetId
        val info = pendingProvider
        val span = pendingSpan
        val cb = pendingCallback
        clearPending()
        if (result.resultCode == Activity.RESULT_OK && id > 0 && info != null && cb != null) {
            cb(id, info, span)
        } else if (id > 0) {
            deleteWidgetId(id)
        }
    }

    fun startListening() {
        runCatching { appWidgetHost?.startListening() }
    }

    fun stopListening() {
        runCatching { appWidgetHost?.stopListening() }
    }

    fun deleteWidgetId(appWidgetId: Int) {
        if (appWidgetId <= 0) return
        runCatching { appWidgetHost?.deleteAppWidgetId(appWidgetId) }
    }

    fun requestAddAppWidget(
        providerInfo: AppWidgetProviderInfo,
        defaultSpan: Pair<Int, Int>,
        onReady: (appWidgetId: Int, info: AppWidgetProviderInfo, span: Pair<Int, Int>) -> Unit
    ) {
        val manager = appWidgetManager ?: return
        val host = appWidgetHost ?: return

        // Release any previous unfinished allocation cleanly before starting a new request.
        if (pendingWidgetId > 0) {
            deleteWidgetId(pendingWidgetId)
            clearPending()
        }

        val id = runCatching { host.allocateAppWidgetId() }.getOrElse { return }
        if (id <= 0) return

        val allowed = runCatching {
            manager.bindAppWidgetIdIfAllowed(id, providerInfo.provider)
        }.getOrDefault(false)

        if (allowed) {
            continueAfterBind(id, providerInfo, defaultSpan, onReady)
        } else {
            pendingWidgetId = id
            pendingProvider = providerInfo
            pendingSpan = defaultSpan
            pendingCallback = onReady
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
            }
            runCatching { bindLauncher.launch(intent) }.onFailure {
                deleteWidgetId(id)
                clearPending()
            }
        }
    }

    private fun continueAfterBind(
        appWidgetId: Int,
        info: AppWidgetProviderInfo,
        defaultSpan: Pair<Int, Int>,
        onReady: (Int, AppWidgetProviderInfo, Pair<Int, Int>) -> Unit
    ) {
        val configureComponent = info.configure
        if (configureComponent != null) {
            pendingWidgetId = appWidgetId
            pendingProvider = info
            pendingSpan = defaultSpan
            pendingCallback = onReady

            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configureComponent
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            runCatching {
                configureLauncher.launch(configIntent)
            }.onFailure {
                // Some OEM widget providers declare non-exported configure activities; commit directly if launch fails.
                clearPending()
                onReady(appWidgetId, info, defaultSpan)
            }
        } else {
            clearPending()
            onReady(appWidgetId, info, defaultSpan)
        }
    }

    private fun clearPending() {
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider = null
        pendingCallback = null
    }

    fun createHostView(context: Context, appWidgetId: Int): AppWidgetHostView? {
        if (appWidgetId <= 0) return null
        val manager = appWidgetManager ?: return null
        val host = appWidgetHost ?: return null
        val info = runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull() ?: return null
        return runCatching { host.createView(context, appWidgetId, info) }.getOrNull()
    }

    fun updateHostViewSize(hostView: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        val w = widthDp.coerceAtLeast(48)
        val h = heightDp.coerceAtLeast(48)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hostView.updateAppWidgetSize(Bundle.EMPTY, listOf(SizeF(w.toFloat(), h.toFloat())))
            } else {
                @Suppress("DEPRECATION")
                hostView.updateAppWidgetSize(Bundle.EMPTY, w, h, w, h)
            }
        }
    }

    fun loadInstalledWidgets(gridCols: Int): List<AvailableAppWidget> {
        val manager = appWidgetManager ?: return emptyList()
        val pm = appContext.packageManager
        val providers = runCatching { manager.installedProviders }.getOrNull().orEmpty()
        val safeCols = gridCols.coerceAtLeast(4)

        return providers.mapNotNull { info ->
            val pkg = info.provider?.packageName ?: return@mapNotNull null
            val widgetLabel = runCatching { info.loadLabel(pm).toString().trim() }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: info.provider?.shortClassName
                ?: pkg
            val appLabel = runCatching {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString().trim()
            }.getOrNull()?.takeIf { it.isNotEmpty() } ?: pkg

            val (colSpan, rowSpan) = computeDefaultSpan(info, safeCols)
            AvailableAppWidget(
                providerInfo = info,
                appPackage = pkg,
                appLabel = appLabel,
                widgetLabel = widgetLabel,
                defaultColSpan = colSpan,
                defaultRowSpan = rowSpan
            )
        }.sortedWith(
            compareBy<AvailableAppWidget> { it.appLabel.lowercase() }
                .thenBy { it.widgetLabel.lowercase() }
        )
    }

    private fun computeDefaultSpan(info: AppWidgetProviderInfo, gridCols: Int): Pair<Int, Int> {
        val rawCols = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0) {
            info.targetCellWidth
        } else {
            ceil((info.minWidth.coerceAtLeast(70) + 20f) / 105f).toInt()
        }
        val rawRows = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellHeight > 0) {
            info.targetCellHeight
        } else {
            ceil((info.minHeight.coerceAtLeast(60) + 16f) / 90f).toInt()
        }
        return rawCols.coerceIn(1, gridCols) to rawRows.coerceIn(1, 4)
    }

    suspend fun loadWidgetPreviewBitmap(
        context: Context,
        info: AppWidgetProviderInfo,
        maxWidthPx: Int = 320,
        maxHeightPx: Int = 200
    ): Bitmap? {
        val previewFromDrawable = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = info.loadPreviewImage(appContext, 0) ?: return@runCatching null
                val rawW = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxWidthPx
                val rawH = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxHeightPx
                val scale = minOf(1f, maxWidthPx.toFloat() / rawW, maxHeightPx.toFloat() / rawH)
                val targetW = (rawW * scale).toInt().coerceIn(24, maxWidthPx)
                val targetH = (rawH * scale).toInt().coerceIn(24, maxHeightPx)
                drawable.toBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            }.getOrNull()
        }
        if (previewFromDrawable != null) return previewFromDrawable

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.previewLayout != 0) {
            return withContext(Dispatchers.Main) {
                runCatching {
                    val container = FrameLayout(context)
                    val remoteView = RemoteViews(info.provider.packageName, info.previewLayout)
                        .apply(context, container)
                    val atMostW = View.MeasureSpec.makeMeasureSpec(maxWidthPx, View.MeasureSpec.AT_MOST)
                    val atMostH = View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST)
                    remoteView.measure(atMostW, atMostH)
                    if (remoteView.measuredWidth <= 0 || remoteView.measuredHeight <= 0) {
                        remoteView.measure(
                            View.MeasureSpec.makeMeasureSpec(maxWidthPx, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.EXACTLY)
                        )
                    }
                    val w = remoteView.measuredWidth.coerceIn(24, maxWidthPx)
                    val h = remoteView.measuredHeight.coerceIn(24, maxHeightPx)
                    remoteView.layout(0, 0, w, h)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    remoteView.draw(canvas)
                    bmp
                }.getOrNull()
            }
        }
        return null
    }
}
