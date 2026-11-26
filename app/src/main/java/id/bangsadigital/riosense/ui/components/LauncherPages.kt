package id.bangsadigital.riosense.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bangsadigital.riosense.launcher.InstalledAppsRepository
import id.bangsadigital.riosense.model.AppDrawerBackground
import id.bangsadigital.riosense.model.AppIconSize
import id.bangsadigital.riosense.model.DockDisplayMode
import id.bangsadigital.riosense.model.DockPosition
import id.bangsadigital.riosense.model.InstalledAppInfo
import id.bangsadigital.riosense.model.LauncherSettings
import id.bangsadigital.riosense.model.MaintenanceState
import id.bangsadigital.riosense.model.VehicleTelemetry
import id.bangsadigital.riosense.ui.i18n.AppStrings
import id.bangsadigital.riosense.ui.i18n.appStrings
import id.bangsadigital.riosense.ui.theme.ObsidianColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Page 0 (Swipe Right from Home): Obsidian Widgets Hub with live Clock/Date,
 * Vehicle Status summary, and Phase 2 widget slot preview.
 */
@Composable
fun LauncherWidgetsPage(
    telemetry: VehicleTelemetry,
    maintenance: MaintenanceState,
    onOpenControlsTab: (ControlsTab) -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = appStrings()
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            val millisIntoMinute = nowMs % 60_000L
            delay((60_000L - millisIntoMinute).coerceAtLeast(1000L))
        }
    }

    val timeStr = remember(nowMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowMs))
    }
    val dateStr = remember(nowMs) {
        SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date(nowMs))
    }
    val dueCount = remember(maintenance) { maintenance.dueOrOverdueItems().size }
    val odoText = remember(maintenance.currentOdometerKm) {
        NumberFormat.getIntegerInstance(Locale("id", "ID")).format(maintenance.currentOdometerKm)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header row
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

        // Main two-column widget grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Large Clock & Date + Phase 2 Widget Slots
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianColors.ObsidianPanel)
                        .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = timeStr,
                        color = ObsidianColors.PureSilver,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = dateStr,
                        color = ObsidianColors.MutedSilver,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianColors.ObsidianPanel)
                        .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = s.upcomingWidgetsLabel,
                        color = ObsidianColors.CobaltAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = s.upcomingWidgetsDesc,
                        color = ObsidianColors.MutedSilver,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // Right: Live Vehicle Telemetry & Quick Actions
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianColors.ObsidianPanel)
                    .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = s.vehicleSummaryTitle,
                    color = ObsidianColors.DimSilver,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WidgetStatCell(
                        label = "SPEED",
                        value = "${telemetry.speedKmh} km/h",
                        accent = ObsidianColors.PureSilver,
                        modifier = Modifier.weight(1f)
                    )
                    WidgetStatCell(
                        label = "RPM",
                        value = "${telemetry.rpm}",
                        accent = ObsidianColors.PureSilver,
                        modifier = Modifier.weight(1f)
                    )
                    WidgetStatCell(
                        label = s.coolant.uppercase(),
                        value = "${telemetry.coolantTempC}°C",
                        accent = if (telemetry.isCoolantOverheat) ObsidianColors.CrimsonAlert else ObsidianColors.EmeraldNominal,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WidgetStatCell(
                        label = s.battery.uppercase(),
                        value = String.format("%.1f V", telemetry.batteryVoltage),
                        accent = if (telemetry.isBatteryAlert) ObsidianColors.CrimsonAlert else ObsidianColors.PureSilver,
                        modifier = Modifier.weight(1f)
                    )
                    WidgetStatCell(
                        label = "ODO",
                        value = "$odoText km",
                        accent = ObsidianColors.PureSilver,
                        modifier = Modifier.weight(1f)
                    )
                    WidgetStatCell(
                        label = s.serviceTitle.uppercase(),
                        value = if (dueCount == 0) s.statusAllOk else s.statusDueSoon(dueCount),
                        accent = if (dueCount == 0) ObsidianColors.EmeraldNominal else ObsidianColors.AmberCaution,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick Jump Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        ControlsTab.SERVICE to s.serviceTitle,
                        ControlsTab.TRIP_HISTORY to s.tripHistoryTitle,
                        ControlsTab.SETTINGS to s.settingsTitle
                    ).forEach { (tab, label) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianColors.ObsidianSubPanel)
                                .border(1.dp, ObsidianColors.ObsidianSubBorder, RoundedCornerShape(8.dp))
                                .clickable { onOpenControlsTab(tab) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = ObsidianColors.PureSilver,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun WidgetStatCell(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianColors.ObsidianSubPanel)
            .border(1.dp, ObsidianColors.ObsidianSubBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            color = ObsidianColors.DimSilver,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp
        )
        Text(
            text = value,
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Stationary top header for the horizontally paged App Drawer (remains anchored when flipping
 * between App Drawer pages, while sliding smoothly during Home <-> Apps transitions).
 */
@Composable
fun LauncherAppDrawerHeader(
    totalAppCount: Int,
    appPageIndex: Int,
    totalAppPages: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = appStrings()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(0.33f)) {
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

        // Inline Search Box
        Box(
            modifier = Modifier
                .weight(0.45f)
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianColors.ObsidianPanel)
                .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (searchQuery.isEmpty()) {
                Text(
                    text = s.searchAppsPlaceholder,
                    color = ObsidianColors.DimSilver,
                    fontSize = 11.5.sp
                )
            }
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = ObsidianColors.PureSilver,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(ObsidianColors.CobaltAccent),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (searchQuery.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianColors.ObsidianPanel)
                    .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(8.dp))
                    .clickable { onSearchQueryChange("") }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(
                    text = s.clear,
                    color = ObsidianColors.MutedSilver,
                    fontSize = 10.sp
                )
            }
        }

        // Top-Right Action Icons: Gear Settings (left of Home) + Home Icon
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LauncherSettingsGearButton(onClick = onOpenSettings)
            LauncherHomeIconButton(onClick = onBackToHome)
        }
    }
}

