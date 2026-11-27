package id.bangsadigital.riosense.ui.components

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import id.bangsadigital.riosense.model.KiaRioPaintColor
import id.bangsadigital.riosense.model.LightState
import id.bangsadigital.riosense.model.TireInfo
import id.bangsadigital.riosense.model.VehicleTelemetry
import id.bangsadigital.riosense.ui.i18n.appStrings
import id.bangsadigital.riosense.ui.theme.ObsidianColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val TURNTABLE_COUNT = 180
private const val TURNTABLE_CACHE_MAX_FRAMES = 36
private const val DOOR_SEQ_MAX_INDEX = 19
private const val DOOR_CACHE_MAX_FRAMES = 24
private const val REAR_RIGHT_DOOR_FRAME = 0f
private const val REAR_LEFT_DOOR_FRAME = 146f

enum class RoadMotionDirection {
    FRONT,
    BACK
}

private enum class CarViewMode(val label: String, val targetFrame: Float?) {
    AUTO_ROTATE("360° Auto", null),
    FRONT("Front", 62f),
    FRONT_45("45° Front", 72f),
    BACK("Back", 164f),
    BACK_45("45° Back", 176f)
}

@Composable
fun KiaRioInteractiveStage(
    telemetry: VehicleTelemetry,
    onToggleDoor: (String) -> Unit,
    onDragStateChanged: (Boolean) -> Unit = {},
    onRoadMotionDirectionChange: (RoadMotionDirection?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // All turntable_360 and door assets use Transparent PNG (ARGB_8888 to preserve full alpha)
    val decodeOpts = remember {
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    }

    // 1. Thread-Safe LRU Frame Cache for 180 Transparent PNGs (2 deg/frame: turntable_000.png .. turntable_179.png)
    //    Capped at 36 frames (~32 MB max active) with mode-aware prefetching and auto-evict when backgrounded.
    val turntableCache = remember {
        object : LruCache<Int, ImageBitmap>(TURNTABLE_CACHE_MAX_FRAMES) {}
    }

    fun getOrLoadTurntableFrame(index: Int): ImageBitmap? {
        val normIdx = ((index % TURNTABLE_COUNT) + TURNTABLE_COUNT) % TURNTABLE_COUNT
        synchronized(turntableCache) {
            turntableCache.get(normIdx)?.let { return it }
        }
        val name = String.format("car_sequences/turntable_360/turntable_%03d.png", normIdx)
        val loaded = runCatching {
            context.assets.open(name).use {
                BitmapFactory.decodeStream(it, null, decodeOpts)?.asImageBitmap()
            }
        }.getOrNull()
        if (loaded != null) {
            synchronized(turntableCache) {
                turntableCache.put(normIdx, loaded)
            }
        }
        return loaded
    }

    var isUserDraggingTurntable by remember { mutableStateOf(false) }
    var selectedViewMode by remember { mutableStateOf<CarViewMode?>(CarViewMode.FRONT_45) }
    var isStageForeground by remember { mutableStateOf(true) }

    // 360° orbit state (0f .. 180f, each 1.0f = 2° smooth step), defaulting to 45° Front View (frame 72)
    val turntableAnim = remember { Animatable(CarViewMode.FRONT_45.targetFrame ?: 72f) }
    val activeTurntableIdx = ((turntableAnim.value.roundToInt() % TURNTABLE_COUNT) + TURNTABLE_COUNT) % TURNTABLE_COUNT

    // Mode-aware background prefetcher:
    // - Static preset: small ±5 window + 4 tab anchors (~13 MB vs ~64 MB)
    // - 360° Auto: forward-only 18-frame lookahead (~1.1s ahead at 16 fps)
    // - Manual drag: bidirectional ±14 window
    LaunchedEffect(activeTurntableIdx, selectedViewMode, isUserDraggingTurntable, isStageForeground) {
        if (!isStageForeground) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            when {
                selectedViewMode == CarViewMode.AUTO_ROTATE -> {
                    for (offset in 0..18) {
                        getOrLoadTurntableFrame(activeTurntableIdx + offset)
                    }
                }
                isUserDraggingTurntable -> {
                    for (offset in 0..14) {
                        getOrLoadTurntableFrame(activeTurntableIdx + offset)
                        if (offset > 0) {
                            getOrLoadTurntableFrame(activeTurntableIdx - offset)
                        }
                    }
                }
                else -> {
                    for (offset in 0..5) {
                        getOrLoadTurntableFrame(activeTurntableIdx + offset)
                        if (offset > 0) {
                            getOrLoadTurntableFrame(activeTurntableIdx - offset)
                        }
                    }
                    CarViewMode.entries.forEach { preset ->
                        preset.targetFrame?.let { getOrLoadTurntableFrame(it.roundToInt()) }
                    }
                }
            }
        }
    }

    // 2. Lazy LRU Cache for 7 Door & Tailgate Swing Sequences (max 24 frames in RAM, auto-evicted once doors close)
    val doorCache = remember {
        object : LruCache<String, ImageBitmap>(DOOR_CACHE_MAX_FRAMES) {}
    }

    fun getOrLoadDoorFrame(prefix: String, step: Int): ImageBitmap? {
        val clampedStep = step.coerceIn(0, DOOR_SEQ_MAX_INDEX)
        val key = "${prefix}_$clampedStep"
        synchronized(doorCache) {
            doorCache.get(key)?.let { return it }
        }
        val name = String.format("car_sequences/doors/%s_%02d.png", prefix, clampedStep)
        val loaded = runCatching {
            context.assets.open(name).use {
                BitmapFactory.decodeStream(it, null, decodeOpts)?.asImageBitmap()
            }
        }.getOrNull()
        if (loaded != null) {
            synchronized(doorCache) {
                doorCache.put(key, loaded)
            }
        }
        return loaded
    }

    // Evict cached car bitmaps on ON_STOP, memory trim, or disposal so Maps/Spotify get full Head Unit RAM
    DisposableEffect(lifecycleOwner, context) {
        val appContext = context.applicationContext
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    isStageForeground = true
                }
                Lifecycle.Event.ON_STOP -> {
                    isStageForeground = false
                    synchronized(turntableCache) { turntableCache.evictAll() }
                    synchronized(doorCache) { doorCache.evictAll() }
                }
                else -> Unit
            }
        }
        val trimCallbacks = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                    synchronized(turntableCache) { turntableCache.evictAll() }
                    synchronized(doorCache) { doorCache.evictAll() }
                } else if (
                    level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                    level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
                ) {
                    synchronized(turntableCache) { turntableCache.trimToSize(4) }
                    synchronized(doorCache) { doorCache.trimToSize(4) }
                }
            }

            override fun onLowMemory() {
                synchronized(turntableCache) { turntableCache.trimToSize(1) }
                synchronized(doorCache) { doorCache.evictAll() }
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        appContext.registerComponentCallbacks(trimCallbacks)

        onDispose {
            onDragStateChanged(false)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            appContext.unregisterComponentCallbacks(trimCallbacks)
            synchronized(turntableCache) { turntableCache.evictAll() }
            synchronized(doorCache) { doorCache.evictAll() }
        }
    }

    suspend fun animateTurntableTo(targetFrame: Float) {
        val currentMod = ((turntableAnim.value % TURNTABLE_COUNT) + TURNTABLE_COUNT) % TURNTABLE_COUNT
        var delta = targetFrame - currentMod
        if (delta > TURNTABLE_COUNT / 2f) delta -= TURNTABLE_COUNT
        if (delta < -TURNTABLE_COUNT / 2f) delta += TURNTABLE_COUNT
        turntableAnim.snapTo(currentMod)
        turntableAnim.animateTo(
            targetValue = currentMod + delta,
            animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing)
        )
        turntableAnim.snapTo(targetFrame)
    }

    val d = telemetry.doors
    val rightOpenNow = d.frontRightOpen || d.rearRightOpen
    val leftOpenNow = d.frontLeftOpen || d.rearLeftOpen
    val trunkOpenNow = d.trunkOpen

    // Auto Rotate 360° Mode: advance smoothly on display VSync (~16 frames/sec),
    // pausing automatically while any door or tailgate is open or while stage is backgrounded
    val anyDoorOpen = rightOpenNow || leftOpenNow || trunkOpenNow
    LaunchedEffect(selectedViewMode, anyDoorOpen, isStageForeground) {
        if (selectedViewMode == CarViewMode.AUTO_ROTATE && !anyDoorOpen && isStageForeground) {
            var lastNanos = withFrameNanos { it }
            while (selectedViewMode == CarViewMode.AUTO_ROTATE && !anyDoorOpen && isStageForeground) {
                val now = withFrameNanos { it }
                val dtSec = ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
                lastNanos = now
                turntableAnim.snapTo((turntableAnim.value + dtSec * 16f) % TURNTABLE_COUNT)
            }
        }
    }

    // Right, Left, and Tailgate swing animation progress (0f = closed, 19f = fully open)
    val rightSwingAnim = remember { Animatable(if (rightOpenNow) 19f else 0f) }
    val leftSwingAnim = remember { Animatable(if (leftOpenNow) 19f else 0f) }
    val trunkSwingAnim = remember { Animatable(if (trunkOpenNow) 19f else 0f) }

    var activeRightKey by remember { mutableStateOf("rr") }
    var activeLeftKey by remember { mutableStateOf("rl") }

    if (d.frontRightOpen && d.rearRightOpen) activeRightKey = "fr_rr"
    else if (d.frontRightOpen) activeRightKey = "fr"
    else if (d.rearRightOpen) activeRightKey = "rr"

    if (d.frontLeftOpen && d.rearLeftOpen) activeLeftKey = "fl_rl"
    else if (d.frontLeftOpen) activeLeftKey = "fl"
    else if (d.rearLeftOpen) activeLeftKey = "rl"

    // Run smooth open (0 -> 19) and close (19 -> 0) swing animations with async preloading
    LaunchedEffect(d.frontRightOpen, d.rearRightOpen, d.frontLeftOpen, d.rearLeftOpen, d.trunkOpen) {
        withContext(Dispatchers.IO) {
            if (rightOpenNow || rightSwingAnim.value > 0.05f) {
                for (s in 0..DOOR_SEQ_MAX_INDEX) getOrLoadDoorFrame(activeRightKey, s)
            }
            if (leftOpenNow || leftSwingAnim.value > 0.05f) {
                for (s in 0..DOOR_SEQ_MAX_INDEX) getOrLoadDoorFrame(activeLeftKey, s)
            }
            if (trunkOpenNow || trunkSwingAnim.value > 0.05f) {
                for (s in 0..DOOR_SEQ_MAX_INDEX) getOrLoadDoorFrame("trunk", s)
            }
        }

        if (rightOpenNow || leftOpenNow || trunkOpenNow) {
            isUserDraggingTurntable = false
            // Match the turntable angle to the door sequence orientation before swinging open,
            // keeping the bottom view-selector indicator strictly manual (unchanged):
            // FL/RL sequences face rear-left (headlamp on left -> frame 146), while FR/RR/Trunk face rear-right (frame 0).
            val targetDoorFrame = if (leftOpenNow && !rightOpenNow && !trunkOpenNow) {
                REAR_LEFT_DOOR_FRAME
            } else {
                REAR_RIGHT_DOOR_FRAME
            }
            val currentMod = ((turntableAnim.value % TURNTABLE_COUNT) + TURNTABLE_COUNT) % TURNTABLE_COUNT
            var delta = targetDoorFrame - currentMod
            if (delta > TURNTABLE_COUNT / 2f) delta -= TURNTABLE_COUNT
            if (delta < -TURNTABLE_COUNT / 2f) delta += TURNTABLE_COUNT
            if (kotlin.math.abs(delta) > 1.0f) {
                turntableAnim.snapTo(currentMod)
                turntableAnim.animateTo(
                    targetValue = currentMod + delta,
                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                )
                turntableAnim.snapTo(targetDoorFrame)
            }
        }

        coroutineScope {
            launch {
                rightSwingAnim.animateTo(
                    targetValue = if (rightOpenNow) DOOR_SEQ_MAX_INDEX.toFloat() else 0f,
                    animationSpec = tween(durationMillis = 560, easing = LinearEasing)
                )
            }
            launch {
                leftSwingAnim.animateTo(
                    targetValue = if (leftOpenNow) DOOR_SEQ_MAX_INDEX.toFloat() else 0f,
                    animationSpec = tween(durationMillis = 560, easing = LinearEasing)
                )
            }
            launch {
                trunkSwingAnim.animateTo(
                    targetValue = if (trunkOpenNow) DOOR_SEQ_MAX_INDEX.toFloat() else 0f,
                    animationSpec = tween(durationMillis = 560, easing = LinearEasing)
                )
            }
        }

        if (!rightOpenNow && !leftOpenNow && !trunkOpenNow) {
            synchronized(doorCache) { doorCache.evictAll() }
            if (!isUserDraggingTurntable && selectedViewMode != CarViewMode.AUTO_ROTATE) {
                // Once all doors are closed, smoothly return to the manually selected view (or default 45° Front)
                // without changing the bottom view-selector indicator
                val returnFrame = selectedViewMode?.targetFrame ?: (CarViewMode.FRONT_45.targetFrame ?: 72f)
                animateTurntableTo(returnFrame)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "alertPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val paintColorFilter = remember(telemetry.paintColor) {
        createCarPaintColorFilter(telemetry.paintColor)
    }

    val lights = telemetry.lights
    val anyTurnActive = lights.leftTurn || lights.rightTurn || lights.hazard
    var turnFlashOn by remember { mutableStateOf(false) }
    LaunchedEffect(anyTurnActive) {
        if (!anyTurnActive) {
            turnFlashOn = false
        } else {
            while (true) {
                turnFlashOn = true
                delay(420)
                turnFlashOn = false
                delay(380)
            }
        }
    }
    val turnFlashAlpha by animateFloatAsState(
        targetValue = if (turnFlashOn) 1f else 0f,
        animationSpec = tween(durationMillis = 85),
        label = "turnFlashAlpha"
    )

    val showRightDoorAnim = rightSwingAnim.value > 0.05f && !isUserDraggingTurntable
    val showLeftDoorAnim = leftSwingAnim.value > 0.05f && !isUserDraggingTurntable
    val showTrunkAnim = trunkSwingAnim.value > 0.05f && !isUserDraggingTurntable

    val activePanelsCount = (if (showLeftDoorAnim) 1 else 0) +
        (if (showRightDoorAnim) 1 else 0) +
        (if (showTrunkAnim) 1 else 0)

    val activeRoadDirection = if (telemetry.roadEffectEnabled && activePanelsCount == 0) {
        when (selectedViewMode) {
            CarViewMode.FRONT -> RoadMotionDirection.FRONT
            CarViewMode.BACK -> RoadMotionDirection.BACK
            else -> null
        }
    } else {
        null
    }
    LaunchedEffect(activeRoadDirection) {
        onRoadMotionDirectionChange(activeRoadDirection)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(rightOpenNow, leftOpenNow, trunkOpenNow) {
                    detectDragGestures(
                        onDragStart = {
                            onDragStateChanged(true)
                            isUserDraggingTurntable = true
                            selectedViewMode = null
                        },
                        onDragEnd = {
                            onDragStateChanged(false)
                            if (rightOpenNow || leftOpenNow || trunkOpenNow) {
                                isUserDraggingTurntable = false
                            }
                        },
                        onDragCancel = {
                            onDragStateChanged(false)
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            // 180 frames (2 deg/frame): ~514px drag spans a full 360° orbit
                            turntableAnim.snapTo(turntableAnim.value + dragAmount.x * 0.35f)
                        }
                    }
                }
                .pointerInput(rightOpenNow, leftOpenNow, trunkOpenNow) {
                    detectTapGestures(
                        onDoubleTap = {
                            val defaultFrame = CarViewMode.FRONT_45.targetFrame ?: 72f
                            isUserDraggingTurntable = rightOpenNow || leftOpenNow || trunkOpenNow
                            selectedViewMode = CarViewMode.FRONT_45
                            scope.launch {
                                animateTurntableTo(defaultFrame)
                            }
                        }
                    )
                }
        ) {
        when {
            // CASE 1: Multiple Panels Open Simultaneously (Left + Right doors or Doors + Tailgate)
            activePanelsCount >= 2 -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 18.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showLeftDoorAnim) {
                        val leftIdx = leftSwingAnim.value.roundToInt().coerceIn(0, DOOR_SEQ_MAX_INDEX)
                        val leftBmp = getOrLoadDoorFrame(activeLeftKey, leftIdx)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (leftBmp != null) {
                                Image(
                                    bitmap = leftBmp,
                                    contentDescription = "Left Door Swing",
                                    contentScale = ContentScale.Fit,
                                    colorFilter = paintColorFilter,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = when {
                                    d.frontLeftOpen && d.rearLeftOpen -> "LEFT: FL + RL OPEN"
                                    d.frontLeftOpen -> "LEFT: FL OPEN"
                                    d.rearLeftOpen -> "LEFT: RL OPEN"
                                    else -> "LEFT: CLOSING"
                                },
                                color = ObsidianColors.CrimsonAlert,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (showRightDoorAnim) {
                        if (showLeftDoorAnim) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight(0.62f)
                                    .background(Color(0xFF1E222B))
                            )
                        }
                        val rightIdx = rightSwingAnim.value.roundToInt().coerceIn(0, DOOR_SEQ_MAX_INDEX)
                        val rightBmp = getOrLoadDoorFrame(activeRightKey, rightIdx)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (rightBmp != null) {
                                Image(
                                    bitmap = rightBmp,
                                    contentDescription = "Right Door Swing",
                                    contentScale = ContentScale.Fit,
                                    colorFilter = paintColorFilter,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = when {
                                    d.frontRightOpen && d.rearRightOpen -> "RIGHT: FR + RR OPEN"
                                    d.frontRightOpen -> "RIGHT: FR OPEN"
                                    d.rearRightOpen -> "RIGHT: RR OPEN"
                                    else -> "RIGHT: CLOSING"
                                },
                                color = ObsidianColors.CrimsonAlert,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (showTrunkAnim) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight(0.62f)
                                .background(Color(0xFF1E222B))
                        )
                        val trunkIdx = trunkSwingAnim.value.roundToInt().coerceIn(0, DOOR_SEQ_MAX_INDEX)
                        val trunkBmp = getOrLoadDoorFrame("trunk", trunkIdx)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (trunkBmp != null) {
                                Image(
                                    bitmap = trunkBmp,
                                    contentDescription = "Tailgate Swing",
                                    contentScale = ContentScale.Fit,
                                    colorFilter = paintColorFilter,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = if (d.trunkOpen) "TAILGATE OPEN" else "TAILGATE CLOSING",
                                color = ObsidianColors.CrimsonAlert,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // CASE 2: Right Side Swing Animation Only (FR, RR, or FR+RR)
            showRightDoorAnim -> {
                val rightIdx = rightSwingAnim.value.roundToInt().coerceIn(0, DOOR_SEQ_MAX_INDEX)
                val rightBmp = getOrLoadDoorFrame(activeRightKey, rightIdx)
                if (rightBmp != null) {
                    Image(
                        bitmap = rightBmp,
                        contentDescription = "Right Door Animation",
                        contentScale = ContentScale.Fit,
                        colorFilter = paintColorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 6.dp)
                    )
                }
            }

            // CASE 3: Left Side Swing Animation Only (FL, RL, or FL+RL)
            showLeftDoorAnim -> {
                val leftIdx = leftSwingAnim.value.roundToInt().coerceIn(0, DOOR_SEQ_MAX_INDEX)
                val leftBmp = getOrLoadDoorFrame(activeLeftKey, leftIdx)
                if (leftBmp != null) {
                    Image(
                        bitmap = leftBmp,
                        contentDescription = "Left Door Animation",
                        contentScale = ContentScale.Fit,
                        colorFilter = paintColorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 6.dp)
                    )
                }
            }

            // CASE 4: Rear Tailgate Swing Animation Only
            showTrunkAnim -> {
                val trunkIdx = trunkSwingAnim.value.roundToInt().coerceIn(0, DOOR_SEQ_MAX_INDEX)
                val trunkBmp = getOrLoadDoorFrame("trunk", trunkIdx)
                if (trunkBmp != null) {
                    Image(
                        bitmap = trunkBmp,
                        contentDescription = "Tailgate Animation",
                        contentScale = ContentScale.Fit,
                        colorFilter = paintColorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 6.dp)
                    )
                }
            }

            // CASE 5: 360° Turntable Mode — Transparent PNG 2°/Frame (180 frames: 000..179)
            else -> {
                val frameIdx = ((turntableAnim.value.roundToInt() % TURNTABLE_COUNT) + TURNTABLE_COUNT) % TURNTABLE_COUNT
                val bmp = getOrLoadTurntableFrame(frameIdx)
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Kia Rio 360 Turntable",
                        contentScale = ContentScale.Fit,
                        colorFilter = paintColorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 6.dp)
                    )
                }

                // Option A: Precision exterior light overlay on Front (59..65) and Back (161..167) views
                val isFrontView = frameIdx in 59..65
                val isBackView = frameIdx in 161..167
                if (isFrontView || isBackView) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 6.dp)
                    ) {
                        drawExteriorLightsOverlay(
                            isFrontView = isFrontView,
                            lights = lights,
                            turnFlashAlpha = turnFlashAlpha
                        )
                    }
                }
            }
        }

        // BORDERLESS MINIMALIST TPMS READOUTS (AUTO-HIDE WHEN NO TPMS SIGNAL) & SLEEK DOOR MICRO-TRIGGERS
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (telemetry.tpmsSignalAvailable) {
                    TpmsMinimalReadout(
                        tire = telemetry.tpms.frontLeft,
                        telemetry = telemetry,
                        blinkAlpha = pulseAlpha,
                        alignRight = false,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }

                Text(
                    text = "Double-tap Reset",
                    color = Color(0xFF3E4452),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 2.dp)
                )

                if (telemetry.tpmsSignalAvailable) {
                    TpmsMinimalReadout(
                        tire = telemetry.tpms.frontRight,
                        telemetry = telemetry,
                        blinkAlpha = pulseAlpha,
                        alignRight = true,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DoorMicroControl(
                        label = "FL",
                        isOpen = d.frontLeftOpen,
                        onClick = { onToggleDoor("FL") }
                    )
                    DoorMicroControl(
                        label = "RL",
                        isOpen = d.rearLeftOpen,
                        onClick = { onToggleDoor("RL") }
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DoorMicroControl(
                        label = "FR",
                        isOpen = d.frontRightOpen,
                        onClick = { onToggleDoor("FR") }
                    )
                    DoorMicroControl(
                        label = "RR",
                        isOpen = d.rearRightOpen,
                        onClick = { onToggleDoor("RR") }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                if (telemetry.tpmsSignalAvailable) {
                    TpmsMinimalReadout(
                        tire = telemetry.tpms.rearLeft,
                        telemetry = telemetry,
                        blinkAlpha = pulseAlpha,
                        alignRight = false,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )
                }

                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    DoorMicroControl(
                        label = "Trunk",
                        isOpen = d.trunkOpen,
                        onClick = { onToggleDoor("TRUNK") }
                    )
                }

                if (telemetry.tpmsSignalAvailable) {
                    TpmsMinimalReadout(
                        tire = telemetry.tpms.rearRight,
                        telemetry = telemetry,
                        blinkAlpha = pulseAlpha,
                        alignRight = true,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
        }

        Spacer(modifier = Modifier.height(6.dp))

        CarViewSelectionBar(
            selectedMode = selectedViewMode,
            onSelectMode = { mode ->
                selectedViewMode = mode
                val target = mode.targetFrame
                if (target == null) {
                    isUserDraggingTurntable = rightOpenNow || leftOpenNow || trunkOpenNow
                } else {
                    isUserDraggingTurntable = (mode != CarViewMode.BACK_45) && (rightOpenNow || leftOpenNow || trunkOpenNow)
                    scope.launch {
                        animateTurntableTo(target)
                    }
                }
            }
        )
    }
}

/**
 * Lightweight full-height perspective neon road drawn behind the Home stage (pure Canvas).
 * - Shown only for the Front and Back tabs, extending down to the bottom edge of the screen.
 * - Marking flow speed follows [speedKmh]; at 0 km/h the road is static and the frame loop stops (0% CPU).
 */
@Composable
fun RoadMotionBackdrop(
    direction: RoadMotionDirection?,
    speedKmh: Int,
    modifier: Modifier = Modifier
) {
    val active = direction != null
    var lastDirection by remember { mutableStateOf(RoadMotionDirection.FRONT) }
    if (direction != null) lastDirection = direction

    val fade by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "roadFade"
    )
    val speedState = rememberUpdatedState(speedKmh)
    var phase by remember { mutableFloatStateOf(0f) }
    val moving = speedKmh > 0

    // Frame loop runs ONLY while the road is visible and the car is moving
    LaunchedEffect(active, moving, lastDirection) {
        if (!active || !moving) return@LaunchedEffect
        // Ground flows toward the viewer when seen from behind, away from the viewer when seen from the front
        val dir = if (lastDirection == RoadMotionDirection.BACK) 1f else -1f
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            val kmh = speedState.value.coerceAtLeast(1)
            val cyclesPerSec = 0.12f + (kmh / 120f).coerceIn(0f, 1.5f) * 1.5f
            var next = phase + dir * cyclesPerSec * dt
            next -= kotlin.math.floor(next)
            phase = next
        }
    }

    if (fade < 0.01f) return

    Canvas(modifier = modifier.clipToBounds()) {
        val w = size.width
        val h = size.height
        val horizonY = h * HOME_SHOWROOM_HORIZON_Y_FRAC
        // Vanishing point behind the car and near edge ending flush on the showroom horizon line
        val v = Offset(0.50f * w, 0.20f * h)
        val b = Offset(0.50f * w, horizonY)
        fun pt(s: Float) = Offset(v.x + (b.x - v.x) * s, v.y + (b.y - v.y) * s)

        val halfW = w * 0.28f
        val cobalt = ObsidianColors.CobaltAccent
        val ph = phase
        val k = (speedState.value / 120f).coerceIn(0f, 1f)

        clipRect(bottom = horizonY) {
            // Neon road edges (20 slices, brighter & thicker down to the showroom horizon line)
            val slices = 20
            for (side in intArrayOf(-1, 1)) {
                for (i in 0 until slices) {
                    val s0 = i / slices.toFloat()
                    val s1 = (i + 1) / slices.toFloat()
                    val a = (s1 * 0.9f) * fade
                    val p0 = pt(s0) + Offset(side * halfW * s0, 0f)
                    val p1 = pt(s1) + Offset(side * halfW * s1, 0f)
                    drawLine(cobalt.copy(alpha = a * 0.22f), p0, p1, strokeWidth = (4f + 16f * s1).dp.toPx() * 0.7f)
                    drawLine(cobalt.copy(alpha = a), p0, p1, strokeWidth = (2f + 6.5f * s1).dp.toPx())
                }
            }

            // Tapered road-marking (trapezoid): narrow far away, wide near the horizon line (real perspective)
            fun dash(offsetFrac: Float, u: Float, len: Float, color: Color, baseHalfWidth: Float, alphaMul: Float) {
                val u0 = u - kotlin.math.floor(u)
                val u1 = (u0 + len).coerceAtMost(1f)
                val s0 = u0 * u0
                val s1 = u1 * u1
                val a = (0.3f + 0.7f * s1) * alphaMul * fade
                val c0 = pt(s0) + Offset(offsetFrac * halfW * s0, 0f)
                val c1 = pt(s1) + Offset(offsetFrac * halfW * s1, 0f)
                val hw0 = baseHalfWidth * s0
                val hw1 = baseHalfWidth * s1
                val quad = Path().apply {
                    moveTo(c0.x - hw0, c0.y)
                    lineTo(c0.x + hw0, c0.y)
                    lineTo(c1.x + hw1, c1.y)
                    lineTo(c1.x - hw1, c1.y)
                    close()
                }
                drawPath(quad, color.copy(alpha = a.coerceIn(0f, 1f)))
            }

            // Lane dividers (thick tapered dashes) flowing smoothly to the showroom horizon line
            val dashCount = 5
            for (i in 0 until dashCount) {
                val u = (i + ph * 1f) / dashCount
                dash(-0.5f, u, 0.16f, Color.White, w * 0.017f, 0.87f)
                dash(0.5f, u, 0.16f, Color.White, w * 0.017f, 0.87f)
            }

            // Central lane marking in the middle of the road following vehicle speed
            val centerCount = 9
            for (i in 0 until centerCount) {
                val u = (i + ph * (0.8f + 0.9f * k)) / centerCount
                dash(0f, u, 0.20f, Color.White, w * 0.014f, 0.9f)
            }

            // Speed streaks on the outer sides: only while moving, longer & brighter as speed rises
            if (k > 0f) {
                for (i in 0 until 5) {
                    val u = (i * 0.2f) + ph * 1.35f + i * 0.07f
                    val len = 0.10f + 0.22f * k
                    dash(-1.35f, u, len, cobalt, w * 0.011f, 0.25f + 0.65f * k)
                    dash(1.35f, u + 0.11f, len, cobalt, w * 0.011f, 0.25f + 0.65f * k)
                }
            }
        }
    }
}

