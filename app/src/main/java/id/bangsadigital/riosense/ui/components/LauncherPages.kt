package id.bangsadigital.riosense.ui.components

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bangsadigital.riosense.launcher.InstalledAppsRepository
import id.bangsadigital.riosense.launcher.MediaSessionBridge
import id.bangsadigital.riosense.launcher.NowPlayingState
import id.bangsadigital.riosense.launcher.WeatherRepository
import id.bangsadigital.riosense.launcher.WeatherSnapshot
import id.bangsadigital.riosense.model.AppDrawerBackground
import id.bangsadigital.riosense.model.AppIconSize
import id.bangsadigital.riosense.model.DockDisplayMode
import id.bangsadigital.riosense.model.DockPosition
import id.bangsadigital.riosense.model.InstalledAppInfo
import id.bangsadigital.riosense.model.LauncherSettings
import id.bangsadigital.riosense.model.MaintenanceState
import id.bangsadigital.riosense.model.VehicleTelemetry
import id.bangsadigital.riosense.ui.i18n.AppLanguage
import id.bangsadigital.riosense.ui.i18n.AppStrings
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

/**
 * Page 0 (Swipe Right from Home): Borderless Apple + Nothing OS Dot-Matrix Obsidian Bento Hub
 * featuring Dot-Matrix Clock & Weekly Calendar, Live Music Player, Cached Weather, and Vehicle Snapshot.
 */
@Composable
fun LauncherWidgetsPage(
    telemetry: VehicleTelemetry,
    maintenance: MaintenanceState,
    onOpenControlsTab: (ControlsTab) -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
    isPageVisible: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = appStrings()
    val isId = LocalAppLanguage.current == AppLanguage.ID
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var isSystemMusicActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            val millisIntoMinute = nowMs % 60_000L
            delay((60_000L - millisIntoMinute).coerceAtLeast(1000L))
        }
    }

    LaunchedEffect(Unit) {
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Minimal Borderless Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = s.widgetsTitle,
                    color = ObsidianColors.PureSilver,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp
                )
                Text(
                    text = s.widgetsSubtitle,
                    color = ObsidianColors.DimSilver,
                    fontSize = 10.sp
                )
            }
            LauncherHomeIconButton(onClick = onBackToHome)
        }

        // Borderless 2x2 Apple + Nothing Dot-Matrix Bento Grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 10.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // LEFT COLUMN: Tight Clock (top) + Live Music Player (bottom)
            Column(
                modifier = Modifier
                    .width(234.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                RioSenseClockWidgetCard(
                    timeStr = timeStr,
                    dateStr = dateStr,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.46f)
                )

                RioSenseMusicWidgetCard(
                    nowPlaying = nowPlaying,
                    isAudioActive = isAudioActive,
                    isPageVisible = isPageVisible,
                    trackProgress = trackProgress,
                    isId = isId,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.54f)
                )
            }

            // RIGHT COLUMN: Dot-Matrix Weather (top) + Borderless Vehicle Snapshot (bottom)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                RioSenseWeatherWidgetCard(
                    weather = weather,
                    isId = isId,
                    onRefresh = {
                        scope.launch { WeatherRepository.refreshIfStale(context, force = true) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.54f)
                )

                RioSenseVehicleWidgetCard(
                    telemetry = telemetry,
                    dueCount = dueCount,
                    odoText = odoText,
                    s = s,
                    onOpenControlsTab = onOpenControlsTab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.46f)
                )
            }
        }
    }
}