/**
 * Page 2+ (Swipe Left from Home): Horizontally paged Android Head Unit App Drawer
 * with no vertical scrolling, dynamic page overflow to the right, configurable icon sizing,
 * and a tilted champagne-gold pushpin on pinned apps.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherAppDrawerPage(
    pageApps: List<InstalledAppInfo>,
    totalAppCount: Int,
    hasSearchQuery: Boolean,
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
                    text = if (totalAppCount == 0 && !hasSearchQuery) s.loadingAppsLabel else s.noAppsFoundLabel,
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

@Composable
private fun LauncherHomeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(ObsidianColors.ObsidianPanel)
            .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(9.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
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
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(ObsidianColors.ObsidianPanel)
            .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(9.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(15.dp)) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.5f
            val tint = ObsidianColors.PureSilver
            val stroke = 1.4.dp.toPx()
            val rRing = w * 0.30f
            val rInner = w * 0.13f
            val rToothIn = w * 0.34f
            val rToothOut = w * 0.46f

            for (i in 0 until 8) {
                val angle = i * (PI / 4.0)
                val cosA = cos(angle).toFloat()
                val sinA = sin(angle).toFloat()
                drawLine(
                    color = tint,
                    start = Offset(cx + cosA * rToothIn, cy + sinA * rToothIn),
                    end = Offset(cx + cosA * rToothOut, cy + sinA * rToothOut),
                    strokeWidth = 1.7.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            drawCircle(
                color = tint,
                radius = rRing,
                center = Offset(cx, cy),
                style = Stroke(width = stroke)
            )
            drawCircle(
                color = ObsidianColors.ObsidianPanel,
                radius = rInner,
                center = Offset(cx, cy)
            )
        }
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
