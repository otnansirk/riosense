package id.bangsadigital.riosense.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.bangsadigital.riosense.data.MapTileCache
import id.bangsadigital.riosense.data.MapTileKey
import id.bangsadigital.riosense.data.MapTileStyle
import id.bangsadigital.riosense.model.ActiveTripState
import id.bangsadigital.riosense.model.KiaRioPaintColor
import id.bangsadigital.riosense.model.TripRecord
import id.bangsadigital.riosense.model.TripRoutePoint
import id.bangsadigital.riosense.ui.i18n.AppLanguage
import id.bangsadigital.riosense.ui.i18n.LocalAppLanguage
import id.bangsadigital.riosense.ui.i18n.appStrings
import id.bangsadigital.riosense.ui.theme.ObsidianColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Strava-inspired minimalist Obsidian Trip History list & detail view.
 * Styled identically to Controls -> Settings: flat hairline-separated rows for the list,
 * and low-contrast Obsidian border containers strictly for grouped detail sections.
 */
@Composable
fun TripHistoryListView(
    trips: List<TripRecord>,
    onSelectTrip: (TripRecord) -> Unit,
    onLoadSampleTrips: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = appStrings()
    val isId = LocalAppLanguage.current == AppLanguage.ID
    val totalKm = trips.sumOf { it.distanceKm.toDouble() }.toFloat()

    Column(modifier = modifier.fillMaxWidth()) {
        // Minimalist summary bar (matching Settings top bar rhythm)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isId) {
                    "${trips.size} perjalanan  ·  ${String.format("%.1f", totalKm)} km total"
                } else {
                    "${trips.size} trips  ·  ${String.format("%.1f", totalKm)} km total"
                },
                color = ObsidianColors.DimSilver,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )
            if (trips.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ObsidianColors.CrimsonSurface)
                        .clickable { onClearAll() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = s.clear,
                        color = ObsidianColors.CrimsonAlert,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        TripHairlineDivider()

        if (trips.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = s.tripNoRecords,
                    color = ObsidianColors.DimSilver,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF161B24))
                        .clickable { onLoadSampleTrips() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = s.tripLoadSamplesBtn,
                        color = ObsidianColors.CobaltAccent,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(trips, key = { _, t -> t.id }) { idx, trip ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTrip(trip) }
                            .padding(vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mini Strava-style route silhouette
                        MiniRouteThumbnail(
                            points = trip.routePoints,
                            modifier = Modifier.size(width = 36.dp, height = 28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = trip.routeTitle,
                                color = ObsidianColors.PureSilver,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatTripDate(trip.startTimeMs)}  ·  ${formatDurationCompact(trip.durationSec, isId)}",
                                color = ObsidianColors.DimSilver,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${String.format("%.1f", trip.distanceKm)} km",
                                color = ObsidianColors.PureSilver,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            val eco = trip.fuelEconomyKmPerL
                            Text(
                                text = if (eco > 0f) "${String.format("%.1f", eco)} km/L" else "${trip.maxSpeedKmh} km/h max",
                                color = ObsidianColors.MutedSilver,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                    if (idx < trips.lastIndex) {
                        TripHairlineDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun TripHistoryDetailView(
    trip: TripRecord,
    paintColor: KiaRioPaintColor = KiaRioPaintColor.SNOW_WHITE_PEARL,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = appStrings()
    val isId = LocalAppLanguage.current == AppLanguage.ID

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header summary (route title + date range)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = trip.routeTitle,
                color = ObsidianColors.PureSilver,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${formatTripDate(trip.startTimeMs)} – ${formatTripTimeOnly(trip.endTimeMs)}",
                color = ObsidianColors.DimSilver,
                fontSize = 10.5.sp
            )
        }

        // 1. Strava-Style Route Trace Card (subtle low-contrast Obsidian container)
        var mapStyle by remember(trip.id) { mutableStateOf(MapTileStyle.ROAD) }
        var isMapFullScreen by remember(trip.id) { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianColors.ObsidianPanel)
                .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.tripRouteSection,
                    color = ObsidianColors.DimSilver,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                MapViewModeSelector(
                    selected = mapStyle,
                    onSelect = { mapStyle = it }
                )
            }

            StravaRouteCanvas(
                points = trip.routePoints,
                paintColor = paintColor,
                mapStyle = mapStyle,
                isFullScreen = false,
                onToggleFullScreen = { isMapFullScreen = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF090B0F))
            )

            // Subtle Start / Finish legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "● ${trip.startLabel.ifBlank { "Start" }}",
                    color = ObsidianColors.EmeraldNominal,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "● ${trip.endLabel.ifBlank { "Finish" }}",
                    color = ObsidianColors.CrimsonAlert,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isMapFullScreen) {
            Dialog(
                onDismissRequest = { isMapFullScreen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF090B0F))
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
                            .background(Color(0xFF090B0F))
                    ) {
                        StravaRouteCanvas(
                            points = trip.routePoints,
                            paintColor = paintColor,
                            mapStyle = mapStyle,
                            isFullScreen = true,
                            onToggleFullScreen = { isMapFullScreen = false },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Floating top-left title + icon view-mode selector inside full-screen map
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xCC0D1016))
                                .border(0.8.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = trip.routeTitle,
                                color = ObsidianColors.PureSilver,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            MapViewModeSelector(
                                selected = mapStyle,
                                onSelect = { mapStyle = it }
                            )
                        }
                    }
                }
            }
        }

        // 2. Grouped Driving Telemetry Container (matching Controls -> Settings container style)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianColors.ObsidianPanel)
                .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = s.tripTelemetrySection,
                color = ObsidianColors.DimSilver,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                TripMetricCell(
                    label = s.tripDistanceLabel,
                    value = "${String.format("%.1f", trip.distanceKm)} km",
                    modifier = Modifier.weight(1f)
                )
                TripMetricCell(
                    label = s.tripDurationLabel,
                    value = formatDurationCompact(trip.durationSec, isId),
                    modifier = Modifier.weight(1f)
                )
                TripMetricCell(
                    label = s.tripIdleLabel,
                    value = formatDurationCompact(trip.idleSec, isId),
                    modifier = Modifier.weight(1f)
                )
            }

            TripHairlineDivider()

            Row(modifier = Modifier.fillMaxWidth()) {
                TripMetricCell(
                    label = s.tripAvgSpeedLabel,
                    value = "${String.format("%.0f", trip.avgSpeedKmh)} km/h",
                    modifier = Modifier.weight(1f)
                )
                TripMetricCell(
                    label = s.tripMaxSpeedLabel,
                    value = "${trip.maxSpeedKmh} km/h",
                    modifier = Modifier.weight(1f)
                )
                val eco = trip.fuelEconomyKmPerL
                TripMetricCell(
                    label = s.tripFuelLabel,
                    value = if (eco > 0f) "${String.format("%.1f", eco)} km/L" else "${String.format("%.2f", trip.estimatedFuelLiters)} L",
                    valueColor = ObsidianColors.EmeraldNominal,
                    modifier = Modifier.weight(1f)
                )
            }

            TripHairlineDivider()

            Row(modifier = Modifier.fillMaxWidth()) {
                TripMetricCell(
                    label = s.tripMaxRpmLabel,
                    value = "${trip.maxRpm} RPM",
                    modifier = Modifier.weight(1f)
                )
                TripMetricCell(
                    label = s.tripMaxCoolantLabel,
                    value = "${trip.maxCoolantTempC} °C",
                    valueColor = if (trip.maxCoolantTempC >= 100) ObsidianColors.CrimsonAlert else ObsidianColors.PureSilver,
                    modifier = Modifier.weight(1f)
                )
                TripMetricCell(
                    label = s.tripMinBatteryLabel,
                    value = "${String.format("%.1f", trip.minBatteryV)} V",
                    valueColor = if (trip.minBatteryV <= 11.8f) ObsidianColors.AmberCaution else ObsidianColors.PureSilver,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Delete button (subtle premium crimson pill)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianColors.CrimsonSurface)
                .clickable { onDelete() }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = s.tripDeleteBtn,
                color = ObsidianColors.CrimsonAlert,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private data class InitialMapCamera(
    val zoom: Float,
    val centerNormX: Double,
    val centerNormY: Double,
    val minNormX: Double,
    val maxNormX: Double,
    val minNormY: Double,
    val maxNormY: Double
)

private fun latLngToNormMercator(lat: Double, lng: Double): Pair<Double, Double> {
    val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
    val x = (lng + 180.0) / 360.0
    val sinLat = sin(Math.toRadians(clampedLat))
    val y = 0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)
    return x to y
}