@Composable
private fun CarViewSelectionBar(
    selectedMode: CarViewMode?,
    onSelectMode: (CarViewMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xB80E1016))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CarViewMode.values().forEach { mode ->
            val isSelected = selectedMode == mode
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isSelected) Color(0xCC212632) else Color.Transparent)
                    .clickable { onSelectMode(mode) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (mode == CarViewMode.AUTO_ROTATE) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) ObsidianColors.IceCyan else ObsidianColors.DimSilver)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                val s = appStrings()
                val localizedLabel = when (mode) {
                    CarViewMode.AUTO_ROTATE -> s.viewAuto360
                    CarViewMode.FRONT -> s.viewFront
                    CarViewMode.FRONT_45 -> s.viewFront45
                    CarViewMode.BACK -> s.viewBack
                    CarViewMode.BACK_45 -> s.viewBack45
                }
                Text(
                    text = localizedLabel,
                    color = if (isSelected) ObsidianColors.PureSilver else ObsidianColors.MutedSilver,
                    fontSize = 9.5.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

private fun createCarPaintColorFilter(paintColor: KiaRioPaintColor): ColorFilter? {
    return when (paintColor) {
        KiaRioPaintColor.SNOW_WHITE_PEARL -> null
        KiaRioPaintColor.BRIGHT_SILVER -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.82f, 0f, 0f, 0f, 0f,
                    0f, 0.85f, 0f, 0f, 0f,
                    0f, 0f, 0.90f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        KiaRioPaintColor.SIGNAL_RED -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.95f, 0f, 0f, 0f, 0f,
                    0f, 0.28f, 0f, 0f, 0f,
                    0f, 0f, 0.30f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        KiaRioPaintColor.WENDY_BROWN -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.68f, 0f, 0f, 0f, 0f,
                    0f, 0.54f, 0f, 0f, 0f,
                    0f, 0f, 0.44f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        KiaRioPaintColor.AURORA_BLACK -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.38f, 0f, 0f, 0f, 0f,
                    0f, 0.40f, 0f, 0f, 0f,
                    0f, 0f, 0.45f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }
}

@Composable
private fun TpmsMinimalReadout(
    tire: TireInfo,
    telemetry: VehicleTelemetry,
    blinkAlpha: Float,
    alignRight: Boolean,
    modifier: Modifier = Modifier
) {
    val isPressureAlert = tire.isPressureAlert(telemetry.tpmsLowThresholdPsi, telemetry.tpmsHighThresholdPsi)
    val isTempAlert = tire.isTempAlert(telemetry.tpmsHighTempC)
    val hasAnyAlert = isPressureAlert || isTempAlert
    val blinkingRed = ObsidianColors.CrimsonAlert.copy(alpha = blinkAlpha)
    val accentColor = if (hasAnyAlert) blinkingRed else Color(0xFF3E4452)
    val psiValueColor = if (isPressureAlert) blinkingRed else ObsidianColors.PureSilver
    val psiUnitColor = if (isPressureAlert) blinkingRed else ObsidianColors.DimSilver
    val tempColor = if (isTempAlert) blinkingRed else ObsidianColors.DimSilver

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!alignRight) {
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
        }

        Column(
            horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = String.format("%.0f", tire.pressurePsi),
                    color = psiValueColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = "psi",
                    color = psiUnitColor,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${tire.code} · ",
                    color = ObsidianColors.DimSilver,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${tire.tempC}°C",
                    color = tempColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (alignRight) {
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
        }
    }
}

