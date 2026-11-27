package id.bangsadigital.riosense.ui.components

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import id.bangsadigital.riosense.launcher.AvailableAppWidget
import id.bangsadigital.riosense.launcher.MediaSessionBridge
import id.bangsadigital.riosense.launcher.RioSenseAppWidgetController
import id.bangsadigital.riosense.launcher.WeatherRepository
import id.bangsadigital.riosense.model.BuiltInWidgetType
import id.bangsadigital.riosense.model.MAX_WIDGET_PAGES
import id.bangsadigital.riosense.model.MaintenanceState
import id.bangsadigital.riosense.model.PlacedWidgetItem
import id.bangsadigital.riosense.model.VehicleTelemetry
import id.bangsadigital.riosense.model.WidgetItemKind
import id.bangsadigital.riosense.model.resolveWidgetReflow
import id.bangsadigital.riosense.model.resolveWidgetReflowWithNudge
import id.bangsadigital.riosense.ui.i18n.AppLanguage
import id.bangsadigital.riosense.ui.i18n.LocalAppLanguage
import id.bangsadigital.riosense.ui.i18n.appStrings
import id.bangsadigital.riosense.ui.theme.ObsidianColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private data class GridCellRect(
    val col: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int
)

private enum class ResizeHandle {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    BOTTOM_RIGHT
}

/**
 * Tablet/iPadOS-style customizable widget grid page featuring iPhone-like jiggle edit mode,
 * borderless monochrome Obsidian styling with top-left `−` remove badges, smooth spring-animated
 * neighbor reflow when dragging or resizing near other widgets, and support for both RioSense
 * built-in cards and real third-party Android app widgets.
 */