private fun computeInitialMapCamera(
    points: List<TripRoutePoint>,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    tileSizePx: Float,
    padPx: Float
): InitialMapCamera {
    val usableW = (canvasWidthPx - padPx * 2f).coerceAtLeast(32f).toDouble()
    val usableH = (canvasHeightPx - padPx * 2f).coerceAtLeast(32f).toDouble()

    var minNormX = Double.MAX_VALUE
    var maxNormX = -Double.MAX_VALUE
    var minNormY = Double.MAX_VALUE
    var maxNormY = -Double.MAX_VALUE
    for (p in points) {
        val (nx, ny) = latLngToNormMercator(p.lat, p.lng)
        if (nx < minNormX) minNormX = nx
        if (nx > maxNormX) maxNormX = nx
        if (ny < minNormY) minNormY = ny
        if (ny > maxNormY) maxNormY = ny
    }

    var chosenZoom = 12f
    for (z in 16 downTo 8) {
        val worldSize = tileSizePx.toDouble() * 2.0.pow(z)
        val spanX = (maxNormX - minNormX) * worldSize
        val spanY = (maxNormY - minNormY) * worldSize
        if (spanX <= usableW && spanY <= usableH) {
            chosenZoom = z.toFloat()
            break
        }
    }

    return InitialMapCamera(
        zoom = chosenZoom,
        centerNormX = (minNormX + maxNormX) * 0.5,
        centerNormY = (minNormY + maxNormY) * 0.5,
        minNormX = minNormX,
        maxNormX = maxNormX,
        minNormY = minNormY,
        maxNormY = maxNormY
    )
}

/**
 * Clamps map panning strictly to the route bounding box so dragging left/right/up/down
 * stops cleanly at the edges of the route line and snaps centered when fully zoomed out.
 */
private fun clampCameraCenter(
    camera: InitialMapCamera,
    zoom: Float,
    targetX: Double,
    targetY: Double
): Pair<Double, Double> {
    if (zoom <= camera.zoom + 0.02f) {
        return camera.centerNormX to camera.centerNormY
    }
    return targetX.coerceIn(camera.minNormX, camera.maxNormX) to
        targetY.coerceIn(camera.minNormY, camera.maxNormY)
}