@Composable
internal fun RioSenseClockWidgetCard(
    timeStr: String,
    dateStr: String,
    modifier: Modifier = Modifier
) {
    ObsidianWidgetCard(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            val maxDotByW = (maxWidth.value / 33f).dp
            val maxDotByH = (maxHeight.value / 15f).dp
            val dotSize = minOf(maxDotByW, maxDotByH).coerceIn(3.2.dp, 6.4.dp)
            val dotGap = dotSize * 0.37f
            val charGap = dotSize * 1.2f
            val showDate = maxHeight >= 52.dp
            val compactHeight = maxHeight < 90.dp

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center
            ) {
                DotMatrixText(
                    text = timeStr,
                    dotSize = dotSize,
                    dotGap = dotGap,
                    charGap = charGap,
                    color = ObsidianColors.PureSilver
                )
                if (showDate) {
                    Spacer(modifier = Modifier.height(if (compactHeight) 4.dp else 8.dp))
                    Text(
                        text = dateStr,
                        color = ObsidianColors.MutedSilver,
                        fontSize = if (compactHeight) 10.5.sp else 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun RioSenseMusicWidgetCard(
    nowPlaying: NowPlayingState,
    isAudioActive: Boolean,
    isPageVisible: Boolean,
    trackProgress: Float,
    isId: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    ObsidianWidgetCard(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val showSpectrum = maxHeight >= 104.dp
            val showArtist = maxHeight >= 66.dp
            val artSize = if (maxHeight < 78.dp) 44.dp else 62.dp

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(artSize)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF191D27))
                            .clickable { MediaSessionBridge.openMusicApp(context) },
                        contentAlignment = Alignment.Center
                    ) {
                        val art = nowPlaying.albumArt
                        if (art != null) {
                            Image(
                                bitmap = art.asImageBitmap(),
                                contentDescription = nowPlaying.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Canvas(modifier = Modifier.size(artSize * 0.52f)) {
                                val c = Offset(size.width * 0.5f, size.height * 0.5f)
                                drawCircle(
                                    color = ObsidianColors.DimSilver.copy(alpha = 0.45f),
                                    radius = size.minDimension * 0.46f,
                                    center = c,
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                                drawCircle(
                                    color = ObsidianColors.MutedSilver.copy(alpha = 0.65f),
                                    radius = size.minDimension * 0.24f,
                                    center = c,
                                    style = Stroke(width = 1.2.dp.toPx())
                                )
                                drawCircle(
                                    color = ObsidianColors.PureSilver,
                                    radius = size.minDimension * 0.08f,
                                    center = c
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(if (showArtist) 5.dp else 2.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = nowPlaying.title.ifBlank {
                                    if (isId) "Pemutar Musik" else "Now Playing"
                                },
                                color = ObsidianColors.PureSilver,
                                fontSize = 13.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (showArtist) {
                                if (!nowPlaying.hasListenerPermission) {
                                    Text(
                                        text = if (isId) "Aktifkan info lagu live ›" else "Enable live track info ›",
                                        color = ObsidianColors.CobaltAccent,
                                        fontSize = 10.5.sp,
                                        lineHeight = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable {
                                            MediaSessionBridge.openNotificationListenerSettings(context)
                                        }
                                    )
                                } else {
                                    Text(
                                        text = nowPlaying.artist.ifBlank {
                                            "Spotify · YouTube Music"
                                        },
                                        color = ObsidianColors.MutedSilver,
                                        fontSize = 10.5.sp,
                                        lineHeight = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { MediaSessionBridge.skipPrevious(context) },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(13.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    drawLine(
                                        color = ObsidianColors.ChampagneAccent,
                                        start = Offset(w * 0.18f, h * 0.22f),
                                        end = Offset(w * 0.18f, h * 0.78f),
                                        strokeWidth = 1.7.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    val tri = Path().apply {
                                        moveTo(w * 0.84f, h * 0.20f)
                                        lineTo(w * 0.34f, h * 0.50f)
                                        lineTo(w * 0.84f, h * 0.80f)
                                        close()
                                    }
                                    drawPath(tri, color = ObsidianColors.ChampagneAccent)
                                }
                            }

                            MusicPlayPauseProgressButton(
                                isPlaying = isAudioActive,
                                progress = trackProgress,
                                hasDuration = nowPlaying.durationMs > 0L,
                                onClick = { MediaSessionBridge.togglePlayPause(context) }
                            )

                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { MediaSessionBridge.skipNext(context) },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(13.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    val tri = Path().apply {
                                        moveTo(w * 0.16f, h * 0.20f)
                                        lineTo(w * 0.66f, h * 0.50f)
                                        lineTo(w * 0.16f, h * 0.80f)
                                        close()
                                    }
                                    drawPath(tri, color = ObsidianColors.ChampagneAccent)
                                    drawLine(
                                        color = ObsidianColors.ChampagneAccent,
                                        start = Offset(w * 0.82f, h * 0.22f),
                                        end = Offset(w * 0.82f, h * 0.78f),
                                        strokeWidth = 1.7.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }

                if (showSpectrum) {
                    DotMatrixAudioSpectrum(
                        isPlaying = isAudioActive,
                        isVisible = isPageVisible,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun RioSenseWeatherWidgetCard(
    weather: WeatherSnapshot,
    isId: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    ObsidianWidgetCard(
        modifier = modifier,
        onClick = onRefresh
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val showForecast = maxWidth >= 235.dp && maxHeight >= 108.dp
            val showLocationRight = maxWidth >= 210.dp

            if (showForecast) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DotMatrixWeatherIcon(
                                condition = weather.condition,
                                modifier = Modifier.size(38.dp)
                            )
                            DotMatrixText(
                                text = "${weather.tempC}°",
                                dotSize = 3.5.dp,
                                dotGap = 1.4.dp,
                                charGap = 4.5.dp,
                                color = ObsidianColors.PureSilver
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "H ${weather.highC}°",
                                    color = ObsidianColors.MutedSilver,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "L ${weather.lowC}°",
                                    color = ObsidianColors.DimSilver,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (showLocationRight) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = weather.locationName,
                                    color = ObsidianColors.PureSilver,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isId) weather.conditionLabelId else weather.conditionLabelEn,
                                    color = ObsidianColors.MutedSilver,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        weather.daily.take(5).forEach { day ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = day.dayLabel,
                                    color = ObsidianColors.DimSilver,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                DotMatrixWeatherIcon(
                                    condition = day.condition,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "${day.highC}°",
                                    color = ObsidianColors.PureSilver,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${day.lowC}°",
                                    color = ObsidianColors.DimSilver,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DotMatrixWeatherIcon(
                            condition = weather.condition,
                            modifier = Modifier.size(34.dp)
                        )
                        DotMatrixText(
                            text = "${weather.tempC}°",
                            dotSize = 3.4.dp,
                            dotGap = 1.3.dp,
                            charGap = 4.2.dp,
                            color = ObsidianColors.PureSilver
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${weather.locationName} · H ${weather.highC}° / L ${weather.lowC}°",
                        color = ObsidianColors.MutedSilver,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun RioSenseVehicleWidgetCard(
    telemetry: VehicleTelemetry,
    dueCount: Int,
    odoText: String,
    s: AppStrings,
    onOpenControlsTab: (ControlsTab) -> Unit,
    modifier: Modifier = Modifier
) {
    ObsidianWidgetCard(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val showInlineMetrics = maxWidth >= 240.dp
            val showCompactMetrics = !showInlineMetrics && maxHeight >= 100.dp

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = if (showInlineMetrics) "ODOMETER · KIA RIO UB" else "KIA RIO UB",
                            color = ObsidianColors.DimSilver,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.7.sp
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DotMatrixText(
                                text = odoText,
                                dotSize = if (showInlineMetrics) 2.4.dp else 2.0.dp,
                                dotGap = 0.9.dp,
                                charGap = 2.8.dp,
                                color = ObsidianColors.PureSilver
                            )
                            Text(
                                text = "KM",
                                color = ObsidianColors.MutedSilver,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (showInlineMetrics) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            BorderlessTelemetryMetric(
                                label = s.coolant.uppercase(),
                                value = "${telemetry.coolantTempC}°C",
                                accent = if (telemetry.isCoolantOverheat) ObsidianColors.CrimsonAlert else ObsidianColors.EmeraldNominal
                            )
                            BorderlessTelemetryMetric(
                                label = s.battery.uppercase(),
                                value = String.format("%.1fV", telemetry.batteryVoltage),
                                accent = if (telemetry.isBatteryAlert) ObsidianColors.CrimsonAlert else ObsidianColors.PureSilver
                            )
                            BorderlessTelemetryMetric(
                                label = s.serviceTitle.uppercase(),
                                value = if (dueCount == 0) s.statusAllOk else s.statusDueSoon(dueCount),
                                accent = if (dueCount == 0) ObsidianColors.EmeraldNominal else ObsidianColors.AmberCaution
                            )
                        }
                    }
                }

                if (showCompactMetrics) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BorderlessTelemetryMetric(
                            label = s.coolant.uppercase(),
                            value = "${telemetry.coolantTempC}°C",
                            accent = if (telemetry.isCoolantOverheat) ObsidianColors.CrimsonAlert else ObsidianColors.EmeraldNominal
                        )
                        BorderlessTelemetryMetric(
                            label = s.battery.uppercase(),
                            value = String.format("%.1fV", telemetry.batteryVoltage),
                            accent = if (telemetry.isBatteryAlert) ObsidianColors.CrimsonAlert else ObsidianColors.PureSilver
                        )
                    }
                }

                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        ControlsTab.SERVICE,
                        ControlsTab.TRIP_HISTORY,
                        ControlsTab.SETTINGS
                    ).forEach { tab ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { onOpenControlsTab(tab) },
                            contentAlignment = Alignment.Center
                        ) {
                            QuickJumpVectorIcon(
                                tab = tab,
                                showAlertDot = tab == ControlsTab.SERVICE && dueCount > 0,
                                tint = ObsidianColors.MutedSilver
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ObsidianWidgetCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF14171F), Color(0xFF0C0E13))
                )
            )
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            )
            .padding(horizontal = 18.dp, vertical = 15.dp),
        content = content
    )
}

@Composable
internal fun MusicPlayPauseProgressButton(
    isPlaying: Boolean,
    progress: Float,
    hasDuration: Boolean,
    onClick: () -> Unit
) {
    val sweepStartAngle = if (isPlaying && !hasDuration) {
        val transition = rememberInfiniteTransition(label = "musicRingSweep")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "musicRingAngle"
        )
        angle
    } else {
        -90f
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ringStrokePx = 2.2.dp.toPx()
            val diameter = (minOf(size.width, size.height) - ringStrokePx).coerceAtLeast(1f)
            val left = (size.width - diameter) * 0.5f
            val top = (size.height - diameter) * 0.5f
            val arcTopLeft = Offset(left, top)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = Color(0xFF252A36),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = ringStrokePx)
            )

            val clampedProgress = progress.coerceIn(0f, 1f)
            if (hasDuration && clampedProgress > 0.001f) {
                drawArc(
                    color = ObsidianColors.PureSilver,
                    startAngle = -90f,
                    sweepAngle = 360f * clampedProgress,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = ringStrokePx, cap = StrokeCap.Round)
                )
            } else if (isPlaying) {
                drawArc(
                    color = ObsidianColors.CobaltAccent.copy(alpha = 0.85f),
                    startAngle = sweepStartAngle,
                    sweepAngle = 85f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = ringStrokePx, cap = StrokeCap.Round)
                )
            }
        }

        Canvas(modifier = Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            if (isPlaying) {
                val barW = w * 0.22f
                drawRoundRect(
                    color = ObsidianColors.PureSilver,
                    topLeft = Offset(w * 0.22f, h * 0.16f),
                    size = Size(barW, h * 0.68f),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                drawRoundRect(
                    color = ObsidianColors.PureSilver,
                    topLeft = Offset(w * 0.56f, h * 0.16f),
                    size = Size(barW, h * 0.68f),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            } else {
                val tri = Path().apply {
                    moveTo(w * 0.28f, h * 0.14f)
                    lineTo(w * 0.88f, h * 0.50f)
                    lineTo(w * 0.28f, h * 0.86f)
                    close()
                }
                drawPath(tri, color = ObsidianColors.PureSilver)
            }
        }
    }
}

@Composable
internal fun BorderlessTelemetryMetric(
    label: String,
    value: String,
    accent: Color
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = ObsidianColors.DimSilver,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp
        )
        Text(
            text = value,
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
internal fun QuickJumpVectorIcon(
    tab: ControlsTab,
    showAlertDot: Boolean,
    tint: Color
) {
    Box(
        modifier = Modifier.size(22.dp),
        contentAlignment = Alignment.Center
    ) {
        val iconModifier = Modifier.size(17.dp)
        when (tab) {
            ControlsTab.SERVICE -> ServiceWrenchVectorIcon(tint = tint, modifier = iconModifier)
            ControlsTab.TRIP_HISTORY -> TripRouteVectorIcon(tint = tint, modifier = iconModifier)
            else -> SettingsGearVectorIcon(tint = tint, modifier = iconModifier)
        }

        if (showAlertDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(ObsidianColors.AmberCaution)
            )
        }
    }
}

/**
 * Stationary borderless top header for the horizontally paged App Drawer
 * (remains anchored when flipping between App Drawer pages).
 */
@Composable
fun LauncherAppDrawerHeader(
    totalAppCount: Int,
    appPageIndex: Int,
    totalAppPages: Int,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = appStrings()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${s.allAppsTitle} ($totalAppCount)",
                    color = ObsidianColors.PureSilver,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (totalAppPages > 1) {
                    Text(
                        text = "${appPageIndex + 1}/$totalAppPages",
                        color = ObsidianColors.DimSilver,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Text(
                text = s.longPressToPinHint,
                color = ObsidianColors.DimSilver,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Top-Right Borderless OEM Icons: Search + Settings Gear + Home
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LauncherSearchIconButton(onClick = onOpenSearch)
            LauncherSettingsGearButton(onClick = onOpenSettings)
            LauncherHomeIconButton(onClick = onBackToHome)
        }
    }
}

/**
 * Page 2+ (Swipe Left from Home): Horizontally paged Android Head Unit App Drawer
 * with no vertical scrolling, dynamic page overflow to the right, configurable icon sizing,
 * and a tilted pushpin on pinned apps.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherAppDrawerPage(
    pageApps: List<InstalledAppInfo>,
    columns: Int,
    rows: Int,
    iconSize: AppIconSize,
    pinnedPackages: List<String>,
    onTogglePinApp: (InstalledAppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val s = appStrings()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 66.dp, bottom = 14.dp)
    ) {
        if (pageApps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s.loadingAppsLabel,
                    color = ObsidianColors.DimSilver,
                    fontSize = 12.sp
                )
            }
        } else {
            // Non-scrolling fixed grid matching screen dimensions & chosen icon size
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (r in 0 until rows) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (c in 0 until columns) {
                            val idx = r * columns + c
                            if (idx < pageApps.size) {
                                val app = pageApps[idx]
                                val isPinned = pinnedPackages.contains(app.packageName)
                                val iconBitmap by produceState<ImageBitmap?>(
                                    initialValue = null,
                                    key1 = app.packageName
                                ) {
                                    value = withContext(Dispatchers.IO) {
                                        InstalledAppsRepository
                                            .getAppIconBitmap(context, app.packageName, 128)
                                            ?.asImageBitmap()
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    InstalledAppsRepository.launchApp(context, app.packageName)
                                                },
                                                onLongClick = { onTogglePinApp(app) }
                                            )
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier.size(iconSize.iconDp.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (iconBitmap != null) {
                                                Image(
                                                    bitmap = iconBitmap!!,
                                                    contentDescription = app.label,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(RoundedCornerShape(11.dp))
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(RoundedCornerShape(11.dp))
                                                        .background(ObsidianColors.ObsidianPanel)
                                                )
                                            }

                                            if (isPinned) {
                                                val pinSizeDp = when (iconSize) {
                                                    AppIconSize.SMALL -> 18.dp
                                                    AppIconSize.MEDIUM -> 22.dp
                                                    AppIconSize.LARGE -> 26.dp
                                                }
                                                val pinOffsetDp = when (iconSize) {
                                                    AppIconSize.SMALL -> 4.dp
                                                    AppIconSize.MEDIUM -> 5.dp
                                                    AppIconSize.LARGE -> 6.dp
                                                }
                                                TiltedPinVectorIcon(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .offset(x = pinOffsetDp, y = -pinOffsetDp)
                                                        .size(pinSizeDp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Text(
                                            text = app.label,
                                            color = ObsidianColors.PureSilver,
                                            fontSize = iconSize.labelSp.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else {
                                Spacer(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * macOS Spotlight (Cmd+Space)-inspired centered floating search modal for the App Drawer.
 * Auto-focuses a large search input in the upper-middle area and lists instant matches below.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoxScope.AppSpotlightSearchOverlay(
    visible: Boolean,
    allApps: List<InstalledAppInfo>,
    pinnedPackages: List<String>,
    onLaunchApp: (InstalledAppInfo) -> Unit,
    onTogglePinApp: (InstalledAppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val s = appStrings()
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (visible) {
            delay(60L)
            runCatching { focusRequester.requestFocus() }
        } else {
            query = ""
        }
    }

    val matches = remember(allApps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else allApps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(130)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 56.dp, start = 24.dp, end = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .widthIn(max = 560.dp)
                .shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.Black,
                    spotColor = Color.Black
                )
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xF5151922), Color(0xF50E1118))
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
        ) {
            // Large centered Spotlight search input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    val w = size.width
                    val h = size.height
                    val stroke = 1.8.dp.toPx()
                    val radius = w * 0.30f
                    val center = Offset(w * 0.42f, h * 0.42f)
                    drawCircle(
                        color = ObsidianColors.MutedSilver,
                        radius = radius,
                        center = center,
                        style = Stroke(width = stroke)
                    )
                    drawLine(
                        color = ObsidianColors.MutedSilver,
                        start = Offset(center.x + radius * 0.72f, center.y + radius * 0.72f),
                        end = Offset(w * 0.86f, h * 0.86f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = s.searchAppsPlaceholder,
                            color = ObsidianColors.DimSilver,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = ObsidianColors.PureSilver,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(ObsidianColors.CobaltAccent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                matches.firstOrNull()?.let { onLaunchApp(it) }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }

                // Clear / Close X vector button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (query.isNotEmpty()) query = "" else onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        val w = size.width
                        val h = size.height
                        val stroke = 1.6.dp.toPx()
                        drawLine(
                            color = ObsidianColors.MutedSilver,
                            start = Offset(w * 0.15f, h * 0.15f),
                            end = Offset(w * 0.85f, h * 0.85f),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = ObsidianColors.MutedSilver,
                            start = Offset(w * 0.85f, h * 0.15f),
                            end = Offset(w * 0.15f, h * 0.85f),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            if (query.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ObsidianColors.ObsidianHairline)
                )

                if (matches.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = s.noAppsFoundLabel,
                            color = ObsidianColors.DimSilver,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                    ) {
                        items(matches, key = { it.packageName }) { app ->
                            val isPinned = pinnedPackages.contains(app.packageName)
                            val iconBitmap by produceState<ImageBitmap?>(
                                initialValue = null,
                                key1 = app.packageName
                            ) {
                                value = withContext(Dispatchers.IO) {
                                    InstalledAppsRepository
                                        .getAppIconBitmap(context, app.packageName, 128)
                                        ?.asImageBitmap()
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onLaunchApp(app) },
                                        onLongClick = { onTogglePinApp(app) }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (iconBitmap != null) {
                                    Image(
                                        bitmap = iconBitmap!!,
                                        contentDescription = app.label,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ObsidianColors.ObsidianPanel)
                                    )
                                }

                                Text(
                                    text = app.label,
                                    color = ObsidianColors.PureSilver,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isPinned) {
                                    TiltedPinVectorIcon(modifier = Modifier.size(16.dp))
                                }

                                Text(
                                    text = "›",
                                    color = ObsidianColors.DimSilver,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherSearchIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(17.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 1.6.dp.toPx()
            val tint = ObsidianColors.MutedSilver
            val radius = w * 0.30f
            val center = Offset(w * 0.42f, h * 0.42f)
            drawCircle(
                color = tint,
                radius = radius,
                center = center,
                style = Stroke(width = stroke)
            )
            drawLine(
                color = tint,
                start = Offset(center.x + radius * 0.72f, center.y + radius * 0.72f),
                end = Offset(w * 0.86f, h * 0.86f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
internal fun LauncherHomeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(17.dp)) {
            val w = size.width
            val h = size.height
            val p = Path().apply {
                moveTo(w * 0.14f, h * 0.48f)
                lineTo(w * 0.50f, h * 0.16f)
                lineTo(w * 0.86f, h * 0.48f)
                moveTo(w * 0.24f, h * 0.44f)
                lineTo(w * 0.24f, h * 0.84f)
                lineTo(w * 0.76f, h * 0.84f)
                lineTo(w * 0.76f, h * 0.44f)
            }
            drawPath(
                path = p,
                color = ObsidianColors.PureSilver,
                style = Stroke(width = 1.65.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
private fun LauncherSettingsGearButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        SettingsGearVectorIcon(
            tint = ObsidianColors.MutedSilver,
            modifier = Modifier.size(17.dp)
        )
    }
}

/**
 * Monochrome luxury pushpin vector icon (Dark Grey cap/collar + muted Light Grey center body +
 * cool Steel needle + bold Black outer contrast border, tilted 36°).
 */
@Composable
private fun TiltedPinVectorIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val darkGreyCap = Color(0xFF424854)
        val mutedLightGreyBody = Color(0xFF9AA3B2)
        val steelNeedle = Color(0xFF858D9D)
        val outlineBlack = Color(0xF7050507)

        rotate(degrees = 36f, pivot = Offset(w * 0.5f, h * 0.5f)) {
            val fullHeadPath = Path().apply {
                moveTo(w * 0.30f, h * 0.16f)
                lineTo(w * 0.70f, h * 0.16f)
                lineTo(w * 0.70f, h * 0.25f)
                lineTo(w * 0.62f, h * 0.28f)
                lineTo(w * 0.62f, h * 0.42f)
                lineTo(w * 0.74f, h * 0.54f)
                lineTo(w * 0.26f, h * 0.54f)
                lineTo(w * 0.38f, h * 0.42f)
                lineTo(w * 0.38f, h * 0.28f)
                lineTo(w * 0.30f, h * 0.25f)
                close()
            }

            // Slightly thicker black contrast border so the pin reads cleanly on bright & dark icons
            drawPath(
                path = fullHeadPath,
                color = outlineBlack,
                style = Stroke(width = 3.4.dp.toPx(), join = StrokeJoin.Round)
            )
            drawLine(
                color = outlineBlack,
                start = Offset(w * 0.50f, h * 0.53f),
                end = Offset(w * 0.50f, h * 0.89f),
                strokeWidth = 3.4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Steel needle
            drawLine(
                color = steelNeedle,
                start = Offset(w * 0.50f, h * 0.53f),
                end = Offset(w * 0.50f, h * 0.88f),
                strokeWidth = 1.4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Dark-grey center body
            val centerBody = Path().apply {
                moveTo(w * 0.38f, h * 0.25f)
                lineTo(w * 0.62f, h * 0.25f)
                lineTo(w * 0.62f, h * 0.44f)
                lineTo(w * 0.38f, h * 0.44f)
                close()
            }
            drawPath(path = centerBody, color = darkGreyCap)

            // Muted light-grey top cap & flared collar
            val capBar = Path().apply {
                moveTo(w * 0.30f, h * 0.16f)
                lineTo(w * 0.70f, h * 0.16f)
                lineTo(w * 0.70f, h * 0.26f)
                lineTo(w * 0.30f, h * 0.26f)
                close()
            }
            val collarBase = Path().apply {
                moveTo(w * 0.38f, h * 0.42f)
                lineTo(w * 0.62f, h * 0.42f)
                lineTo(w * 0.74f, h * 0.54f)
                lineTo(w * 0.26f, h * 0.54f)
                close()
            }
            drawPath(path = capBar, color = mutedLightGreyBody)
            drawPath(path = collarBase, color = mutedLightGreyBody)
        }
    }
}

/**
 * Procedural monochrome dark/light/black/white luxury Canvas background for App Drawer pages
 * and miniature Settings preview swatches.
 */
@Composable
fun AppDrawerBackgroundCanvas(
    preset: AppDrawerBackground,
    isPreview: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        when (preset) {
            AppDrawerBackground.OBSIDIAN_PURE -> {
                drawRect(color = ObsidianColors.DeepObsidian)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A1E27).copy(alpha = 0.55f),
                            ObsidianColors.DeepObsidian
                        ),
                        center = Offset(w * 0.5f, h * 0.12f),
                        radius = w * 0.85f
                    )
                )
            }

            AppDrawerBackground.CARBON_WEAVE -> {
                drawRect(color = Color(0xFF07080B))
                val cell = if (isPreview) 6.dp.toPx() else 16.dp.toPx()
                val cols = (w / cell).toInt() + 2
                val rows = (h / cell).toInt() + 2
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val isLightCell = (r + c) % 2 == 0
                        val x = c * cell
                        val y = r * cell
                        drawRect(
                            color = if (isLightCell) Color(0xFF13161D) else Color(0xFF090B0E),
                            topLeft = Offset(x, y),
                            size = Size(cell * 0.92f, cell * 0.48f)
                        )
                        drawRect(
                            color = if (isLightCell) Color(0xFF1E232E) else Color(0xFF101319),
                            topLeft = Offset(x, y + cell * 0.5f),
                            size = Size(cell * 0.48f, cell * 0.44f)
                        )
                    }
                }
                // Soft dark vignette so app icons pop cleanly
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xC8050507)
                        ),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = maxOf(w, h) * 0.68f
                    )
                )
            }

            AppDrawerBackground.BRUSHED_TITANIUM -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF171B22),
                            Color(0xFF090B0F),
                            Color(0xFF12151C)
                        )
                    )
                )
                // Diagonal silver-white metallic sheen
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.08f),
                            Color(0xFFD8DEE9).copy(alpha = 0.11f),
                            Color.Transparent
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                )
                val step = if (isPreview) 3.dp.toPx() else 5.dp.toPx()
                val strokeW = if (isPreview) 0.5.dp.toPx() else 0.7.dp.toPx()
                var y = 0f
                var idx = 0
                while (y < h) {
                    val alpha = if (idx % 3 == 0) 0.045f else 0.02f
                    drawLine(
                        color = Color.White.copy(alpha = alpha),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = strokeW
                    )
                    y += step
                    idx++
                }
            }

            AppDrawerBackground.TOPO_CONTOUR -> {
                drawRect(color = Color(0xFF06070A))
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.78f, h * 0.24f),
                        radius = maxOf(w, h) * 0.6f
                    )
                )
                val lineCount = if (isPreview) 6 else 12
                val baseStroke = if (isPreview) 0.8.dp.toPx() else 1.2.dp.toPx()
                for (i in 0 until lineCount) {
                    val frac = i / (lineCount - 1f).coerceAtLeast(1f)
                    val yStart = h * (0.12f + frac * 0.85f)
                    val yEnd = h * (0.04f + frac * 0.92f)
                    val path = Path().apply {
                        moveTo(-w * 0.05f, yStart)
                        cubicTo(
                            w * 0.30f, yStart - h * (0.18f - frac * 0.08f),
                            w * 0.65f, yEnd + h * (0.16f - frac * 0.06f),
                            w * 1.05f, yEnd
                        )
                    }
                    val toneAlpha = 0.05f + (1f - kotlin.math.abs(frac - 0.5f) * 2f) * 0.11f
                    drawPath(
                        path = path,
                        color = Color(0xFFECEFF4).copy(alpha = toneAlpha),
                        style = Stroke(width = baseStroke, cap = StrokeCap.Round)
                    )
                }
            }

            AppDrawerBackground.MONOLITH_GRID -> {
                drawRect(color = Color(0xFF050608))
                val facet1 = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w * 0.62f, 0f)
                    lineTo(w * 0.36f, h)
                    lineTo(0f, h)
                    close()
                }
                val facet2 = Path().apply {
                    moveTo(w * 0.62f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h * 0.58f)
                    lineTo(w * 0.36f, h)
                    close()
                }
                val facet3 = Path().apply {
                    moveTo(w, h * 0.58f)
                    lineTo(w, h)
                    lineTo(w * 0.36f, h)
                    close()
                }
                drawPath(facet1, color = Color(0xFF12151D))
                drawPath(facet2, color = Color(0xFF090B0F))
                drawPath(facet3, color = Color(0xFF252A35).copy(alpha = 0.65f))

                val lineStroke = if (isPreview) 0.7.dp.toPx() else 1.0.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.14f),
                    start = Offset(w * 0.62f, 0f),
                    end = Offset(w * 0.36f, h),
                    strokeWidth = lineStroke
                )
                drawLine(
                    color = Color(0xFFD8DEE9).copy(alpha = 0.12f),
                    start = Offset(w, h * 0.58f),
                    end = Offset(w * 0.36f, h),
                    strokeWidth = lineStroke
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, h * 0.32f),
                    end = Offset(w * 0.85f, h * 0.14f),
                    strokeWidth = lineStroke
                )
            }
        }
    }
}

