package id.bangsadigital.riosense.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import id.bangsadigital.riosense.MainActivity
import id.bangsadigital.riosense.R
import id.bangsadigital.riosense.model.DockDisplayMode
import id.bangsadigital.riosense.model.DockPosition
import id.bangsadigital.riosense.model.LAUNCHER_PAGE_APPS
import id.bangsadigital.riosense.model.LAUNCHER_PAGE_HOME
import id.bangsadigital.riosense.model.LauncherSettings
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lightweight native WindowManager overlay service providing a macOS-inspired Obsidian Dock
 * across all Head Unit apps (Google Maps, Spotify, YouTube, Radio, etc.).
 *
 * Uses pure Android Views (~4-6 MB RAM) rather than a secondary Compose runtime so 6 GB / 8 GB
 * Android Head Units remain completely smooth during background navigation.
 */
class GlobalDockOverlayService : Service() {

    companion object {
        const val ACTION_SYNC_SETTINGS = "id.bangsadigital.riosense.action.SYNC_GLOBAL_DOCK"
        const val EXTRA_DOCK_ENABLED = "dock_enabled"
        const val EXTRA_DOCK_MODE = "dock_mode"
        const val EXTRA_DOCK_POSITION = "dock_position"
        const val EXTRA_PINNED_PACKAGES = "pinned_packages"

        private const val CHANNEL_ID = "riosense_global_dock"
        private const val NOTIF_ID = 4041
        private const val AUTO_HIDE_DELAY_MS = 4500L

        fun sync(context: Context, settings: LauncherSettings) {
            val appCtx = context.applicationContext
            if (!settings.dockEnabled || !Settings.canDrawOverlays(appCtx)) {
                runCatching { appCtx.stopService(Intent(appCtx, GlobalDockOverlayService::class.java)) }
                return
            }
            val intent = Intent(appCtx, GlobalDockOverlayService::class.java).apply {
                action = ACTION_SYNC_SETTINGS
                putExtra(EXTRA_DOCK_ENABLED, settings.dockEnabled)
                putExtra(EXTRA_DOCK_MODE, settings.dockDisplayMode.name)
                putExtra(EXTRA_DOCK_POSITION, settings.dockPosition.name)
                putStringArrayListExtra(EXTRA_PINNED_PACKAGES, ArrayList(settings.pinnedPackages))
            }
            runCatching {
                ContextCompat.startForegroundService(appCtx, intent)
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentSettings = LauncherSettings()
    private var permanentDockView: View? = null
    private var transientDockView: View? = null
    private val edgeStripViews = mutableMapOf<DockPosition, View>()

    private val autoHideRunnable = Runnable {
        hideTransientDock()
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val safeIntent = intent
        if (safeIntent != null && (safeIntent.action == ACTION_SYNC_SETTINGS || safeIntent.extras != null)) {
            val enabled = safeIntent.getBooleanExtra(EXTRA_DOCK_ENABLED, true)
            if (!enabled) {
                stopSelf()
                return START_NOT_STICKY
            }
            val modeName = safeIntent.getStringExtra(EXTRA_DOCK_MODE)
            val posName = safeIntent.getStringExtra(EXTRA_DOCK_POSITION)
            val pinned = safeIntent.getStringArrayListExtra(EXTRA_PINNED_PACKAGES)

            val parsedMode = runCatching {
                DockDisplayMode.valueOf(modeName ?: currentSettings.dockDisplayMode.name)
            }.getOrDefault(currentSettings.dockDisplayMode)

            val parsedPos = runCatching {
                DockPosition.valueOf(posName ?: currentSettings.dockPosition.name)
            }.getOrDefault(currentSettings.dockPosition)

            currentSettings = LauncherSettings(
                dockEnabled = enabled,
                dockDisplayMode = parsedMode,
                dockPosition = parsedPos,
                pinnedPackages = (pinned ?: currentSettings.pinnedPackages).take(LauncherSettings.MAX_PINNED_APPS)
            )
            rebuildOverlays()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mainHandler.removeCallbacks(autoHideRunnable)
        clearAllOverlays()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RioSense Global Dock",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_LAUNCHER_PAGE, LAUNCHER_PAGE_HOME)
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("RioSense Global Dock")
            .setContentText("Quick access bar active")
            .setSmallIcon(R.drawable.ic_kia_modern)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun clearAllOverlays() {
        permanentDockView?.let { v ->
            runCatching { windowManager.removeViewImmediate(v) }
        }
        permanentDockView = null

        transientDockView?.let { v ->
            runCatching { windowManager.removeViewImmediate(v) }
        }
        transientDockView = null

        edgeStripViews.values.forEach { v ->
            runCatching { windowManager.removeViewImmediate(v) }
        }
        edgeStripViews.clear()
    }

    private fun rebuildOverlays() {
        mainHandler.removeCallbacks(autoHideRunnable)
        clearAllOverlays()

        if (!currentSettings.dockEnabled || !Settings.canDrawOverlays(this)) return

        when (currentSettings.dockDisplayMode) {
            DockDisplayMode.ALWAYS_VISIBLE -> {
                val view = buildDockPillView(currentSettings.dockPosition, autoHideOnAction = false)
                val params = createDockLayoutParams(currentSettings.dockPosition, watchOutsideTouch = false)
                runCatching {
                    windowManager.addView(view, params)
                    permanentDockView = view
                }
            }
            DockDisplayMode.AUTO_HIDE_SWIPE -> {
                DockPosition.entries.forEach { edge ->
                    attachEdgeSwipeTrigger(edge)
                }
            }
        }
    }

    private fun attachEdgeSwipeTrigger(edge: DockPosition) {
        val density = resources.displayMetrics.density
        val stripContainer = FrameLayout(this)

        val pillView = View(this).apply {
            val pillDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 4f * density
                setColor(
                    when (edge) {
                        DockPosition.TOP -> 0x44B8C0CC
                        DockPosition.BOTTOM -> 0x66B8C0CC
                        else -> 0x55B8C0CC
                    }
                )
            }
            background = pillDrawable
        }

        val pillParams = when (edge) {
            DockPosition.BOTTOM -> FrameLayout.LayoutParams(dp(44), dp(3)).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(4)
            }
            DockPosition.TOP -> FrameLayout.LayoutParams(dp(38), dp(3)).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(3)
            }
            DockPosition.LEFT -> FrameLayout.LayoutParams(dp(3), dp(36)).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = dp(4)
            }
            DockPosition.RIGHT -> FrameLayout.LayoutParams(dp(3), dp(36)).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                marginEnd = dp(4)
            }
        }
        stripContainer.addView(pillView, pillParams)

        var downX = 0f
        var downY = 0f
        var triggered = false
        val swipeThresholdPx = dp(18).toFloat()

        stripContainer.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    triggered = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!triggered) {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        val swipedIn = when (edge) {
                            DockPosition.BOTTOM -> dy <= -swipeThresholdPx
                            DockPosition.TOP -> dy >= swipeThresholdPx
                            DockPosition.LEFT -> dx >= swipeThresholdPx
                            DockPosition.RIGHT -> dx <= -swipeThresholdPx
                        }
                        if (swipedIn) {
                            triggered = true
                            showTransientDock(edge)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!triggered) {
                        val totalMove = abs(event.rawX - downX) + abs(event.rawY - downY)
                        if (totalMove < dp(12)) {
                            showTransientDock(edge)
                        }
                    }
                    true
                }
                else -> false
            }
        }

        val params = WindowManager.LayoutParams(
            when (edge) {
                DockPosition.BOTTOM -> dp(180)
                DockPosition.TOP -> dp(140)
                DockPosition.LEFT, DockPosition.RIGHT -> dp(16)
            },
            when (edge) {
                DockPosition.BOTTOM -> dp(16)
                DockPosition.TOP -> dp(14)
                DockPosition.LEFT, DockPosition.RIGHT -> dp(140)
            },
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = when (edge) {
                DockPosition.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                DockPosition.TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                DockPosition.LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
                DockPosition.RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
            }
        }

        runCatching {
            windowManager.addView(stripContainer, params)
            edgeStripViews[edge] = stripContainer
        }
    }

    private fun showTransientDock(edge: DockPosition) {
        mainHandler.removeCallbacks(autoHideRunnable)
        transientDockView?.let { old ->
            runCatching { windowManager.removeViewImmediate(old) }
        }
        transientDockView = null

        val dockView = buildDockPillView(edge, autoHideOnAction = true).apply {
            alpha = 0f
            val slideDist = dp(24).toFloat()
            when (edge) {
                DockPosition.BOTTOM -> translationY = slideDist
                DockPosition.TOP -> translationY = -slideDist
                DockPosition.LEFT -> translationX = -slideDist
                DockPosition.RIGHT -> translationX = slideDist
            }
        }

        val params = createDockLayoutParams(edge, watchOutsideTouch = true)
        dockView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hideTransientDock()
                true
            } else {
                armAutoHideTimer()
                false
            }
        }

        runCatching {
            windowManager.addView(dockView, params)
            transientDockView = dockView
            dockView.animate()
                .alpha(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator())
                .start()
            armAutoHideTimer()
        }
    }

    private fun hideTransientDock() {
        mainHandler.removeCallbacks(autoHideRunnable)
        transientDockView?.let { v ->
            runCatching { windowManager.removeViewImmediate(v) }
        }
        transientDockView = null
    }

    private fun armAutoHideTimer() {
        mainHandler.removeCallbacks(autoHideRunnable)
        mainHandler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY_MS)
    }

    private fun createDockLayoutParams(
        position: DockPosition,
        watchOutsideTouch: Boolean
    ): WindowManager.LayoutParams {
        val marginPx = dp(10)
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (watchOutsideTouch) {
            flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = when (position) {
                DockPosition.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                DockPosition.TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                DockPosition.LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
                DockPosition.RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
            }
            when (position) {
                DockPosition.BOTTOM, DockPosition.TOP -> y = marginPx
                DockPosition.LEFT, DockPosition.RIGHT -> x = marginPx
            }
        }
    }

    private fun buildDockPillView(position: DockPosition, autoHideOnAction: Boolean): View {
        val isHorizontal = position == DockPosition.BOTTOM || position == DockPosition.TOP
        val density = resources.displayMetrics.density

        val containerBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f * density
            setColor(0xEB0E1016.toInt())
            setStroke(dp(1), 0x444A5160)
        }

        val outerRow = LinearLayout(this).apply {
            orientation = if (isHorizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = containerBg
            elevation = 10f * density
            setPadding(
                dp(if (isHorizontal) 10 else 6),
                dp(if (isHorizontal) 6 else 10),
                dp(if (isHorizontal) 10 else 6),
                dp(if (isHorizontal) 6 else 10)
            )
        }

        // 1. Fixed RioSense Home Button (always visible)
        outerRow.addView(
            createDockIconButton(
                bitmap = createHomeVectorBitmap(dp(24)),
                contentPaddingPx = dp(7),
                isHorizontal = isHorizontal,
                isLast = false
            ) {
                openRioSensePage(LAUNCHER_PAGE_HOME)
                if (autoHideOnAction) hideTransientDock()
            }
        )

        // 2. Fixed App Drawer Button (always visible)
        val pinned = currentSettings.pinnedPackages.take(LauncherSettings.MAX_PINNED_APPS)
        outerRow.addView(
            createDockIconButton(
                bitmap = createDrawerGridBitmap(dp(22)),
                contentPaddingPx = dp(8),
                isHorizontal = isHorizontal,
                isLast = pinned.isEmpty()
            ) {
                openRioSensePage(LAUNCHER_PAGE_APPS)
                if (autoHideOnAction) hideTransientDock()
            }
        )

        if (pinned.isNotEmpty()) {
            // Fixed Subtle Obsidian Hairline Separator
            val divider = View(this).apply {
                setBackgroundColor(0x44858D9D)
            }
            val divParams = if (isHorizontal) {
                LinearLayout.LayoutParams(dp(1), dp(22)).apply {
                    marginStart = dp(4)
                    marginEnd = dp(6)
                }
            } else {
                LinearLayout.LayoutParams(dp(22), dp(1)).apply {
                    topMargin = dp(4)
                    bottomMargin = dp(6)
                }
            }
            outerRow.addView(divider, divParams)

            // 3. Independently Scrollable Pinned Apps Container ONLY
            val pinnedStrip = LinearLayout(this).apply {
                orientation = if (isHorizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            pinned.forEachIndexed { idx, pkg ->
                val bmp = InstalledAppsRepository.getAppIconBitmap(this, pkg, 128)
                    ?: createFallbackAppBitmap(dp(24))
                pinnedStrip.addView(
                    createDockIconButton(
                        bitmap = bmp,
                        contentPaddingPx = dp(3),
                        isHorizontal = isHorizontal,
                        isLast = idx == pinned.lastIndex
                    ) {
                        InstalledAppsRepository.launchApp(this, pkg)
                        if (autoHideOnAction) hideTransientDock()
                    }
                )
            }

            if (isHorizontal) {
                val maxPinnedWidthPx = minOf(dp(340), (resources.displayMetrics.widthPixels * 0.52f).roundToInt())
                val scroller = MaxSizeHorizontalScrollView(this, maxPinnedWidthPx).apply {
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    addView(
                        pinnedStrip,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
                outerRow.addView(
                    scroller,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            } else {
                val maxPinnedHeightPx = minOf(dp(240), (resources.displayMetrics.heightPixels * 0.50f).roundToInt())
                val scroller = MaxSizeScrollView(this, maxPinnedHeightPx).apply {
                    isVerticalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    addView(
                        pinnedStrip,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
                outerRow.addView(
                    scroller,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }

        return outerRow
    }

    private class MaxSizeHorizontalScrollView(
        context: Context,
        private val maxWidthPx: Int
    ) : HorizontalScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val clampedSpec = MeasureSpec.makeMeasureSpec(maxWidthPx, MeasureSpec.AT_MOST)
            super.onMeasure(clampedSpec, heightMeasureSpec)
        }
    }

    private class MaxSizeScrollView(
        context: Context,
        private val maxHeightPx: Int
    ) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val clampedSpec = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, clampedSpec)
        }
    }

    private fun createDockIconButton(
        bitmap: Bitmap,
        contentPaddingPx: Int,
        isHorizontal: Boolean,
        isLast: Boolean,
        onClick: () -> Unit
    ): ImageView {
        val density = resources.displayMetrics.density
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 11f * density
            setColor(0x162C313B)
        }
        val ripple = RippleDrawable(ColorStateList.valueOf(0x38ECEFF4), shape, shape)
        return ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = ripple
            setPadding(contentPaddingPx, contentPaddingPx, contentPaddingPx, contentPaddingPx)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                if (!isLast) {
                    if (isHorizontal) marginEnd = dp(4) else bottomMargin = dp(4)
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun openRioSensePage(page: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_LAUNCHER_PAGE, page)
        }
        runCatching { startActivity(intent) }
    }

    private fun createHomeVectorBitmap(sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFECEFF4.toInt()
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.085f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val w = sizePx.toFloat()
        val h = sizePx.toFloat()
        val path = Path().apply {
            moveTo(w * 0.16f, h * 0.48f)
            lineTo(w * 0.50f, h * 0.18f)
            lineTo(w * 0.84f, h * 0.48f)
            moveTo(w * 0.25f, h * 0.44f)
            lineTo(w * 0.25f, h * 0.82f)
            lineTo(w * 0.75f, h * 0.82f)
            lineTo(w * 0.75f, h * 0.44f)
        }
        canvas.drawPath(path, paint)
        return bmp
    }

    private fun createDrawerGridBitmap(sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFB8C0CC.toInt()
            style = Paint.Style.FILL
        }
        val cell = sizePx * 0.18f
        val r = sizePx * 0.05f
        val coords = floatArrayOf(0.20f, 0.50f, 0.80f)
        for (ry in coords) {
            for (rx in coords) {
                val cx = sizePx * rx
                val cy = sizePx * ry
                val rect = RectF(cx - cell * 0.5f, cy - cell * 0.5f, cx + cell * 0.5f, cy + cell * 0.5f)
                canvas.drawRoundRect(rect, r, r, paint)
            }
        }
        return bmp
    }

    private fun createFallbackAppBitmap(sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF63B3ED.toInt()
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.09f
        }
        val m = sizePx * 0.2f
        canvas.drawRoundRect(RectF(m, m, sizePx - m, sizePx - m), m * 0.6f, m * 0.6f, paint)
        return bmp
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