private data class ReplaySegment(
    val startTimeSec: Float,
    val endTimeSec: Float,
    val startNormX: Double,
    val startNormY: Double,
    val endNormX: Double,
    val endNormY: Double,
    val startSpeedKmh: Int,
    val endSpeedKmh: Int,
    val bearingDeg: Float,
    val isStopped: Boolean
)

private data class ReplaySample(
    val normX: Double,
    val normY: Double,
    val speedKmh: Int,
    val bearingDeg: Float,
    val isStopped: Boolean,
    val isBraking: Boolean,
    val progress: Float
)

/**
 * Builds a speed-proportional time-compressed replay timeline (~9.5s total moving time)
 * while inserting realistic ~0.75s traffic-light pauses wherever recorded speed <= 2 km/h.
 */
private fun buildReplayTimeline(points: List<TripRoutePoint>): Pair<List<ReplaySegment>, Float> {
    if (points.size < 2) return emptyList<ReplaySegment>() to 0f

    val normPts = points.map { latLngToNormMercator(it.lat, it.lng) }
    val rawMoveWeights = FloatArray(points.size - 1)
    var totalMoveWeight = 0f

    for (i in 0 until points.lastIndex) {
        val dx = normPts[i + 1].first - normPts[i].first
        val dy = normPts[i + 1].second - normPts[i].second
        val dist = hypot(dx, dy).toFloat()
        if (dist > 1e-8f) {
            val avgSpeed = ((points[i].speedKmh + points[i + 1].speedKmh) * 0.5f).coerceAtLeast(10f)
            val weight = dist / avgSpeed
            rawMoveWeights[i] = weight
            totalMoveWeight += weight
        }
    }

    val targetMoveSec = 9.5f
    val stopPauseSec = 0.75f
    val segments = ArrayList<ReplaySegment>(points.size + 4)
    var cursorSec = 0f
    var lastBearingDeg = 0f

    for (i in 0 until points.lastIndex) {
        val p0 = normPts[i]
        val p1 = normPts[i + 1]
        val dx = p1.first - p0.first
        val dy = p1.second - p0.second
        val dist = hypot(dx, dy)
        if (dist > 1e-8) {
            // Heading in degrees where 0 deg points right (+X), matching Compose Canvas rotate()
            lastBearingDeg = Math.toDegrees(atan2(dy, dx)).toFloat()
        }

        // Insert brief traffic-light pause at a recorded 0-2 km/h point (except final endpoint)
        if (i > 0 && points[i].speedKmh <= 2) {
            segments.add(
                ReplaySegment(
                    startTimeSec = cursorSec,
                    endTimeSec = cursorSec + stopPauseSec,
                    startNormX = p0.first,
                    startNormY = p0.second,
                    endNormX = p0.first,
                    endNormY = p0.second,
                    startSpeedKmh = 0,
                    endSpeedKmh = 0,
                    bearingDeg = lastBearingDeg,
                    isStopped = true
                )
            )
            cursorSec += stopPauseSec
        }

        if (rawMoveWeights[i] > 0f && totalMoveWeight > 0f) {
            val segDur = (rawMoveWeights[i] / totalMoveWeight) * targetMoveSec
            val clampedDur = segDur.coerceAtLeast(0.08f)
            segments.add(
                ReplaySegment(
                    startTimeSec = cursorSec,
                    endTimeSec = cursorSec + clampedDur,
                    startNormX = p0.first,
                    startNormY = p0.second,
                    endNormX = p1.first,
                    endNormY = p1.second,
                    startSpeedKmh = points[i].speedKmh,
                    endSpeedKmh = points[i + 1].speedKmh,
                    bearingDeg = lastBearingDeg,
                    isStopped = false
                )
            )
            cursorSec += clampedDur
        }
    }

    return segments to cursorSec.coerceAtLeast(0.1f)
}

private fun sampleReplayAt(
    segments: List<ReplaySegment>,
    totalDurationSec: Float,
    elapsedSec: Float
): ReplaySample? {
    if (segments.isEmpty()) return null
    val t = elapsedSec.coerceIn(0f, totalDurationSec)
    val seg = segments.firstOrNull { t <= it.endTimeSec } ?: segments.last()
    val segSpan = (seg.endTimeSec - seg.startTimeSec).coerceAtLeast(1e-4f)
    val f = ((t - seg.startTimeSec) / segSpan).coerceIn(0f, 1f)

    val nx = seg.startNormX + (seg.endNormX - seg.startNormX) * f
    val ny = seg.startNormY + (seg.endNormY - seg.startNormY) * f
    val speed = if (seg.isStopped) 0 else (seg.startSpeedKmh + (seg.endSpeedKmh - seg.startSpeedKmh) * f).roundToInt()
    val isBraking = seg.isStopped || (seg.endSpeedKmh < seg.startSpeedKmh - 6)

    return ReplaySample(
        normX = nx,
        normY = ny,
        speedKmh = speed,
        bearingDeg = seg.bearingDeg,
        isStopped = seg.isStopped,
        isBraking = isBraking,
        progress = (t / totalDurationSec).coerceIn(0f, 1f)
    )
}

/**
 * Draws a crisp, lightweight top-down Kia Rio UB (2015) hatchback vector icon
 * oriented along the travel heading, matching the user's selected paint color.
 */