fun appDrawerBackgroundLabel(preset: AppDrawerBackground, s: AppStrings): String = when (preset) {
    AppDrawerBackground.OBSIDIAN_PURE -> s.bgObsidian
    AppDrawerBackground.CARBON_WEAVE -> s.bgCarbon
    AppDrawerBackground.BRUSHED_TITANIUM -> s.bgTitanium
    AppDrawerBackground.TOPO_CONTOUR -> s.bgContour
    AppDrawerBackground.MONOLITH_GRID -> s.bgPrism
}

/**
 * Interactive slide-up bottom preset carousel rendered directly over the live App List screen
 * so the user immediately sees background changes in real time and taps outside to finish.
 */
@Composable
fun BoxScope.AppDrawerBackgroundPickerOverlay(
    visible: Boolean,
    selectedBackground: AppDrawerBackground,
    onSelectBackground: (AppDrawerBackground) -> Unit,
    onDismiss: () -> Unit
) {
    val s = appStrings()

    // 100% transparent outside-tap catcher so live background changes remain clearly visible
    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(150)),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xEB11141B))
                .border(0.8.dp, ObsidianColors.ObsidianSubBorder, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = s.backgroundTitle,
                        color = ObsidianColors.PureSilver,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "· ${appDrawerBackgroundLabel(selectedBackground, s)}",
                        color = ObsidianColors.MutedSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = s.tapOutsideToFinish,
                    color = ObsidianColors.DimSilver,
                    fontSize = 10.sp
                )
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(AppDrawerBackground.entries, key = { it.name }) { preset ->
                    val selected = preset == selectedBackground
                    Column(
                        modifier = Modifier
                            .width(112.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Color(0xFF1A202B) else Color(0xFF0A0C10))
                            .border(
                                width = if (selected) 1.4.dp else 0.8.dp,
                                color = if (selected) ObsidianColors.PureSilver else ObsidianColors.ObsidianSubBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectBackground(preset) }
                            .padding(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        AppDrawerBackgroundCanvas(
                            preset = preset,
                            isPreview = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .clip(RoundedCornerShape(7.dp))
                        )
                        Text(
                            text = appDrawerBackgroundLabel(preset, s),
                            color = if (selected) ObsidianColors.PureSilver else ObsidianColors.MutedSilver,
                            fontSize = 10.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * In-app Compose mirror of the Obsidian macOS Dock shown when Android's "Display over other apps"
 * permission has not been granted yet, keeping Home + Apps fixed while pinned shortcuts scroll independently.
 */
@Composable
fun BoxScope.InAppObsidianDock(
    settings: LauncherSettings,
    onGoHome: () -> Unit,
    onOpenApps: () -> Unit,
    onLaunchApp: (String) -> Unit
) {
    if (!settings.dockEnabled) return

    var transientEdge by remember(settings.dockDisplayMode) { mutableStateOf<DockPosition?>(null) }

    LaunchedEffect(transientEdge) {
        if (transientEdge != null) {
            delay(4500L)
            transientEdge = null
        }
    }

    if (settings.dockDisplayMode == DockDisplayMode.ALWAYS_VISIBLE) {
        val alignment = when (settings.dockPosition) {
            DockPosition.BOTTOM -> Alignment.BottomCenter
            DockPosition.TOP -> Alignment.TopCenter
            DockPosition.LEFT -> Alignment.CenterStart
            DockPosition.RIGHT -> Alignment.CenterEnd
        }
        Box(
            modifier = Modifier
                .align(alignment)
                .padding(10.dp)
        ) {
            ObsidianDockPill(
                position = settings.dockPosition,
                pinnedPackages = settings.pinnedPackages,
                onGoHome = onGoHome,
                onOpenApps = onOpenApps,
                onLaunchApp = onLaunchApp
            )
        }
    } else {
        // 4 subtle edge-swipe handles so user can swipe in from any edge
        DockPosition.entries.forEach { edge ->
            val align = when (edge) {
                DockPosition.BOTTOM -> Alignment.BottomCenter
                DockPosition.TOP -> Alignment.TopCenter
                DockPosition.LEFT -> Alignment.CenterStart
                DockPosition.RIGHT -> Alignment.CenterEnd
            }
            val isHoriz = edge == DockPosition.BOTTOM || edge == DockPosition.TOP
            Box(
                modifier = Modifier
                    .align(align)
                    .then(
                        if (isHoriz) Modifier.width(160.dp).height(16.dp)
                        else Modifier.width(16.dp).height(130.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { transientEdge = edge }
                    .pointerInput(edge) {
                        var triggered = false
                        var totalX = 0f
                        var totalY = 0f
                        detectDragGestures(
                            onDragStart = {
                                triggered = false
                                totalX = 0f
                                totalY = 0f
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            totalX += dragAmount.x
                            totalY += dragAmount.y
                            if (!triggered) {
                                val swipedIn = when (edge) {
                                    DockPosition.BOTTOM -> totalY <= -14f
                                    DockPosition.TOP -> totalY >= 14f
                                    DockPosition.LEFT -> totalX >= 14f
                                    DockPosition.RIGHT -> totalX <= -14f
                                }
                                if (swipedIn) {
                                    triggered = true
                                    transientEdge = edge
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (isHoriz) Modifier.width(42.dp).height(3.dp)
                            else Modifier.width(3.dp).height(34.dp)
                        )
                        .clip(CircleShape)
                        .background(ObsidianColors.ChampagneAccent.copy(alpha = 0.42f))
                )
            }
        }

        // Outside-tap catcher when transient dock is visible
        if (transientEdge != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { transientEdge = null }
            )
        }

        val activeEdge = transientEdge
        if (activeEdge != null) {
            val align = when (activeEdge) {
                DockPosition.BOTTOM -> Alignment.BottomCenter
                DockPosition.TOP -> Alignment.TopCenter
                DockPosition.LEFT -> Alignment.CenterStart
                DockPosition.RIGHT -> Alignment.CenterEnd
            }
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + when (activeEdge) {
                    DockPosition.BOTTOM -> slideInVertically { it / 2 }
                    DockPosition.TOP -> slideInVertically { -it / 2 }
                    DockPosition.LEFT -> slideInHorizontally { -it / 2 }
                    DockPosition.RIGHT -> slideInHorizontally { it / 2 }
                },
                exit = fadeOut() + when (activeEdge) {
                    DockPosition.BOTTOM -> slideOutVertically { it / 2 }
                    DockPosition.TOP -> slideOutVertically { -it / 2 }
                    DockPosition.LEFT -> slideOutHorizontally { -it / 2 }
                    DockPosition.RIGHT -> slideOutHorizontally { it / 2 }
                },
                modifier = Modifier
                    .align(align)
                    .padding(10.dp)
            ) {
                ObsidianDockPill(
                    position = activeEdge,
                    pinnedPackages = settings.pinnedPackages,
                    onGoHome = {
                        transientEdge = null
                        onGoHome()
                    },
                    onOpenApps = {
                        transientEdge = null
                        onOpenApps()
                    },
                    onLaunchApp = { pkg ->
                        transientEdge = null
                        onLaunchApp(pkg)
                    }
                )
            }
        }
    }
}

@Composable
private fun ObsidianDockPill(
    position: DockPosition,
    pinnedPackages: List<String>,
    onGoHome: () -> Unit,
    onOpenApps: () -> Unit,
    onLaunchApp: (String) -> Unit
) {
    val context = LocalContext.current
    val isHorizontal = position == DockPosition.BOTTOM || position == DockPosition.TOP
    val shape = RoundedCornerShape(18.dp)
    val pinned = pinnedPackages.take(LauncherSettings.MAX_PINNED_APPS)

    val containerMod = Modifier
        .clip(shape)
        .background(Color(0xEB0E1016))
        .border(1.dp, Color(0x444A5160), shape)

    if (isHorizontal) {
        Row(
            modifier = containerMod.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fixed Home + Apps icons
            DockFixedCoreButtons(onGoHome = onGoHome, onOpenApps = onOpenApps)

            if (pinned.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(ObsidianColors.MutedSilver.copy(alpha = 0.28f))
                )
                // Only the pinned list scrolls horizontally
                Row(
                    modifier = Modifier
                        .widthIn(max = 340.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pinned.forEach { pkg ->
                        DockPinnedAppTile(
                            context = context,
                            packageName = pkg,
                            onClick = { onLaunchApp(pkg) }
                        )
                    }
                }
            }
        }
    } else {
        Column(
            modifier = containerMod.padding(horizontal = 6.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Fixed Home + Apps icons
            DockFixedCoreButtons(onGoHome = onGoHome, onOpenApps = onOpenApps)

            if (pinned.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(1.dp)
                        .background(ObsidianColors.MutedSilver.copy(alpha = 0.28f))
                )
                // Only the pinned list scrolls vertically
                Column(
                    modifier = Modifier
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    pinned.forEach { pkg ->
                        DockPinnedAppTile(
                            context = context,
                            packageName = pkg,
                            onClick = { onLaunchApp(pkg) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockFixedCoreButtons(
    onGoHome: () -> Unit,
    onOpenApps: () -> Unit
) {
    DockVectorButton(onClick = onGoHome) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val p = Path().apply {
                moveTo(w * 0.14f, h * 0.48f)
                lineTo(w * 0.50f, h * 0.16f)
                lineTo(w * 0.86f, h * 0.48f)
                moveTo(w * 0.24f, h * 0.44f)
                lineTo(w * 0.24f, h * 0.84f)
                lineTo(w * 0.76f, h * 0.84f)
                lineTo(w * 0.76f, h * 0.44f)
            }
            drawPath(
                path = p,
                color = ObsidianColors.PureSilver,
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }

    DockVectorButton(onClick = onOpenApps) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val coords = listOf(0.20f, 0.50f, 0.80f)
            val cell = size.width * 0.18f
            val corner = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            coords.forEach { ry ->
                coords.forEach { rx ->
                    drawRoundRect(
                        color = ObsidianColors.ChampagneAccent,
                        topLeft = Offset(size.width * rx - cell * 0.5f, size.height * ry - cell * 0.5f),
                        size = Size(cell, cell),
                        cornerRadius = corner
                    )
                }
            }
        }
    }
}

@Composable
private fun DockPinnedAppTile(
    context: android.content.Context,
    packageName: String,
    onClick: () -> Unit
) {
    val bmp by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            InstalledAppsRepository.getAppIconBitmap(context, packageName, 128)?.asImageBitmap()
        }
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0x162C313B))
            .clickable { onClick() }
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp!!,
                contentDescription = packageName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ObsidianColors.ObsidianSubPanel)
            )
        }
    }
}

@Composable
private fun DockVectorButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0x1A2C313B))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}
