package id.bangsadigital.riosense.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bangsadigital.riosense.model.KiaRioPaintColor
import id.bangsadigital.riosense.model.TireInfo
import id.bangsadigital.riosense.model.VehicleTelemetry
import id.bangsadigital.riosense.ui.theme.ObsidianColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val TURNTABLE_COUNT = 180
private const val DOOR_SEQ_MAX_INDEX = 19

@Composable
fun KiaRioInteractiveStage(
    telemetry: VehicleTelemetry,
    onToggleDoor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val decodeOpts = remember {
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
    }
    // Transparent PNG turntable frames membutuhkan ARGB_8888 agar channel alpha tetap utuh
    val turntableDecodeOpts = remember {
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    }

    // 1. Smart Thread-Safe LRU Frame Cache untuk 180 Frame Transparent PNG (2° per Frame: turntable_000.png .. turntable_179.png)
    //    Kapasitas 90 frame (~80 MB RAM saat aktif) + auto-evict saat minimize agar HU RAM 4GB tetap lega.
    val turntableCache = remember {
        object : LruCache<Int, ImageBitmap>(90) {}
    }

    fun getOrLoadTurntableFrame(index: Int): ImageBitmap? {
        val normIdx = ((index % TURNTABLE_COUNT) + TURNTABLE_COUNT) % TURNTABLE_COUNT
        synchronized(turntableCache) {
            turntableCache.get(normIdx)?.let { return it }
        }
        val name = String.format("car_sequences/turntable_360/turntable_%03d.png", normIdx)
        val loaded = runCatching {
            context.assets.open(name).use {
                BitmapFactory.decodeStream(it, null, turntableDecodeOpts)?.asImageBitmap()
            }
        }.getOrNull()
        if (loaded != null) {
            synchronized(turntableCache) {
                turntableCache.put(normIdx, loaded)
            }
        }
        return loaded
    }

    // State putaran 360° (0f .. 180f, setiap 1.0f = 2° rotasi mulus!)
    val turntableAnim = remember { Animatable(0f) }
    val activeTurntableIdx = ((turntableAnim.value.roundToInt() % TURNTABLE_COUNT) + TURNTABLE_COUNT) % TURNTABLE_COUNT

    // Sliding-window async prefetcher (±35 frame) di background agar putaran & wrap 179->0 tetap 60 FPS tanpa jeda
    LaunchedEffect(activeTurntableIdx) {
        withContext(Dispatchers.IO) {
            for (offset in 0..35) {
                getOrLoadTurntableFrame(activeTurntableIdx + offset)
                if (offset > 0) {
                    getOrLoadTurntableFrame(activeTurntableIdx - offset)
                }
            }
        }
    }

    // 2. Lazy LRU Cache untuk 7 Sekuens Animasi Pintu & Bagasi (Max 45 frame di RAM ~28 MB, hemat 60 MB saat boot!)
    val doorCache = remember {
        object : LruCache<String, ImageBitmap>(45) {}
    }

    fun getOrLoadDoorFrame(prefix: String, step: Int): ImageBitmap? {
        val clampedStep = step.coerceIn(0, DOOR_SEQ_MAX_INDEX)
        val key = "${prefix}_$clampedStep"
        synchronized(doorCache) {
            doorCache.get(key)?.let { return it }
        }
        val name = String.format("car_sequences/doors/%s_%02d.jpg", prefix, clampedStep)
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

    // Kosongkan cache gambar mobil otomatis saat Stage dilepas (mode Minimize) agar Maps/Spotify tetap lega
    DisposableEffect(Unit) {
        onDispose {
            synchronized(turntableCache) { turntableCache.evictAll() }
            synchronized(doorCache) { doorCache.evictAll() }
        }
    }

    var isUserDraggingTurntable by remember { mutableStateOf(false) }

    val d = telemetry.doors
    val rightOpenNow = d.frontRightOpen || d.rearRightOpen
    val leftOpenNow = d.frontLeftOpen || d.rearLeftOpen
    val trunkOpenNow = d.trunkOpen

    // Animasi progres ayunan pintu Kanan, Kiri, dan Bagasi (0f = tertutup, 19f = terbuka penuh)
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

    // Jalankan animasi Buka (0 -> 19) dan Tutup (19 -> 0) secara mulus dengan prefetch asinkron
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
            // Jika mobil sedang di sudut lain, putar balik cepat ke titik awal (0°) sebelum pintu mengayun buka
            val currentMod = ((turntableAnim.value % TURNTABLE_COUNT) + TURNTABLE_COUNT) % TURNTABLE_COUNT
            if (currentMod > 1.0f && currentMod < TURNTABLE_COUNT - 1.0f) {
                val targetZero = if (currentMod <= TURNTABLE_COUNT / 2f) 0f else TURNTABLE_COUNT.toFloat()
                turntableAnim.snapTo(currentMod)
                turntableAnim.animateTo(
                    targetValue = targetZero,
                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                )
                turntableAnim.snapTo(0f)
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
    }

    val infiniteTransition = rememberInfiniteTransition(label = "alertPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val flLow = telemetry.tpms.frontLeft.pressurePsi < telemetry.tpmsLowThresholdPsi
    val frLow = telemetry.tpms.frontRight.pressurePsi < telemetry.tpmsLowThresholdPsi
    val rlLow = telemetry.tpms.rearLeft.pressurePsi < telemetry.tpmsLowThresholdPsi
    val rrLow = telemetry.tpms.rearRight.pressurePsi < telemetry.tpmsLowThresholdPsi

    val paintColorFilter = remember(telemetry.paintColor) {
        createCarPaintColorFilter(telemetry.paintColor)
    }

    val showRightDoorAnim = rightSwingAnim.value > 0.05f && !isUserDraggingTurntable
    val showLeftDoorAnim = leftSwingAnim.value > 0.05f && !isUserDraggingTurntable
    val showTrunkAnim = trunkSwingAnim.value > 0.05f && !isUserDraggingTurntable

    val activePanelsCount = (if (showLeftDoorAnim) 1 else 0) +
        (if (showRightDoorAnim) 1 else 0) +
        (if (showTrunkAnim) 1 else 0)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .pointerInput(rightOpenNow, leftOpenNow, trunkOpenNow) {
                detectDragGestures(
                    onDragStart = {
                        isUserDraggingTurntable = true
                    },
                    onDragEnd = {
                        if (rightOpenNow || leftOpenNow || trunkOpenNow) {
                            isUserDraggingTurntable = false
                        }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    scope.launch {
                        // 180 frame (2 derajat per frame): geser ~514px = 360 derajat penuh!
                        turntableAnim.snapTo(turntableAnim.value + dragAmount.x * 0.35f)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        isUserDraggingTurntable = false
                        scope.launch {
                            val currentMod = ((turntableAnim.value % TURNTABLE_COUNT) + TURNTABLE_COUNT) % TURNTABLE_COUNT
                            val targetZero = if (currentMod <= TURNTABLE_COUNT / 2f) 0f else TURNTABLE_COUNT.toFloat()
                            turntableAnim.snapTo(currentMod)
                            turntableAnim.animateTo(targetZero, tween(320, easing = FastOutSlowInEasing))
                            turntableAnim.snapTo(0f)
                        }
                    }
                )
            }
    ) {
        when {
            // KASUS 1: Multi-Panel Aktif Bersamaan (Misal Kiri + Kanan, atau Pintu + Bagasi)
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

            // KASUS 2: Sisi Kanan saja sedang mengayun buka / tutup (FR, RR, atau FR+RR)
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

            // KASUS 3: Sisi Kiri saja sedang mengayun buka / tutup (FL, RL, atau FL+RL)
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

            // KASUS 4: Bagasi Belakang (Tailgate) saja sedang mengayun buka / tutup
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

            // KASUS 5: Mode Putaran 360° Transparent PNG 2°/Frame (180 Frame: 000 s/d 179)
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
            }
        }

        // PERSPECTIVE TPMS TIRE WARNING GLOW OVERLAY ON STUDIO FLOOR
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            fun drawTireFloorWarning(cxRatio: Float, cyRatio: Float) {
                val center = Offset(w * cxRatio, h * cyRatio)
                val rx = w * 0.12f
                val ry = h * 0.06f
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ObsidianColors.CrimsonAlert.copy(alpha = pulseAlpha * 0.50f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = rx
                    ),
                    topLeft = Offset(center.x - rx, center.y - ry),
                    size = Size(rx * 2f, ry * 2f)
                )
            }

            if (flLow) drawTireFloorWarning(0.28f, 0.66f)
            if (frLow) drawTireFloorWarning(0.76f, 0.64f)
            if (rlLow) drawTireFloorWarning(0.32f, 0.78f)
            if (rrLow) drawTireFloorWarning(0.54f, 0.78f)
        }

        // BORDERLESS MINIMALIST TPMS READOUTS & SLEEK DOOR MICRO-TRIGGERS
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                TpmsMinimalReadout(
                    tire = telemetry.tpms.frontLeft,
                    isLow = flLow,
                    alignRight = false
                )

                val currentDeg = (((turntableAnim.value.roundToInt() % TURNTABLE_COUNT) + TURNTABLE_COUNT) % TURNTABLE_COUNT) * 2
                Text(
                    text = when {
                        activePanelsCount >= 2 -> "Multi-Door Animation · Drag to Orbit 360°"
                        activePanelsCount == 1 -> "Door Swing Animation · Drag to Orbit 360°"
                        else -> "360° Studio Orbit (${currentDeg}°/360°) · Double-tap Reset"
                    },
                    color = Color(0xFF3E4452),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp)
                )

                TpmsMinimalReadout(
                    tire = telemetry.tpms.frontRight,
                    isLow = frLow,
                    alignRight = true
                )
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                TpmsMinimalReadout(
                    tire = telemetry.tpms.rearLeft,
                    isLow = rlLow,
                    alignRight = false
                )

                DoorMicroControl(
                    label = "Trunk",
                    isOpen = d.trunkOpen,
                    onClick = { onToggleDoor("TRUNK") }
                )

                TpmsMinimalReadout(
                    tire = telemetry.tpms.rearRight,
                    isLow = rrLow,
                    alignRight = true
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
    isLow: Boolean,
    alignRight: Boolean
) {
    val accentColor = if (isLow) ObsidianColors.CrimsonAlert else Color(0xFF3E4452)

    Row(
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
                    color = if (isLow) ObsidianColors.CrimsonAlert else ObsidianColors.PureSilver,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = "psi",
                    color = ObsidianColors.DimSilver,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            Text(
                text = "${tire.code} · ${tire.tempC}°C",
                color = if (isLow) ObsidianColors.CrimsonAlert else ObsidianColors.DimSilver,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
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
