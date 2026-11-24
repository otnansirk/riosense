package id.bangsadigital.riosense.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bangsadigital.riosense.data.MapTileCache
import id.bangsadigital.riosense.data.MapTileKey
import id.bangsadigital.riosense.model.ActiveTripState
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
import kotlin.math.floor
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
                Text(
                    text = if (trip.routePoints.size >= 2) {
                        "${trip.routePoints.size} GPS pts"
                    } else {
                        if (isId) "OBD-II (tanpa GPS)" else "OBD-II (no GPS)"
                    },
                    color = ObsidianColors.MutedSilver,
                    fontSize = 9.5.sp
                )
            }

            StravaRouteCanvas(
                points = trip.routePoints,
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

private data class MercatorViewport(
    val zoom: Int,
    val tileSizePx: Float,
    val centerWorldX: Double,
    val centerWorldY: Double,
    val minTileX: Int,
    val maxTileX: Int,
    val minTileY: Int,
    val maxTileY: Int
)

private fun latLngToWorldPx(lat: Double, lng: Double, worldSize: Double): Pair<Double, Double> {
    val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
    val x = ((lng + 180.0) / 360.0) * worldSize
    val sinLat = sin(Math.toRadians(clampedLat))
    val y = (0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)) * worldSize
    return x to y
}

private fun computeMercatorViewport(
    points: List<TripRoutePoint>,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    padPx: Float
): MercatorViewport {
    val tileSize = 256.0
    val usableW = (canvasWidthPx - padPx * 2f).coerceAtLeast(32f).toDouble()
    val usableH = (canvasHeightPx - padPx * 2f).coerceAtLeast(32f).toDouble()

    var chosenZoom = 12
    for (z in 16 downTo 7) {
        val worldSize = tileSize * 2.0.pow(z)
        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (p in points) {
            val (wx, wy) = latLngToWorldPx(p.lat, p.lng, worldSize)
            if (wx < minX) minX = wx
            if (wx > maxX) maxX = wx
            if (wy < minY) minY = wy
            if (wy > maxY) maxY = wy
        }
        val spanX = maxX - minX
        val spanY = maxY - minY
        if (spanX <= usableW && spanY <= usableH) {
            chosenZoom = z
            break
        }
    }

    val worldSize = tileSize * 2.0.pow(chosenZoom)
    var minX = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE
    for (p in points) {
        val (wx, wy) = latLngToWorldPx(p.lat, p.lng, worldSize)
        if (wx < minX) minX = wx
        if (wx > maxX) maxX = wx
        if (wy < minY) minY = wy
        if (wy > maxY) maxY = wy
    }

    val centerX = (minX + maxX) * 0.5
    val centerY = (minY + maxY) * 0.5
    val maxTileIndex = (1 shl chosenZoom) - 1

    val leftWorld = centerX - canvasWidthPx * 0.5
    val rightWorld = centerX + canvasWidthPx * 0.5
    val topWorld = centerY - canvasHeightPx * 0.5
    val bottomWorld = centerY + canvasHeightPx * 0.5

    return MercatorViewport(
        zoom = chosenZoom,
        tileSizePx = tileSize.toFloat(),
        centerWorldX = centerX,
        centerWorldY = centerY,
        minTileX = floor(leftWorld / tileSize).toInt().coerceIn(0, maxTileIndex),
        maxTileX = floor(rightWorld / tileSize).toInt().coerceIn(0, maxTileIndex),
        minTileY = floor(topWorld / tileSize).toInt().coerceIn(0, maxTileIndex),
        maxTileY = floor(bottomWorld / tileSize).toInt().coerceIn(0, maxTileIndex)
    )
}

@Composable
private fun StravaRouteCanvas(
    points: List<TripRoutePoint>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val padPx = with(density) { 26.dp.toPx() }

        val viewport = remember(points, widthPx, heightPx) {
            if (points.size >= 2) {
                computeMercatorViewport(points, widthPx, heightPx, padPx)
            } else null
        }

        var loadedTiles by remember(viewport) { mutableStateOf<Map<MapTileKey, ImageBitmap>>(emptyMap()) }

        LaunchedEffect(viewport) {
            val vp = viewport ?: return@LaunchedEffect
            val keys = buildList {
                for (tx in vp.minTileX..vp.maxTileX) {
                    for (ty in vp.minTileY..vp.maxTileY) {
                        add(MapTileKey(vp.zoom, tx, ty))
                    }
                }
            }.take(12)

            val fetched = coroutineScope {
                keys.map { key ->
                    async { key to MapTileCache.loadTile(context, key) }
                }.awaitAll()
            }
            val map = HashMap<MapTileKey, ImageBitmap>(fetched.size)
            for ((k, bmp) in fetched) {
                if (bmp != null) map[k] = bmp
            }
            loadedTiles = map
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Subtle dark cockpit grid fallback (visible while loading or when offline & uncached)
            val gridColor = Color(0xFF151922)
            for (c in 1 until 6) {
                val x = (w / 6) * c
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 0.7.dp.toPx())
            }
            for (r in 1 until 4) {
                val y = (h / 4) * r
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 0.7.dp.toPx())
            }

            val vp = viewport ?: return@Canvas
            val tileSize = vp.tileSizePx
            val worldSize = tileSize.toDouble() * 2.0.pow(vp.zoom)

            // 1. Draw cached / live CartoDB Dark Matter road map tiles aligned in Web Mercator space
            for ((key, bmp) in loadedTiles) {
                val tileLeft = (key.x * tileSize - vp.centerWorldX + w * 0.5).roundToInt()
                val tileTop = (key.y * tileSize - vp.centerWorldY + h * 0.5).roundToInt()
                val drawSize = tileSize.roundToInt() + 1 // +1 avoids 1px seams between tiles
                drawImage(
                    image = bmp,
                    dstOffset = IntOffset(tileLeft, tileTop),
                    dstSize = IntSize(drawSize, drawSize)
                )
            }

            // Subtle dark vignette tint over tiles so the Strava route line pops cleanly
            if (loadedTiles.isNotEmpty()) {
                drawRect(color = Color(0x28090B0F))
            }

            // 2. Project route GPS points onto the exact same Web Mercator coordinates
            val projected = points.map { pt ->
                val (wx, wy) = latLngToWorldPx(pt.lat, pt.lng, worldSize)
                Offset(
                    x = (wx - vp.centerWorldX + w * 0.5).toFloat(),
                    y = (wy - vp.centerWorldY + h * 0.5).toFloat()
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