private fun DrawScope.drawMiniKiaRioTopDown(
    center: Offset,
    bearingDeg: Float,
    paintColor: KiaRioPaintColor,
    isBraking: Boolean
) {
    val carLen = 22.dp.toPx()
    val carWid = 11.dp.toPx()
    val halfL = carLen * 0.5f
    val halfW = carWid * 0.5f

    // Nose points right (+X) at 0 degrees
    rotate(degrees = bearingDeg, pivot = center) {
        // 1. Subtle warm-white LED headlight beam cone ahead of the nose
        val beamPath = Path().apply {
            moveTo(center.x + halfL - 1.dp.toPx(), center.y - halfW * 0.65f)
            lineTo(center.x + halfL + 11.dp.toPx(), center.y - halfW * 1.45f)
            lineTo(center.x + halfL + 11.dp.toPx(), center.y + halfW * 1.45f)
            lineTo(center.x + halfL - 1.dp.toPx(), center.y + halfW * 0.65f)
            close()
        }
        drawPath(beamPath, color = Color(0x28FFF6DC))

        // 2. Soft ambient shadow under the hatchback body
        drawRoundRect(
            color = Color(0x88000000),
            topLeft = Offset(center.x - halfL - 1.2.dp.toPx(), center.y - halfW - 0.8.dp.toPx()),
            size = Size(carLen + 2.4.dp.toPx(), carWid + 2.2.dp.toPx()),
            cornerRadius = CornerRadius(halfW * 0.85f, halfW * 0.85f)
        )

        // 3. Side mirrors (Kia Rio UB profile)
        val mirrorX = center.x + halfL * 0.14f
        drawRoundRect(
            color = paintColor.shadowHex,
            topLeft = Offset(mirrorX - 1.2.dp.toPx(), center.y - halfW - 1.4.dp.toPx()),
            size = Size(2.4.dp.toPx(), 1.8.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
        )
        drawRoundRect(
            color = paintColor.shadowHex,
            topLeft = Offset(mirrorX - 1.2.dp.toPx(), center.y + halfW - 0.4.dp.toPx()),
            size = Size(2.4.dp.toPx(), 1.8.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
        )

        // 4. Sculpted Kia Rio hatchback body + subtle rim outline
        drawRoundRect(
            color = paintColor.primaryHex,
            topLeft = Offset(center.x - halfL, center.y - halfW),
            size = Size(carLen, carWid),
            cornerRadius = CornerRadius(halfW * 0.72f, halfW * 0.72f)
        )
        drawRoundRect(
            color = paintColor.highlightHex.copy(alpha = 0.55f),
            topLeft = Offset(center.x - halfL, center.y - halfW),
            size = Size(carLen, carWid),
            cornerRadius = CornerRadius(halfW * 0.72f, halfW * 0.72f),
            style = Stroke(width = 0.75.dp.toPx())
        )

        // 5. Dark tinted cabin glass (windshield + hatchback rear glass)
        drawRoundRect(
            color = Color(0xFF141923),
            topLeft = Offset(center.x - halfL * 0.68f, center.y - halfW * 0.72f),
            size = Size(carLen * 0.66f, carWid * 0.72f),
            cornerRadius = CornerRadius(halfW * 0.35f, halfW * 0.35f)
        )

        // 6. Roof panel matching body paint
        drawRoundRect(
            color = paintColor.primaryHex.copy(alpha = 0.92f),
            topLeft = Offset(center.x - halfL * 0.44f, center.y - halfW * 0.56f),
            size = Size(carLen * 0.36f, carWid * 0.56f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )

        // 7. Crisp LED headlights at the nose
        val headX = center.x + halfL - 1.6.dp.toPx()
        drawCircle(Color(0xFFFFF8E1), radius = 1.15.dp.toPx(), center = Offset(headX, center.y - halfW * 0.56f))
        drawCircle(Color(0xFFFFF8E1), radius = 1.15.dp.toPx(), center = Offset(headX, center.y + halfW * 0.56f))

        // 8. Tail / brake lights at the rear hatchback (glows brighter when braking or stopped)
        val tailX = center.x - halfL + 1.1.dp.toPx()
        val brakeColor = if (isBraking) Color(0xFFFF3B30) else Color(0x99C62828)
        if (isBraking) {
            drawCircle(Color(0x66FF3B30), radius = 3.2.dp.toPx(), center = Offset(tailX, center.y - halfW * 0.55f))
            drawCircle(Color(0x66FF3B30), radius = 3.2.dp.toPx(), center = Offset(tailX, center.y + halfW * 0.55f))
        }
        drawCircle(brakeColor, radius = 1.1.dp.toPx(), center = Offset(tailX, center.y - halfW * 0.55f))
        drawCircle(brakeColor, radius = 1.1.dp.toPx(), center = Offset(tailX, center.y + halfW * 0.55f))
    }
}

@Composable
private fun StravaRouteCanvas(
    points: List<TripRoutePoint>,
    paintColor: KiaRioPaintColor,
    mapStyle: MapTileStyle,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val (replaySegments, replayDurationSec) = remember(points) {
        buildReplayTimeline(points)
    }
    var isReplaying by remember(points) { mutableStateOf(false) }
    var replayElapsedSec by remember(points) { mutableFloatStateOf(0f) }

    // Runs ONLY while Play is active (0% CPU when idle/paused)
    LaunchedEffect(isReplaying, replayDurationSec) {
        if (!isReplaying || replaySegments.isEmpty()) return@LaunchedEffect
        var lastFrameNs = 0L
        while (isReplaying) {
            withFrameNanos { nowNs ->
                if (lastFrameNs != 0L) {
                    val dtSec = ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.08f)
                    val next = replayElapsedSec + dtSec
                    if (next >= replayDurationSec) {
                        replayElapsedSec = replayDurationSec
                        isReplaying = false
                    } else {
                        replayElapsedSec = next
                    }
                }
                lastFrameNs = nowNs
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.clipToBounds()
    ) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val padPx = with(density) { (if (isFullScreen) 48.dp else 26.dp).toPx() }
        // Density-aware tile size guarantees full edge-to-edge coverage with minimal tile count
        val tileSizePx = with(density) { 196.dp.toPx() }.coerceIn(256f, 512f)

        val initialCamera = remember(points, widthPx, heightPx, tileSizePx) {
            if (points.size >= 2) {
                computeInitialMapCamera(points, widthPx, heightPx, tileSizePx, padPx)
            } else null
        }

        val minZoom = initialCamera?.zoom ?: 12f
        val maxZoom = 17f

        var cameraZoom by remember(initialCamera) {
            mutableFloatStateOf(minZoom)
        }
        var centerNormX by remember(initialCamera) {
            mutableDoubleStateOf(initialCamera?.centerNormX ?: 0.8065)
        }
        var centerNormY by remember(initialCamera) {
            mutableDoubleStateOf(initialCamera?.centerNormY ?: 0.5217)
        }

        val tileZoom = cameraZoom.roundToInt().coerceIn(8, 17)
        val worldSizeAtCamera = tileSizePx.toDouble() * 2.0.pow(cameraZoom.toDouble())
        val tileScale = 2.0.pow((cameraZoom - tileZoom).toDouble()).toFloat()
        val drawnTileSizePx = tileSizePx * tileScale
        val maxTileIndex = (1 shl tileZoom) - 1

        val centerWorldX = centerNormX * worldSizeAtCamera
        val centerWorldY = centerNormY * worldSizeAtCamera

        // Compute exact visible tile grid covering 100% of the wrapper viewport
        val minTileX = floor((centerWorldX - widthPx * 0.5) / drawnTileSizePx).toInt().coerceIn(0, maxTileIndex)
        val maxTileX = floor((centerWorldX + widthPx * 0.5) / drawnTileSizePx).toInt().coerceIn(0, maxTileIndex)
        val minTileY = floor((centerWorldY - heightPx * 0.5) / drawnTileSizePx).toInt().coerceIn(0, maxTileIndex)
        val maxTileY = floor((centerWorldY + heightPx * 0.5) / drawnTileSizePx).toInt().coerceIn(0, maxTileIndex)

        var loadedTiles by remember { mutableStateOf<Map<MapTileKey, ImageBitmap>>(emptyMap()) }

        LaunchedEffect(mapStyle, tileZoom, minTileX, maxTileX, minTileY, maxTileY) {
            if (initialCamera == null || mapStyle == MapTileStyle.GRID) return@LaunchedEffect
            val neededKeys = buildList {
                for (tx in minTileX..maxTileX) {
                    for (ty in minTileY..maxTileY) {
                        val key = MapTileKey(tileZoom, tx, ty, mapStyle)
                        if (!loadedTiles.containsKey(key)) {
                            add(key)
                        }
                    }
                }
            }.take(42)

            if (neededKeys.isEmpty()) return@LaunchedEffect

            val fetched = coroutineScope {
                neededKeys.map { key ->
                    async { key to MapTileCache.loadTile(context, key) }
                }.awaitAll()
            }
            val merged = LinkedHashMap<MapTileKey, ImageBitmap>(loadedTiles.size + fetched.size)
            // Keep tiles for current zoom & style to bound memory cleanly
            loadedTiles.forEach { (k, v) ->
                if (k.z == tileZoom && k.style == mapStyle) merged[k] = v
            }
            fetched.forEach { (k, bmp) ->
                if (bmp != null) merged[k] = bmp
            }
            loadedTiles = merged
        }

        val isTransformed = initialCamera != null && (
            kotlin.math.abs(cameraZoom - initialCamera.zoom) > 0.05f ||
                kotlin.math.abs(centerNormX - initialCamera.centerNormX) > 1e-6 ||
                kotlin.math.abs(centerNormY - initialCamera.centerNormY) > 1e-6
            )

        // LAYER 1: Static Map Tiles + Route Trace (never redrawn during replay frames)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(initialCamera, tileSizePx) {
                    val cam = initialCamera ?: return@pointerInput
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        val nextZoom = if (zoomChange != 1f) {
                            (cameraZoom + (ln(zoomChange.toDouble()) / ln(2.0)).toFloat())
                                .coerceIn(cam.zoom, maxZoom)
                        } else cameraZoom
                        val curWorld = tileSizePx.toDouble() * 2.0.pow(nextZoom.toDouble())
                        val (clampedX, clampedY) = clampCameraCenter(
                            camera = cam,
                            zoom = nextZoom,
                            targetX = centerNormX - pan.x / curWorld,
                            targetY = centerNormY - pan.y / curWorld
                        )
                        cameraZoom = nextZoom
                        centerNormX = clampedX
                        centerNormY = clampedY
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            // Subtle dark cockpit grid (always shown in GRID mode, and as instant fallback while tiles load)
            val gridColor = Color(0xFF151922)
            for (c in 1 until 6) {
                val x = (w / 6) * c
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 0.7.dp.toPx())
            }
            for (r in 1 until 4) {
                val y = (h / 4) * r
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 0.7.dp.toPx())
            }

            if (initialCamera == null) return@Canvas

            val curWorld = tileSizePx.toDouble() * 2.0.pow(cameraZoom.toDouble())
            val curCenterX = centerNormX * curWorld
            val curCenterY = centerNormY * curWorld

            // 1. Draw cached / live tiles when in ROAD or SATELLITE mode
            if (mapStyle != MapTileStyle.GRID) {
                var drewAnyTile = false
                for (tx in minTileX..maxTileX) {
                    for (ty in minTileY..maxTileY) {
                        val bmp = loadedTiles[MapTileKey(tileZoom, tx, ty, mapStyle)] ?: continue
                        drewAnyTile = true
                        val tileLeft = (tx * drawnTileSizePx - curCenterX + w * 0.5).roundToInt()
                        val tileTop = (ty * drawnTileSizePx - curCenterY + h * 0.5).roundToInt()
                        val drawSize = drawnTileSizePx.roundToInt() + 2
                        drawImage(
                            image = bmp,
                            dstOffset = IntOffset(tileLeft, tileTop),
                            dstSize = IntSize(drawSize, drawSize)
                        )
                    }
                }

                // Subtle dark vignette tint over tiles so the Strava route line pops cleanly
                if (drewAnyTile) {
                    drawRect(color = if (mapStyle == MapTileStyle.SATELLITE) Color(0x2E090B0F) else Color(0x24090B0F))
                }
            }

            // 2. Project route GPS points onto the exact same Web Mercator coordinates
            val projected = points.map { pt ->
                val (nx, ny) = latLngToNormMercator(pt.lat, pt.lng)
                Offset(
                    x = (nx * curWorld - curCenterX + w * 0.5).toFloat(),
                    y = (ny * curWorld - curCenterY + h * 0.5).toFloat()
                )
            }

            // Soft glowing under-stroke (Strava aesthetic)
            val fullPath = Path().apply {
                moveTo(projected.first().x, projected.first().y)
                for (i in 1 until projected.size) {
                    lineTo(projected[i].x, projected[i].y)
                }
            }
            drawPath(
                path = fullPath,
                color = ObsidianColors.CobaltAccent.copy(alpha = 0.28f),
                style = Stroke(width = 7.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Speed-tinted foreground route segments
            val strokeWidth = 2.8.dp.toPx()
            for (i in 0 until projected.lastIndex) {
                val spd = points[i].speedKmh
                val segColor = when {
                    spd >= 70 -> ObsidianColors.AmberCaution
                    spd >= 35 -> ObsidianColors.CobaltAccent
                    else -> ObsidianColors.EmeraldNominal
                }
                drawLine(
                    color = segColor,
                    start = projected[i],
                    end = projected[i + 1],
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            // Start & Finish glowing markers
            val start = projected.first()
            val end = projected.last()
            drawCircle(ObsidianColors.EmeraldNominal.copy(alpha = 0.30f), radius = 7.5.dp.toPx(), center = start)
            drawCircle(ObsidianColors.EmeraldNominal, radius = 3.8.dp.toPx(), center = start)
            drawCircle(ObsidianColors.CrimsonAlert.copy(alpha = 0.30f), radius = 7.5.dp.toPx(), center = end)
            drawCircle(ObsidianColors.CrimsonAlert, radius = 3.8.dp.toPx(), center = end)
        }

        // LAYER 2: Lightweight Replay Overlay (Mini Kia Rio 2015 + Live Speed + Thin Progress Line)
        val activeSample = if ((isReplaying || replayElapsedSec > 0f) && initialCamera != null) {
            sampleReplayAt(replaySegments, replayDurationSec, replayElapsedSec)
        } else null

        if (activeSample != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val curWorld = tileSizePx.toDouble() * 2.0.pow(cameraZoom.toDouble())
                val curCenterX = centerNormX * curWorld
                val curCenterY = centerNormY * curWorld
                val carPos = Offset(
                    x = (activeSample.normX * curWorld - curCenterX + size.width * 0.5).toFloat(),
                    y = (activeSample.normY * curWorld - curCenterY + size.height * 0.5).toFloat()
                )
                drawMiniKiaRioTopDown(
                    center = carPos,
                    bearingDeg = activeSample.bearingDeg,
                    paintColor = paintColor,
                    isBraking = activeSample.isBraking
                )
            }

            // Bottom-left Live Segment Speed Readout
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xCC0D1016))
                    .border(0.8.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(7.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                val speedColor = when {
                    activeSample.isStopped -> ObsidianColors.CrimsonAlert
                    activeSample.speedKmh >= 70 -> ObsidianColors.AmberCaution
                    activeSample.speedKmh >= 35 -> ObsidianColors.CobaltAccent
                    else -> ObsidianColors.EmeraldNominal
                }
                Text(
                    text = if (activeSample.isStopped) "0 km/h · STOP" else "${activeSample.speedKmh} km/h",
                    color = speedColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Subtle 2dp progress hairline along bottom edge of map
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(activeSample.progress.coerceIn(0.005f, 1f))
                    .height(2.dp)
                    .background(ObsidianColors.CobaltAccent.copy(alpha = 0.85f))
            )
        }

        // Top-right Full-Screen toggle button inside a matching Obsidian box
        if (initialCamera != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xCC0D1016))
                    .border(0.8.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(7.dp))
                    .clickable { onToggleFullScreen() }
                    .padding(7.dp),
                contentAlignment = Alignment.Center
            ) {
                FullScreenToggleIcon(
                    isFullScreen = isFullScreen,
                    tint = ObsidianColors.PureSilver,
                    modifier = Modifier.size(13.dp)
                )
            }

            // Bottom-right Play/Pause + Zoom & Recenter controls (clamped strictly to route bounds)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xCC0D1016))
                    .border(0.8.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(7.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (replaySegments.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clickable {
                                if (isReplaying) {
                                    isReplaying = false
                                } else {
                                    if (replayElapsedSec >= replayDurationSec - 0.02f) {
                                        replayElapsedSec = 0f
                                    }
                                    isReplaying = true
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ReplayPlayPauseIcon(
                            isPlaying = isReplaying,
                            tint = if (isReplaying) ObsidianColors.CobaltAccent else ObsidianColors.PureSilver,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(0.8.dp)
                            .height(16.dp)
                            .background(ObsidianColors.ObsidianBorder)
                    )
                }
                MapControlButton(
                    label = "−",
                    enabled = cameraZoom > minZoom + 0.02f
                ) {
                    val nextZoom = (cameraZoom - 1f).coerceIn(minZoom, maxZoom)
                    val (cx, cy) = clampCameraCenter(initialCamera, nextZoom, centerNormX, centerNormY)
                    cameraZoom = nextZoom
                    centerNormX = cx
                    centerNormY = cy
                }
                Box(
                    modifier = Modifier
                        .width(0.8.dp)
                        .height(16.dp)
                        .background(ObsidianColors.ObsidianBorder)
                )
                MapControlButton(
                    label = "+",
                    enabled = cameraZoom < maxZoom - 0.02f
                ) {
                    val nextZoom = (cameraZoom + 1f).coerceIn(minZoom, maxZoom)
                    val (cx, cy) = clampCameraCenter(initialCamera, nextZoom, centerNormX, centerNormY)
                    cameraZoom = nextZoom
                    centerNormX = cx
                    centerNormY = cy
                }
                if (isTransformed) {
                    Box(
                        modifier = Modifier
                            .width(0.8.dp)
                            .height(16.dp)
                            .background(ObsidianColors.ObsidianBorder)
                    )
                    MapControlButton(label = "⟲", enabled = true) {
                        cameraZoom = initialCamera.zoom
                        centerNormX = initialCamera.centerNormX
                        centerNormY = initialCamera.centerNormY
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplayPlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (isPlaying) {
            val barW = w * 0.28f
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.16f, h * 0.12f),
                size = Size(barW, h * 0.76f),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.56f, h * 0.12f),
                size = Size(barW, h * 0.76f),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
        } else {
            val playPath = Path().apply {
                moveTo(w * 0.20f, h * 0.12f)
                lineTo(w * 0.88f, h * 0.50f)
                lineTo(w * 0.20f, h * 0.88f)
                close()
            }
            drawPath(playPath, color = tint)
        }
    }
}

@Composable
private fun MapViewModeSelector(
    selected: MapTileStyle,
    onSelect: (MapTileStyle) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF0E1015))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MapTileStyle.entries.forEach { style ->
            val isSelected = style == selected
            val tint = if (isSelected) ObsidianColors.CobaltAccent else ObsidianColors.DimSilver
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (isSelected) Color(0xFF212836) else Color.Transparent)
                    .clickable { onSelect(style) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                when (style) {
                    MapTileStyle.GRID -> GridViewVectorIcon(tint = tint, modifier = Modifier.size(12.dp))
                    MapTileStyle.ROAD -> RoadLinesVectorIcon(tint = tint, modifier = Modifier.size(12.dp))
                    MapTileStyle.SATELLITE -> SatelliteViewVectorIcon(tint = tint, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun GridViewVectorIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.1.dp.toPx()
        // Outer subtle square + inner 2x2 grid lines
        drawLine(tint, Offset(w * 0.35f, h * 0.08f), Offset(w * 0.35f, h * 0.92f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.68f, h * 0.08f), Offset(w * 0.68f, h * 0.92f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.08f, h * 0.35f), Offset(w * 0.92f, h * 0.35f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.08f, h * 0.68f), Offset(w * 0.92f, h * 0.68f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun RoadLinesVectorIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.15.dp.toPx()
        // Minimalist folded map outline (3 panels representing road/street map)
        val path = Path().apply {
            moveTo(w * 0.10f, h * 0.22f)
            lineTo(w * 0.37f, h * 0.12f)
            lineTo(w * 0.63f, h * 0.22f)
            lineTo(w * 0.90f, h * 0.12f)
            lineTo(w * 0.90f, h * 0.78f)
            lineTo(w * 0.63f, h * 0.88f)
            lineTo(w * 0.37f, h * 0.78f)
            lineTo(w * 0.10f, h * 0.88f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
        drawLine(tint, Offset(w * 0.37f, h * 0.14f), Offset(w * 0.37f, h * 0.76f), stroke * 0.85f)
        drawLine(tint, Offset(w * 0.63f, h * 0.24f), Offset(w * 0.63f, h * 0.86f), stroke * 0.85f)
    }
}

@Composable
private fun SatelliteViewVectorIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.15.dp.toPx()
        val r = minOf(w, h) * 0.42f
        val c = Offset(w * 0.5f, h * 0.5f)
        // Minimalist globe icon representing real satellite Earth imagery
        drawCircle(color = tint, radius = r, center = c, style = Stroke(width = stroke))
        drawLine(tint, Offset(c.x - r, c.y), Offset(c.x + r, c.y), stroke * 0.9f)
        drawLine(tint, Offset(c.x, c.y - r), Offset(c.x, c.y + r), stroke * 0.9f)
        drawOval(
            color = tint,
            topLeft = Offset(c.x - r * 0.48f, c.y - r),
            size = androidx.compose.ui.geometry.Size(r * 0.96f, r * 2f),
            style = Stroke(width = stroke * 0.85f)
        )
    }
}

@Composable
private fun FullScreenToggleIcon(
    isFullScreen: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.3.dp.toPx()
        if (!isFullScreen) {
            // 4 corner expand brackets
            val arm = w * 0.28f
            // Top-left
            drawLine(tint, Offset(w * 0.12f, h * 0.12f), Offset(w * 0.12f + arm, h * 0.12f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.12f, h * 0.12f), Offset(w * 0.12f, h * 0.12f + arm), stroke, StrokeCap.Round)
            // Top-right
            drawLine(tint, Offset(w * 0.88f, h * 0.12f), Offset(w * 0.88f - arm, h * 0.12f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.88f, h * 0.12f), Offset(w * 0.88f, h * 0.12f + arm), stroke, StrokeCap.Round)
            // Bottom-left
            drawLine(tint, Offset(w * 0.12f, h * 0.88f), Offset(w * 0.12f + arm, h * 0.88f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.12f, h * 0.88f), Offset(w * 0.12f, h * 0.88f - arm), stroke, StrokeCap.Round)
            // Bottom-right
            drawLine(tint, Offset(w * 0.88f, h * 0.88f), Offset(w * 0.88f - arm, h * 0.88f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.88f, h * 0.88f), Offset(w * 0.88f, h * 0.88f - arm), stroke, StrokeCap.Round)
        } else {
            // Crisp close/exit X icon
            drawLine(tint, Offset(w * 0.20f, h * 0.20f), Offset(w * 0.80f, h * 0.80f), stroke, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.80f, h * 0.20f), Offset(w * 0.20f, h * 0.80f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun MapControlButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) ObsidianColors.PureSilver else ObsidianColors.DimSilver.copy(alpha = 0.4f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MiniRouteThumbnail(
    points: List<TripRoutePoint>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(ObsidianColors.ObsidianPanel)
            .border(0.8.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (points.size < 2) {
                drawCircle(ObsidianColors.DimSilver, radius = 2.dp.toPx(), center = center)
                return@Canvas
            }
            val projected = projectPointsToCanvas(points, size.width, size.height, padPx = 5.dp.toPx())
            val path = Path().apply {
                moveTo(projected.first().x, projected.first().y)
                for (i in 1 until projected.size) {
                    lineTo(projected[i].x, projected[i].y)
                }
            }
            drawPath(
                path = path,
                color = ObsidianColors.CobaltAccent.copy(alpha = 0.90f),
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawCircle(ObsidianColors.EmeraldNominal, radius = 1.8.dp.toPx(), center = projected.first())
            drawCircle(ObsidianColors.CrimsonAlert, radius = 1.8.dp.toPx(), center = projected.last())
        }
    }
}

private fun projectPointsToCanvas(
    points: List<TripRoutePoint>,
    width: Float,
    height: Float,
    padPx: Float
): List<Offset> {
    val minLat = points.minOf { it.lat }
    val maxLat = points.maxOf { it.lat }
    val minLng = points.minOf { it.lng }
    val maxLng = points.maxOf { it.lng }

    val spanLat = (maxLat - minLat).coerceAtLeast(1e-5)
    val spanLng = (maxLng - minLng).coerceAtLeast(1e-5)

    val drawW = (width - padPx * 2f).coerceAtLeast(1f)
    val drawH = (height - padPx * 2f).coerceAtLeast(1f)

    // Preserve geographic aspect ratio so routes never look stretched
    val scale = minOf(drawW / spanLng.toFloat(), drawH / spanLat.toFloat())
    val usedW = spanLng.toFloat() * scale
    val usedH = spanLat.toFloat() * scale
    val offsetX = padPx + (drawW - usedW) * 0.5f
    val offsetY = padPx + (drawH - usedH) * 0.5f

    return points.map { pt ->
        val x = offsetX + ((pt.lng - minLng).toFloat() * scale)
        // North is up (invert Y)
        val y = offsetY + ((maxLat - pt.lat).toFloat() * scale)
        Offset(x, y)
    }
}

@Composable
private fun TripMetricCell(
    label: String,
    value: String,
    valueColor: Color = ObsidianColors.PureSilver,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = ObsidianColors.DimSilver,
            fontSize = 9.5.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TripHairlineDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ObsidianColors.ObsidianHairline)
    )
}

private fun formatTripDate(timestampMs: Long): String {
    val sdf = SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault())
    return sdf.format(Date(timestampMs))
}

private fun formatTripTimeOnly(timestampMs: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestampMs))
}

private fun formatDurationCompact(sec: Long, isId: Boolean): String {
    val mins = (sec / 60L).toInt()
    val hrs = mins / 60
    val remMins = mins % 60
    return when {
        hrs > 0 -> if (isId) "${hrs}j ${remMins}m" else "${hrs}h ${remMins}m"
        mins > 0 -> if (isId) "$mins mnt" else "$mins min"
        else -> "${sec}s"
    }
}