@Composable
fun CustomizableWidgetGridPage(
    pageIndex: Int,
    totalWidgetPages: Int,
    items: List<PlacedWidgetItem>,
    gridCols: Int,
    gridRows: Int,
    telemetry: VehicleTelemetry,
    maintenance: MaintenanceState,
    isPageVisible: Boolean,
    isEditMode: Boolean,
    appWidgetController: RioSenseAppWidgetController,
    onToggleEditMode: (Boolean) -> Unit,
    onOpenWidgetPicker: () -> Unit,
    onAddPage: () -> Unit,
    onRemovePage: () -> Unit,
    onCommitPageLayout: (List<PlacedWidgetItem>) -> Unit,
    onRemoveItem: (id: String) -> Unit,
    onOpenControlsTab: (ControlsTab) -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = appStrings()
    val isId = LocalAppLanguage.current == AppLanguage.ID

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var isSystemMusicActive by remember { mutableStateOf(false) }

    LaunchedEffect(isPageVisible) {
        if (!isPageVisible) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            val millisIntoMinute = nowMs % 60_000L
            delay((60_000L - millisIntoMinute).coerceAtLeast(1000L))
        }
    }

    LaunchedEffect(isPageVisible) {
        if (!isPageVisible) return@LaunchedEffect
        MediaSessionBridge.refresh(context)
        WeatherRepository.refreshIfStale(context)
    }

    LaunchedEffect(isPageVisible, context) {
        if (!isPageVisible) {
            isSystemMusicActive = false
            return@LaunchedEffect
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        while (true) {
            isSystemMusicActive = audioManager?.isMusicActive == true
            delay(700L)
        }
    }

    val nowPlaying by MediaSessionBridge.nowPlaying.collectAsState()
    val isAudioActive = nowPlaying.isPlaying || isSystemMusicActive
    var trackProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(isPageVisible, nowPlaying) {
        fun computeProgress(): Float {
            if (nowPlaying.durationMs <= 0L) return 0f
            val delta = if (nowPlaying.isPlaying && nowPlaying.lastPositionUpdateElapsedMs > 0L) {
                ((SystemClock.elapsedRealtime() - nowPlaying.lastPositionUpdateElapsedMs) * nowPlaying.playbackSpeed)
                    .toLong()
                    .coerceAtLeast(0L)
            } else {
                0L
            }
            val pos = (nowPlaying.positionMs + delta).coerceIn(0L, nowPlaying.durationMs)
            return (pos.toFloat() / nowPlaying.durationMs.toFloat()).coerceIn(0f, 1f)
        }

        trackProgress = computeProgress()
        while (isPageVisible && nowPlaying.isPlaying && nowPlaying.durationMs > 0L) {
            delay(500L)
            trackProgress = computeProgress()
        }
    }

    val weatherState by WeatherRepository.weather.collectAsState()
    val weather = weatherState ?: remember { WeatherRepository.loadCached(context) }

    val timeStr = remember(nowMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowMs))
    }
    val dateStr = remember(nowMs) {
        SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date(nowMs))
    }

    val dueCount = remember(maintenance) { maintenance.dueOrOverdueItems().size }
    val odoText = remember(maintenance.currentOdometerKm) {
        NumberFormat.getIntegerInstance(Locale.US).format(maintenance.currentOdometerKm)
    }

    var livePageItems by remember(pageIndex) { mutableStateOf(items) }
    var selectedItemId by remember(pageIndex, isEditMode) { mutableStateOf<String?>(null) }
    var activeDragId by remember(pageIndex, isEditMode) { mutableStateOf<String?>(null) }
    var activeResizeId by remember(pageIndex, isEditMode) { mutableStateOf<String?>(null) }
    var dragStartItem by remember { mutableStateOf<PlacedWidgetItem?>(null) }
    var dragDeltaPx by remember { mutableStateOf(Offset.Zero) }
    var previewRect by remember { mutableStateOf<GridCellRect?>(null) }

    LaunchedEffect(items, activeDragId, activeResizeId) {
        if (activeDragId == null && activeResizeId == null) {
            livePageItems = items
        }
    }

    // Shared jiggle clock running only while in Edit Mode on the visible page
    val jiggleAngle = if (isEditMode && isPageVisible) {
        val transition = rememberInfiniteTransition(label = "widgetJiggle")
        val angle by transition.animateFloat(
            initialValue = -0.85f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 155, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "widgetJiggleAngle"
        )
        angle
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Strict monochrome borderless header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = s.widgetsTitle,
                        color = ObsidianColors.PureSilver,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (totalWidgetPages > 1) {
                        Text(
                            text = "${pageIndex + 1}/$totalWidgetPages",
                            color = ObsidianColors.DimSilver,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (isEditMode) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF171B24))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${gridCols}×${gridRows}",
                                color = ObsidianColors.MutedSilver,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Text(
                    text = if (isEditMode) {
                        if (isId) "Seret untuk geser posisi, ketuk untuk atur ukuran"
                        else "Drag to move widgets, tap a widget to resize"
                    } else {
                        s.widgetsSubtitle
                    },
                    color = ObsidianColors.DimSilver,
                    fontSize = 10.sp
                )
            }

            if (isEditMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderActionPill(
                        label = "+ Widget",
                        bgColor = Color(0xFF191D26),
                        textColor = ObsidianColors.PureSilver,
                        onClick = onOpenWidgetPicker
                    )
                    if (totalWidgetPages < MAX_WIDGET_PAGES) {
                        HeaderActionPill(
                            label = if (isId) "+ Halaman" else "+ Page",
                            bgColor = Color(0xFF191D26),
                            textColor = ObsidianColors.MutedSilver,
                            onClick = onAddPage
                        )
                    }
                    if (totalWidgetPages > 1) {
                        HeaderActionPill(
                            label = if (isId) "Hapus Hal" else "Delete Page",
                            bgColor = Color(0xFF191D26),
                            textColor = ObsidianColors.MutedSilver,
                            onClick = onRemovePage
                        )
                    }
                    HeaderActionPill(
                        label = if (isId) "Selesai" else "Done",
                        bgColor = Color(0xFF252B38),
                        textColor = ObsidianColors.PureSilver,
                        onClick = { onToggleEditMode(false) }
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderPlusIconButton(onClick = onOpenWidgetPicker)
                    HeaderEditGridIconButton(onClick = { onToggleEditMode(true) })
                    LauncherHomeIconButton(onClick = onBackToHome)
                }
            }
        }

        // Interactive Adaptive Grid Canvas
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 10.dp, bottom = 20.dp)
                .pointerInput(isEditMode) {
                    detectTapGestures(
                        onTap = {
                            if (isEditMode) selectedItemId = null
                        },
                        onLongPress = {
                            if (!isEditMode) onToggleEditMode(true)
                        }
                    )
                }
        ) {
            val cellGap = 12.dp
            val density = LocalDensity.current
            val gapPx = with(density) { cellGap.toPx() }
            val totalW = with(density) { maxWidth.toPx() }
            val totalH = with(density) { maxHeight.toPx() }
            val cellW = ((totalW - gapPx * (gridCols - 1)) / gridCols).coerceAtLeast(24f)
            val cellH = ((totalH - gapPx * (gridRows - 1)) / gridRows).coerceAtLeast(24f)
            val stepX = cellW + gapPx
            val stepY = cellH + gapPx

            // Minimal obsidian alignment dots in Edit Mode (no grey grid boxes)
            if (isEditMode) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dotRadius = 2.dp.toPx()
                    val dotColor = Color(0xFF252B38)
                    for (r in 0 until gridRows) {
                        for (c in 0 until gridCols) {
                            drawCircle(
                                color = dotColor,
                                radius = dotRadius,
                                center = Offset(c * stepX + cellW * 0.5f, r * stepY + cellH * 0.5f)
                            )
                        }
                    }
                }
            }

            // Empty page call-to-action
            if (livePageItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isId) "Halaman widget ini masih kosong" else "This widget page is empty",
                            color = ObsidianColors.MutedSilver,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        HeaderActionPill(
                            label = if (isId) "+ Tambah Widget" else "+ Add Widget",
                            bgColor = Color(0xFF191D26),
                            textColor = ObsidianColors.PureSilver,
                            onClick = onOpenWidgetPicker
                        )
                    }
                }
            }

            // Subtle frosted obsidian snap landing shadow while dragging or resizing
            previewRect?.let { rect ->
                val pLeft = rect.col * stepX
                val pTop = rect.row * stepY
                val pWidth = rect.colSpan * cellW + (rect.colSpan - 1) * gapPx
                val pHeight = rect.rowSpan * cellH + (rect.rowSpan - 1) * gapPx
                Box(
                    modifier = Modifier
                        .offset { IntOffset(pLeft.roundToInt(), pTop.roundToInt()) }
                        .size(
                            width = with(density) { pWidth.toDp() },
                            height = with(density) { pHeight.toDp() }
                        )
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0x1CFFFFFF))
                )
            }

            val latestLiveItems by rememberUpdatedState(livePageItems)
            val latestPersistedItems by rememberUpdatedState(items)

            livePageItems.forEachIndexed { idx, item ->
                key(item.id) {
                    val clampedCol = item.col.coerceIn(0, (gridCols - 1).coerceAtLeast(0))
                    val clampedRow = item.row.coerceIn(0, (gridRows - 1).coerceAtLeast(0))
                    val clampedSpanW = item.colSpan.coerceIn(1, gridCols - clampedCol)
                    val clampedSpanH = item.rowSpan.coerceIn(1, gridRows - clampedRow)

                    val targetLeftPx = clampedCol * stepX
                    val targetTopPx = clampedRow * stepY
                    val targetWPx = clampedSpanW * cellW + (clampedSpanW - 1) * gapPx
                    val targetHPx = clampedSpanH * cellH + (clampedSpanH - 1) * gapPx

                    val springSpec = remember { spring<Float>(dampingRatio = 0.82f, stiffness = 420f) }
                    val animLeftPx by animateFloatAsState(targetLeftPx, springSpec, label = "wx")
                    val animTopPx by animateFloatAsState(targetTopPx, springSpec, label = "wy")
                    val animWPx by animateFloatAsState(targetWPx, springSpec, label = "ww")
                    val animHPx by animateFloatAsState(targetHPx, springSpec, label = "wh")

                    val isDraggingThis = isEditMode && activeDragId == item.id
                    val isResizingThis = isEditMode && activeResizeId == item.id
                    val startAnchor = dragStartItem
                    val curLeftPx = if (isDraggingThis && startAnchor != null) {
                        startAnchor.col * stepX + dragDeltaPx.x
                    } else {
                        animLeftPx
                    }
                    val curTopPx = if (isDraggingThis && startAnchor != null) {
                        startAnchor.row * stepY + dragDeltaPx.y
                    } else {
                        animTopPx
                    }
                    val curWPx = if (isDraggingThis) targetWPx else animWPx
                    val curHPx = if (isDraggingThis) targetHPx else animHPx

                    val boxWDp = with(density) { curWPx.coerceAtLeast(24f).toDp() }
                    val boxHDp = with(density) { curHPx.coerceAtLeast(24f).toDp() }

                    val isSelected = isEditMode && selectedItemId == item.id
                    val minSpanW = item.builtInType?.minColSpan ?: 1
                    val minSpanH = item.builtInType?.minRowSpan ?: 1
                    val latestItem by rememberUpdatedState(item)

                    val widgetRotation = when {
                        !isEditMode || isDraggingThis || isResizingThis -> 0f
                        idx % 2 == 0 -> jiggleAngle
                        else -> -jiggleAngle
                    }
                    val widgetScale = when {
                        isDraggingThis -> 1.03f
                        isEditMode -> 0.985f
                        else -> 1f
                    }
                    val widgetAlpha = when {
                        isDraggingThis -> 0.95f
                        isEditMode -> 0.92f
                        else -> 1f
                    }

                    Box(
                        modifier = Modifier
                            .zIndex(if (isDraggingThis || isResizingThis) 10f else if (isSelected) 5f else 1f)
                            .offset { IntOffset(curLeftPx.roundToInt(), curTopPx.roundToInt()) }
                            .size(width = boxWDp, height = boxHDp)
                            .graphicsLayer {
                                rotationZ = widgetRotation
                                scaleX = widgetScale
                                scaleY = widgetScale
                                alpha = widgetAlpha
                            }
                            .then(
                                if (!isEditMode) {
                                    // Passive iPhone hold-to-jiggle detector that never consumes normal taps
                                    Modifier.pointerInput(item.id) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val slop = viewConfiguration.touchSlop
                                            val deadline = SystemClock.uptimeMillis() + viewConfiguration.longPressTimeoutMillis
                                            var heldLongEnough = false
                                            while (true) {
                                                val remaining = deadline - SystemClock.uptimeMillis()
                                                if (remaining <= 0L) {
                                                    heldLongEnough = true
                                                    break
                                                }
                                                val event = withTimeoutOrNull(remaining) { awaitPointerEvent() }
                                                if (event == null) {
                                                    heldLongEnough = true
                                                    break
                                                }
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                if (!change.pressed) break
                                                if ((change.position - down.position).getDistance() > slop) break
                                            }
                                            if (heldLongEnough) {
                                                onToggleEditMode(true)
                                            }
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        when (item.kind) {
                            WidgetItemKind.BUILT_IN -> {
                                when (item.builtInType) {
                                    BuiltInWidgetType.CLOCK -> {
                                        RioSenseClockWidgetCard(
                                            timeStr = timeStr,
                                            dateStr = dateStr,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    BuiltInWidgetType.MUSIC -> {
                                        RioSenseMusicWidgetCard(
                                            nowPlaying = nowPlaying,
                                            isAudioActive = isAudioActive,
                                            isPageVisible = isPageVisible,
                                            trackProgress = trackProgress,
                                            isId = isId,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    BuiltInWidgetType.WEATHER -> {
                                        RioSenseWeatherWidgetCard(
                                            weather = weather,
                                            isId = isId,
                                            onRefresh = {
                                                if (!isEditMode) {
                                                    scope.launch { WeatherRepository.refreshIfStale(context, force = true) }
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    BuiltInWidgetType.VEHICLE -> {
                                        RioSenseVehicleWidgetCard(
                                            telemetry = telemetry,
                                            dueCount = dueCount,
                                            odoText = odoText,
                                            s = s,
                                            onOpenControlsTab = { tab ->
                                                if (!isEditMode) onOpenControlsTab(tab)
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    null -> Unit
                                }
                            }

                            WidgetItemKind.APP_WIDGET -> {
                                ExternalAppWidgetCard(
                                    appWidgetId = item.appWidgetId,
                                    fallbackTitle = item.providerComponent?.substringAfterLast('.') ?: "Widget",
                                    widthDp = boxWDp.value.roundToInt(),
                                    heightDp = boxHDp.value.roundToInt(),
                                    appWidgetController = appWidgetController,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        if (isEditMode) {
                            // Completely borderless touch capture layer for smooth iOS-style drag & resize
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(22.dp))
                                    .pointerInput(item.id) {
                                        detectTapGestures(
                                            onTap = { selectedItemId = latestItem.id }
                                        )
                                    }
                                    .pointerInput(item.id, gridCols, gridRows, stepX, stepY) {
                                        var sessionStartItem: PlacedWidgetItem? = null
                                        var sessionBaseItems: List<PlacedWidgetItem> = emptyList()
                                        var lastResolved: List<PlacedWidgetItem>? = null

                                        detectDragGestures(
                                            onDragStart = {
                                                val cur = latestItem
                                                sessionStartItem = cur
                                                sessionBaseItems = latestLiveItems
                                                lastResolved = latestLiveItems
                                                dragStartItem = cur
                                                selectedItemId = cur.id
                                                activeDragId = cur.id
                                                dragDeltaPx = Offset.Zero
                                                previewRect = GridCellRect(cur.col, cur.row, cur.colSpan, cur.rowSpan)
                                            },
                                            onDragCancel = {
                                                activeDragId = null
                                                dragStartItem = null
                                                dragDeltaPx = Offset.Zero
                                                previewRect = null
                                                livePageItems = latestPersistedItems
                                            },
                                            onDragEnd = {
                                                val finalLayout = lastResolved ?: latestLiveItems
                                                activeDragId = null
                                                dragStartItem = null
                                                dragDeltaPx = Offset.Zero
                                                previewRect = null
                                                livePageItems = finalLayout
                                                onCommitPageLayout(finalLayout)
                                            }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            dragDeltaPx += dragAmount
                                            val start = sessionStartItem ?: return@detectDragGestures
                                            val rawColFloat = start.col + dragDeltaPx.x / stepX
                                            val rawRowFloat = start.row + dragDeltaPx.y / stepY
                                            // Directional 0.38-cell nudge threshold makes neighbor swapping feel immediate
                                            val desiredCol = (if (dragDeltaPx.x >= 0f) {
                                                floor(rawColFloat + 0.62f).toInt()
                                            } else {
                                                ceil(rawColFloat - 0.62f).toInt()
                                            }).coerceIn(0, (gridCols - start.colSpan).coerceAtLeast(0))
                                            val desiredRow = (if (dragDeltaPx.y >= 0f) {
                                                floor(rawRowFloat + 0.62f).toInt()
                                            } else {
                                                ceil(rawRowFloat - 0.62f).toInt()
                                            }).coerceIn(0, (gridRows - start.rowSpan).coerceAtLeast(0))

                                            val resolved = resolveWidgetReflowWithNudge(
                                                pageItems = sessionBaseItems,
                                                movingId = start.id,
                                                desiredCol = desiredCol,
                                                desiredRow = desiredRow,
                                                targetColSpan = start.colSpan,
                                                targetRowSpan = start.rowSpan,
                                                dragDirX = dragDeltaPx.x,
                                                dragDirY = dragDeltaPx.y,
                                                gridCols = gridCols,
                                                gridRows = gridRows
                                            )
                                            if (resolved != null) {
                                                val (snappedPos, updatedLayout) = resolved
                                                lastResolved = updatedLayout
                                                livePageItems = updatedLayout
                                                previewRect = GridCellRect(
                                                    col = snappedPos.col,
                                                    row = snappedPos.row,
                                                    colSpan = start.colSpan,
                                                    rowSpan = start.rowSpan
                                                )
                                            }
                                        }
                                    }
                            )

                            // Iconic iPhone monochrome Obsidian `−` remove badge at Top-Left (no red, no border)
                            if (!isDraggingThis) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(x = (-4).dp, y = (-4).dp)
                                        .size(22.dp)
                                        .shadow(8.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                                        .clip(CircleShape)
                                        .background(Color(0xF0222733))
                                        .clickable {
                                            if (selectedItemId == item.id) selectedItemId = null
                                            onRemoveItem(item.id)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.size(9.dp)) {
                                        drawLine(
                                            color = ObsidianColors.PureSilver,
                                            start = Offset(0f, size.height * 0.5f),
                                            end = Offset(size.width, size.height * 0.5f),
                                            strokeWidth = 1.8.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }

                            // Subtle monochrome resize handles (corner always visible; 4 edge pills when selected)
                            if (!isDraggingThis) {
                                val handles = if (isSelected) {
                                    listOf(
                                        ResizeHandle.LEFT,
                                        ResizeHandle.RIGHT,
                                        ResizeHandle.TOP,
                                        ResizeHandle.BOTTOM,
                                        ResizeHandle.BOTTOM_RIGHT
                                    )
                                } else {
                                    listOf(ResizeHandle.BOTTOM_RIGHT)
                                }

                                handles.forEach { handle ->
                                    ResizeHandlePill(
                                        handle = handle,
                                        highlighted = isSelected,
                                        itemId = item.id,
                                        getCurrentItem = { latestItem },
                                        getLivePageItems = { latestLiveItems },
                                        getPersistedItems = { latestPersistedItems },
                                        minColSpan = minSpanW,
                                        minRowSpan = minSpanH,
                                        gridCols = gridCols,
                                        gridRows = gridRows,
                                        stepX = stepX,
                                        stepY = stepY,
                                        onStart = {
                                            selectedItemId = latestItem.id
                                            activeResizeId = latestItem.id
                                        },
                                        onLiveReflow = { rect, updatedLayout ->
                                            previewRect = rect
                                            if (updatedLayout != null) {
                                                livePageItems = updatedLayout
                                            }
                                        },
                                        onCommit = { finalLayout ->
                                            activeResizeId = null
                                            previewRect = null
                                            livePageItems = finalLayout
                                            onCommitPageLayout(finalLayout)
                                        },
                                        onCancel = { fallbackLayout ->
                                            activeResizeId = null
                                            previewRect = null
                                            livePageItems = fallbackLayout
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ResizeHandlePill(
    handle: ResizeHandle,
    highlighted: Boolean,
    itemId: String,
    getCurrentItem: () -> PlacedWidgetItem,
    getLivePageItems: () -> List<PlacedWidgetItem>,
    getPersistedItems: () -> List<PlacedWidgetItem>,
    minColSpan: Int,
    minRowSpan: Int,
    gridCols: Int,
    gridRows: Int,
    stepX: Float,
    stepY: Float,
    onStart: () -> Unit,
    onLiveReflow: (GridCellRect?, List<PlacedWidgetItem>?) -> Unit,
    onCommit: (List<PlacedWidgetItem>) -> Unit,
    onCancel: (List<PlacedWidgetItem>) -> Unit
) {
    val alignment = when (handle) {
        ResizeHandle.LEFT -> Alignment.CenterStart
        ResizeHandle.RIGHT -> Alignment.CenterEnd
        ResizeHandle.TOP -> Alignment.TopCenter
        ResizeHandle.BOTTOM -> Alignment.BottomCenter
        ResizeHandle.BOTTOM_RIGHT -> Alignment.BottomEnd
    }

    val touchSizeModifier = when (handle) {
        ResizeHandle.LEFT, ResizeHandle.RIGHT -> Modifier.size(width = 22.dp, height = 38.dp)
        ResizeHandle.TOP, ResizeHandle.BOTTOM -> Modifier.size(width = 38.dp, height = 22.dp)
        ResizeHandle.BOTTOM_RIGHT -> Modifier.size(28.dp)
    }

    val touchOffset = when (handle) {
        ResizeHandle.LEFT -> Modifier.offset(x = (-6).dp)
        ResizeHandle.RIGHT -> Modifier.offset(x = 6.dp)
        ResizeHandle.TOP -> Modifier.offset(y = (-6).dp)
        ResizeHandle.BOTTOM -> Modifier.offset(y = 6.dp)
        ResizeHandle.BOTTOM_RIGHT -> Modifier.offset(x = 3.dp, y = 3.dp)
    }

    val latestStart by rememberUpdatedState(onStart)
    val latestLiveReflow by rememberUpdatedState(onLiveReflow)
    val latestCommit by rememberUpdatedState(onCommit)
    val latestCancel by rememberUpdatedState(onCancel)

    Box(
        modifier = Modifier
            .align(alignment)
            .then(touchOffset)
            .then(touchSizeModifier)
            .pointerInput(itemId, handle, gridCols, gridRows, stepX, stepY) {
                var totalDelta = Offset.Zero
                var startItem: PlacedWidgetItem? = null
                var baseItems: List<PlacedWidgetItem> = emptyList()
                var lastResolvedLayout: List<PlacedWidgetItem>? = null

                detectDragGestures(
                    onDragStart = {
                        totalDelta = Offset.Zero
                        val cur = getCurrentItem()
                        startItem = cur
                        baseItems = getLivePageItems()
                        lastResolvedLayout = baseItems
                        latestStart()
                        latestLiveReflow(GridCellRect(cur.col, cur.row, cur.colSpan, cur.rowSpan), null)
                    },
                    onDragCancel = {
                        totalDelta = Offset.Zero
                        startItem = null
                        latestCancel(getPersistedItems())
                    },
                    onDragEnd = {
                        val finalLayout = lastResolvedLayout ?: getLivePageItems()
                        totalDelta = Offset.Zero
                        startItem = null
                        latestCommit(finalLayout)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    totalDelta += dragAmount
                    val cur = startItem ?: return@detectDragGestures
                    val dCols = (totalDelta.x / stepX).roundToInt()
                    val dRows = (totalDelta.y / stepY).roundToInt()

                    val next = when (handle) {
                        ResizeHandle.RIGHT -> GridCellRect(
                            col = cur.col,
                            row = cur.row,
                            colSpan = (cur.colSpan + dCols).coerceIn(minColSpan, (gridCols - cur.col).coerceAtLeast(minColSpan)),
                            rowSpan = cur.rowSpan
                        )

                        ResizeHandle.LEFT -> {
                            val rightEdge = cur.col + cur.colSpan
                            val newCol = (cur.col + dCols).coerceIn(0, (rightEdge - minColSpan).coerceAtLeast(0))
                            GridCellRect(
                                col = newCol,
                                row = cur.row,
                                colSpan = (rightEdge - newCol).coerceAtLeast(minColSpan),
                                rowSpan = cur.rowSpan
                            )
                        }

                        ResizeHandle.BOTTOM -> GridCellRect(
                            col = cur.col,
                            row = cur.row,
                            colSpan = cur.colSpan,
                            rowSpan = (cur.rowSpan + dRows).coerceIn(minRowSpan, (gridRows - cur.row).coerceAtLeast(minRowSpan))
                        )

                        ResizeHandle.TOP -> {
                            val bottomEdge = cur.row + cur.rowSpan
                            val newRow = (cur.row + dRows).coerceIn(0, (bottomEdge - minRowSpan).coerceAtLeast(0))
                            GridCellRect(
                                col = cur.col,
                                row = newRow,
                                colSpan = cur.colSpan,
                                rowSpan = (bottomEdge - newRow).coerceAtLeast(minRowSpan)
                            )
                        }

                        ResizeHandle.BOTTOM_RIGHT -> GridCellRect(
                            col = cur.col,
                            row = cur.row,
                            colSpan = (cur.colSpan + dCols).coerceIn(minColSpan, (gridCols - cur.col).coerceAtLeast(minColSpan)),
                            rowSpan = (cur.rowSpan + dRows).coerceIn(minRowSpan, (gridRows - cur.row).coerceAtLeast(minRowSpan))
                        )
                    }

                    val reflowed = resolveWidgetReflow(
                        pageItems = baseItems,
                        movingId = cur.id,
                        targetCol = next.col,
                        targetRow = next.row,
                        targetColSpan = next.colSpan,
                        targetRowSpan = next.rowSpan,
                        gridCols = gridCols,
                        gridRows = gridRows
                    )
                    if (reflowed != null) {
                        lastResolvedLayout = reflowed
                        latestLiveReflow(next, reflowed)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val pillColor = if (highlighted) {
            ObsidianColors.PureSilver.copy(alpha = 0.72f)
        } else {
            ObsidianColors.MutedSilver.copy(alpha = 0.55f)
        }
        when (handle) {
            ResizeHandle.LEFT, ResizeHandle.RIGHT -> {
                Box(
                    modifier = Modifier
                        .size(width = 3.5.dp, height = 20.dp)
                        .clip(CircleShape)
                        .background(pillColor)
                )
            }

            ResizeHandle.TOP, ResizeHandle.BOTTOM -> {
                Box(
                    modifier = Modifier
                        .size(width = 20.dp, height = 3.5.dp)
                        .clip(CircleShape)
                        .background(pillColor)
                )
            }

            ResizeHandle.BOTTOM_RIGHT -> {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .shadow(6.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(CircleShape)
                        .background(Color(0xEB222733)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(6.dp)) {
                        drawCircle(color = pillColor, radius = 2.dp.toPx())
                    }
                }
            }
        }
    }
}

@Composable
private fun ExternalAppWidgetCard(
    appWidgetId: Int,
    fallbackTitle: String,
    widthDp: Int,
    heightDp: Int,
    appWidgetController: RioSenseAppWidgetController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hostView = remember(appWidgetId) {
        appWidgetController.createHostView(context, appWidgetId)
    }
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .clip(shape)
            .background(Color(0xFF0E1117)),
        contentAlignment = Alignment.Center
    ) {
        if (hostView != null) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        (hostView.parent as? ViewGroup)?.removeView(hostView)
                        addView(
                            hostView,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                update = { frame ->
                    if (hostView.parent !== frame) {
                        (hostView.parent as? ViewGroup)?.removeView(hostView)
                        frame.removeAllViews()
                        frame.addView(
                            hostView,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                    appWidgetController.updateHostViewSize(hostView, widthDp, heightDp)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(14.dp)
            ) {
                Text(
                    text = fallbackTitle,
                    color = ObsidianColors.MutedSilver,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Widget Unavailable",
                    color = ObsidianColors.DimSilver,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Centered Spotlight-style Obsidian modal allowing users to add RioSense built-in widgets or
 * third-party Android app widgets to their customizable multi-page widget grid.
 */
@Composable
fun WidgetPickerOverlay(
    visible: Boolean,
    gridCols: Int,
    appWidgetController: RioSenseAppWidgetController,
    onAddBuiltInWidget: (BuiltInWidgetType) -> Unit,
    onSelectAppWidget: (AvailableAppWidget) -> Unit,
    onResetDefaultLayout: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isId = LocalAppLanguage.current == AppLanguage.ID
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = RioSense, 1 = Android Apps
    var query by remember { mutableStateOf("") }
    var availableAppWidgets by remember { mutableStateOf<List<AvailableAppWidget>>(emptyList()) }
    var isLoadingAppWidgets by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(visible, gridCols) {
        if (visible) {
            query = ""
            isLoadingAppWidgets = true
            availableAppWidgets = withContext(Dispatchers.IO) {
                appWidgetController.loadInstalledWidgets(gridCols)
            }
            isLoadingAppWidgets = false
        }
    }

    LaunchedEffect(visible, selectedTab) {
        if (visible && selectedTab == 1) {
            delay(80L)
            runCatching { focusRequester.requestFocus() }
        }
    }

    val filteredAppWidgets = remember(availableAppWidgets, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            availableAppWidgets
        } else {
            availableAppWidgets.filter {
                it.appLabel.lowercase().contains(q) ||
                    it.widgetLabel.lowercase().contains(q)
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.94f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            val modalShape = RoundedCornerShape(22.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .widthIn(max = 560.dp)
                    .heightIn(max = 420.dp)
                    .shadow(
                        elevation = 28.dp,
                        shape = modalShape,
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(modalShape)
                    .background(Color(0xF711141C))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isId) "Tambah Widget" else "Add Widget",
                        color = ObsidianColors.PureSilver,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeaderActionPill(
                            label = "RioSense",
                            bgColor = if (selectedTab == 0) Color(0xFF252B38) else Color(0xFF191D26),
                            textColor = if (selectedTab == 0) ObsidianColors.PureSilver else ObsidianColors.MutedSilver,
                            onClick = { selectedTab = 0 }
                        )
                        HeaderActionPill(
                            label = if (isId) "Widget Aplikasi" else "App Widgets",
                            bgColor = if (selectedTab == 1) Color(0xFF252B38) else Color(0xFF191D26),
                            textColor = if (selectedTab == 1) ObsidianColors.PureSilver else ObsidianColors.MutedSilver,
                            onClick = { selectedTab = 1 }
                        )
                    }
                }

                if (selectedTab == 0) {
                    val builtInItems = remember(gridCols) {
                        val rightCols = (gridCols - 2).coerceAtLeast(2)
                        listOf(
                            Triple(
                                BuiltInWidgetType.CLOCK,
                                if (isId) "Jam & Tanggal Dot-Matrix" else "Dot-Matrix Clock & Date",
                                "2×2"
                            ),
                            Triple(
                                BuiltInWidgetType.MUSIC,
                                if (isId) "Pemutar Musik & Spektrum" else "Music Player & Spectrum",
                                "2×2"
                            ),
                            Triple(
                                BuiltInWidgetType.WEATHER,
                                if (isId) "Cuaca & Prakiraan 5 Hari" else "Weather & 5-Day Forecast",
                                "${rightCols}×2"
                            ),
                            Triple(
                                BuiltInWidgetType.VEHICLE,
                                if (isId) "Info Kendaraan & Shortcut" else "Vehicle Info & Shortcuts",
                                "${rightCols}×2"
                            )
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(builtInItems, key = { it.first.name }) { (type, title, spanLabel) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF161A23))
                                    .clickable { onAddBuiltInWidget(type) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = title,
                                        color = ObsidianColors.PureSilver,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "RioSense Obsidian · $spanLabel",
                                        color = ObsidianColors.DimSilver,
                                        fontSize = 10.5.sp
                                    )
                                }
                                Text(
                                    text = "+",
                                    color = ObsidianColors.PureSilver,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        HeaderActionPill(
                            label = if (isId) "Reset Tata Letak Default" else "Reset Default Layout",
                            bgColor = Color(0xFF1A1E28),
                            textColor = ObsidianColors.MutedSilver,
                            onClick = onResetDefaultLayout
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF161A23))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = if (isId) "Cari widget aplikasi (Spotify, Maps, Kalender...)" else "Search app widgets...",
                                color = ObsidianColors.DimSilver,
                                fontSize = 12.sp
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            cursorBrush = SolidColor(ObsidianColors.PureSilver),
                            textStyle = TextStyle(
                                color = ObsidianColors.PureSilver,
                                fontSize = 12.sp
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {}),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }

                    when {
                        isLoadingAppWidgets -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isId) "Memuat daftar widget aplikasi..." else "Loading app widgets...",
                                    color = ObsidianColors.MutedSilver,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        filteredAppWidgets.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isId) "Tidak ada widget aplikasi yang cocok" else "No matching app widgets found",
                                    color = ObsidianColors.DimSilver,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    filteredAppWidgets,
                                    key = { it.providerInfo.provider.flattenToString() }
                                ) { widget ->
                                    val iconBitmap by produceState<ImageBitmap?>(
                                        initialValue = null,
                                        widget.appPackage
                                    ) {
                                        value = withContext(Dispatchers.IO) {
                                            runCatching {
                                                context.packageManager
                                                    .getApplicationIcon(widget.appPackage)
                                                    .toBitmap(width = 72, height = 72)
                                                    .asImageBitmap()
                                            }.getOrNull()
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFF161A23))
                                            .clickable { onSelectAppWidget(widget) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(Color(0xFF202633)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val bmp = iconBitmap
                                            if (bmp != null) {
                                                Image(
                                                    bitmap = bmp,
                                                    contentDescription = widget.appLabel,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = widget.widgetLabel,
                                                color = ObsidianColors.PureSilver,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${widget.appLabel} · ${widget.defaultColSpan}×${widget.defaultRowSpan}",
                                                color = ObsidianColors.DimSilver,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Text(
                                            text = "+",
                                            color = ObsidianColors.PureSilver,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderActionPill(
    label: String,
    bgColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun HeaderPlusIconButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(15.dp)) {
            val stroke = 1.7.dp.toPx()
            val c = ObsidianColors.PureSilver
            drawLine(
                color = c,
                start = Offset(size.width * 0.5f, size.height * 0.14f),
                end = Offset(size.width * 0.5f, size.height * 0.86f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = c,
                start = Offset(size.width * 0.14f, size.height * 0.5f),
                end = Offset(size.width * 0.86f, size.height * 0.5f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun HeaderEditGridIconButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(15.dp)) {
            val cell = size.width * 0.36f
            val r = CornerRadius(2.dp.toPx())
            val stroke = 1.4.dp.toPx()
            val c = ObsidianColors.MutedSilver
            val positions = listOf(
                Offset(size.width * 0.08f, size.height * 0.08f),
                Offset(size.width * 0.56f, size.height * 0.08f),
                Offset(size.width * 0.08f, size.height * 0.56f),
                Offset(size.width * 0.56f, size.height * 0.56f)
            )
            positions.forEach { pos ->
                drawRoundRect(
                    color = c,
                    topLeft = pos,
                    size = Size(cell, cell),
                    cornerRadius = r,
                    style = Stroke(width = stroke)
                )
            }
        }
    }
}