@Composable
private fun DoorMicroControl(
    label: String,
    isOpen: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isOpen) ObsidianColors.AlertSurface else Color(0xFF0E1014)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (isOpen) ObsidianColors.CrimsonAlert else Color(0xFF3E4452))
        )
        Text(
            text = if (isOpen) "$label Open" else label,
            color = if (isOpen) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
            fontSize = 10.sp,
            fontWeight = if (isOpen) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private fun DrawScope.drawExteriorLightsOverlay(
    isFrontView: Boolean,
    lights: LightState,
    turnFlashAlpha: Float
) {
    // Compute exact fitted 16:9 image rect (ContentScale.Fit) for pixel-accurate alignment across aspect ratios
    val imgAspect = 640f / 360f
    val boxAspect = size.width / size.height.coerceAtLeast(1f)
    val drawW: Float
    val drawH: Float
    val left: Float
    val top: Float
    if (boxAspect > imgAspect) {
        drawH = size.height
        drawW = drawH * imgAspect
        left = (size.width - drawW) * 0.5f
        top = 0f
    } else {
        drawW = size.width
        drawH = drawW / imgAspect
        left = 0f
        top = (size.height - drawH) * 0.5f
    }

    fun pt(nx: Float, ny: Float) = Offset(left + nx * drawW, top + ny * drawH)

    fun drawLampBloom(
        nx: Float,
        ny: Float,
        radiusRatio: Float,
        coreColor: Color,
        haloColor: Color,
        intensity: Float
    ) {
        if (intensity <= 0.01f) return
        val c = pt(nx, ny)
        val r = drawW * radiusRatio
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    haloColor.copy(alpha = (0.72f * intensity).coerceIn(0f, 1f)),
                    haloColor.copy(alpha = (0.28f * intensity).coerceIn(0f, 1f)),
                    Color.Transparent
                ),
                center = c,
                radius = r
            ),
            radius = r,
            center = c,
            blendMode = BlendMode.Plus
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    coreColor.copy(alpha = (0.95f * intensity).coerceIn(0f, 1f)),
                    coreColor.copy(alpha = 0f)
                ),
                center = c,
                radius = r * 0.38f
            ),
            radius = r * 0.38f,
            center = c,
            blendMode = BlendMode.Plus
        )
    }

    fun drawFloorPool(nx: Float, ny: Float, rxRatio: Float, ryRatio: Float, color: Color, alpha: Float) {
        if (alpha <= 0.01f) return
        val c = pt(nx, ny)
        val rx = drawW * rxRatio
        val ry = drawH * ryRatio
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                center = c,
                radius = rx
            ),
            topLeft = Offset(c.x - rx, c.y - ry),
            size = Size(rx * 2f, ry * 2f),
            blendMode = BlendMode.Plus
        )
    }

    fun drawTailLampBar(nx0: Float, ny0: Float, nx1: Float, ny1: Float, glowAlpha: Float, coreAlpha: Float) {
        val p0 = pt(nx0, ny0)
        val p1 = pt(nx1, ny1)
        drawLine(
            color = Color(0xFFFF1744).copy(alpha = glowAlpha),
            start = p0,
            end = p1,
            strokeWidth = drawH * 0.044f,
            cap = StrokeCap.Round,
            blendMode = BlendMode.Plus
        )
        drawLine(
            color = Color(0xFFFF6E6E).copy(alpha = coreAlpha),
            start = p0,
            end = p1,
            strokeWidth = drawH * 0.018f,
            cap = StrokeCap.Round,
            blendMode = BlendMode.Plus
        )
    }

    if (isFrontView) {
        // 1. Position / City Lights (lower-outer headlamp lens points)
        if (lights.parkingLights) {
            drawLampBloom(0.3713f, 0.6067f, 0.032f, Color(0xFFFFF8E1), Color(0xFFFFE5B4), 0.78f)
            drawLampBloom(0.6148f, 0.6067f, 0.032f, Color(0xFFFFF8E1), Color(0xFFFFE5B4), 0.78f)
        }
        // 2. Low Beam (outer-upper projector bowls + subtle studio floor pool)
        if (lights.lowBeam) {
            drawFloorPool(0.3570f, 0.855f, 0.11f, 0.055f, Color(0xFFEBF3FF), 0.22f)
            drawFloorPool(0.6273f, 0.855f, 0.11f, 0.055f, Color(0xFFEBF3FF), 0.22f)
            drawLampBloom(0.3570f, 0.5569f, 0.048f, Color.White, Color(0xFFD6E8FF), 0.95f)
            drawLampBloom(0.6273f, 0.5569f, 0.048f, Color.White, Color(0xFFD6E8FF), 0.95f)
        }
        // 3. High Beam (inner-center high-intensity reflectors + wider floor spread)
        if (lights.highBeam) {
            drawFloorPool(0.495f, 0.865f, 0.22f, 0.075f, Color(0xFFF2F7FF), 0.30f)
            drawLampBloom(0.3800f, 0.5780f, 0.062f, Color.White, Color(0xFFCCE0FF), 1.0f)
            drawLampBloom(0.6045f, 0.5780f, 0.062f, Color.White, Color(0xFFCCE0FF), 1.0f)
        }
        // 4. Fog Lamps (lower left/right bumper grilles)
        if (lights.fogLights) {
            drawFloorPool(0.3555f, 0.830f, 0.085f, 0.045f, Color(0xFFFFF4D2), 0.20f)
            drawFloorPool(0.6274f, 0.830f, 0.085f, 0.045f, Color(0xFFFFF4D2), 0.20f)
            drawLampBloom(0.3555f, 0.7263f, 0.040f, Color(0xFFFFF9E6), Color(0xFFFFE082), 0.92f)
            drawLampBloom(0.6274f, 0.7321f, 0.040f, Color(0xFFFFF9E6), Color(0xFFFFE082), 0.92f)
        }
        // 5. Turn Signals & Hazard (Front view: Driver's Right = Screen Left, Driver's Left = Screen Right)
        val frontRightBlink = if (lights.rightTurn || lights.hazard) turnFlashAlpha else 0f
        val frontLeftBlink = if (lights.leftTurn || lights.hazard) turnFlashAlpha else 0f
        if (frontRightBlink > 0.01f) {
            drawLampBloom(0.3970f, 0.5960f, 0.032f, Color(0xFFFFD54F), Color(0xFFFF8F00), frontRightBlink)
            drawLampBloom(0.3321f, 0.4237f, 0.018f, Color(0xFFFFD54F), Color(0xFFFF8F00), frontRightBlink * 0.95f)
        }
        if (frontLeftBlink > 0.01f) {
            drawLampBloom(0.5900f, 0.5960f, 0.032f, Color(0xFFFFD54F), Color(0xFFFF8F00), frontLeftBlink)
            drawLampBloom(0.6711f, 0.4208f, 0.018f, Color(0xFFFFD54F), Color(0xFFFF8F00), frontLeftBlink * 0.95f)
        }
    } else {
        // BACK VIEW: Position lights illuminate taillights dimly in red; Brake illuminates brightly + HMSL
        if (lights.parkingLights && !lights.brake) {
            drawLampBloom(0.3879f, 0.4479f, 0.026f, Color(0xFFFF8A80), Color(0xFFD50000), 0.45f)
            drawLampBloom(0.6377f, 0.4538f, 0.026f, Color(0xFFFF8A80), Color(0xFFD50000), 0.45f)
            drawTailLampBar(0.345f, 0.476f, 0.400f, 0.474f, glowAlpha = 0.28f, coreAlpha = 0.35f)
            drawTailLampBar(0.622f, 0.474f, 0.677f, 0.477f, glowAlpha = 0.28f, coreAlpha = 0.35f)
        }
        if (lights.brake) {
            drawLampBloom(0.3714f, 0.4752f, 0.058f, Color(0xFFFF9E80), Color(0xFFFF1744), 1.0f)
            drawLampBloom(0.6503f, 0.4767f, 0.058f, Color(0xFFFF9E80), Color(0xFFFF1744), 1.0f)
            drawTailLampBar(0.344f, 0.476f, 0.402f, 0.474f, glowAlpha = 0.72f, coreAlpha = 0.95f)
            drawTailLampBar(0.620f, 0.474f, 0.678f, 0.477f, glowAlpha = 0.72f, coreAlpha = 0.95f)
            // High-Mount Stop Lamp (HMSL) red LED bar beneath rear spoiler
            val hStart = pt(0.484f, 0.294f)
            val hEnd = pt(0.542f, 0.294f)
            drawLine(
                color = Color(0xFFFF1744).copy(alpha = 0.65f),
                start = hStart,
                end = hEnd,
                strokeWidth = drawH * 0.022f,
                cap = StrokeCap.Round,
                blendMode = BlendMode.Plus
            )
            drawLine(
                color = Color(0xFFFF9E80),
                start = hStart,
                end = hEnd,
                strokeWidth = drawH * 0.009f,
                cap = StrokeCap.Round,
                blendMode = BlendMode.Plus
            )
        }
        val backLeftBlink = if (lights.leftTurn || lights.hazard) turnFlashAlpha else 0f
        val backRightBlink = if (lights.rightTurn || lights.hazard) turnFlashAlpha else 0f
        if (backLeftBlink > 0.01f) {
            drawLampBloom(0.3626f, 0.4386f, 0.032f, Color(0xFFFFD54F), Color(0xFFFF8F00), backLeftBlink)
        }
        if (backRightBlink > 0.01f) {
            drawLampBloom(0.6614f, 0.4382f, 0.032f, Color(0xFFFFD54F), Color(0xFFFF8F00), backRightBlink)
        }
    }
}
