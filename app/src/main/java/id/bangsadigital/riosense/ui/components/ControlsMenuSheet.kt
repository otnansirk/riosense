package id.bangsadigital.riosense.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bangsadigital.riosense.R
import id.bangsadigital.riosense.launcher.InstalledAppsRepository
import id.bangsadigital.riosense.model.ActiveTripState
import id.bangsadigital.riosense.model.AppIconSize
import id.bangsadigital.riosense.model.DockDisplayMode
import id.bangsadigital.riosense.model.DockPosition
import id.bangsadigital.riosense.model.LauncherSettings
import id.bangsadigital.riosense.model.MaintenanceState
import id.bangsadigital.riosense.model.MaintenanceStatus
import id.bangsadigital.riosense.model.ObdActivityEntry
import id.bangsadigital.riosense.model.ObdDirection
import id.bangsadigital.riosense.model.ObdErrorEntry
import id.bangsadigital.riosense.model.ObdErrorSeverity
import id.bangsadigital.riosense.model.TopBarBrandLogo
import id.bangsadigital.riosense.model.TripRecord
import id.bangsadigital.riosense.model.VehicleTelemetry
import id.bangsadigital.riosense.model.VoiceButtonStyle
import id.bangsadigital.riosense.model.WelcomeSoundMode
import id.bangsadigital.riosense.ui.i18n.AppLanguage
import id.bangsadigital.riosense.ui.i18n.appStrings
import id.bangsadigital.riosense.ui.theme.ObsidianColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class ControlsTab(val label: String, val subtitle: String) {
    ERROR_LOG("Error Log", "OBD-II detected faults & DTC history"),
    ACTIVITY_LOG("Activity Log", "Real-time OBD-II TX/RX stream & PIDs"),
    SERVICE("Service", "Fast-moving parts & maintenance reminders"),
    TRIP_HISTORY("Trip History", "GPS route trace, distance & OBD driving stats"),
    SETTINGS("Settings", "Custom alarm thresholds & standard reset")
}

@Composable
fun ControlsMenuSheet(
    telemetry: VehicleTelemetry,
    maintenance: MaintenanceState,
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onWelcomeSoundModeChange: (WelcomeSoundMode) -> Unit,
    onEngineOffVoiceReminderChange: (Boolean) -> Unit,
    onVoiceButtonStyleChange: (VoiceButtonStyle) -> Unit,
    onTripRecordingEnabledChange: (Boolean) -> Unit,
    onTopBarBrandLogoChange: (TopBarBrandLogo) -> Unit,
    onRoadEffectEnabledChange: (Boolean) -> Unit,
    launcherSettings: LauncherSettings,
    hasOverlayPermission: Boolean,
    onDockEnabledChange: (Boolean) -> Unit,
    onDockDisplayModeChange: (DockDisplayMode) -> Unit,
    onDockPositionChange: (DockPosition) -> Unit,
    onAppIconSizeChange: (AppIconSize) -> Unit,
    onOpenBackgroundPicker: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenHomeLauncherSettings: () -> Unit,
    initialTab: ControlsTab? = null,
    tripHistory: List<TripRecord>,
    activeTrip: ActiveTripState?,
    onDeleteTrip: (String) -> Unit,
    onLoadSampleTrips: () -> Unit,
    onClearTripHistory: () -> Unit,
    errorLogs: List<ObdErrorEntry>,
    activityLogs: List<ObdActivityEntry>,
    isActivityPaused: Boolean,
    onToggleActivityPause: () -> Unit,
    onClearErrorLogs: () -> Unit,
    onClearActivityLogs: () -> Unit,
    onUpdateOdometer: (Int) -> Unit,
    onRemindDaysChange: (Int) -> Unit,
    onRemindKmChange: (Int) -> Unit,
    onLogServiceRecord: (itemId: String, dateMs: Long, odometerKm: Int, costIdr: Long, note: String) -> Unit,
    onDeleteHistoryRecord: (itemId: String, recordId: String) -> Unit,
    onUpdateServiceSchedule: (String, Int, Int) -> Unit,
    onAddCustomServiceItem: (name: String, months: Int, km: Int, dateMs: Long, odoKm: Int, costIdr: Long, note: String) -> Unit,
    onDeleteCustomServiceItem: (String) -> Unit,
    onResetMaintenanceToStandard: () -> Unit,
    onSpeedLockThresholdChange: (Int) -> Unit,
    onTpmsLowPsiChange: (Float) -> Unit,
    onTpmsHighPsiChange: (Float) -> Unit,
    onTpmsHighTempChange: (Int) -> Unit,
    onCoolantOverheatChange: (Int) -> Unit,
    onBatteryLowChange: (Float) -> Unit,
    onBatteryHighChange: (Float) -> Unit,
    onResetSettingsToStandard: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSection by remember(initialTab) { mutableStateOf(initialTab) }
    var selectedError by remember { mutableStateOf<ObdErrorEntry?>(null) }
    var selectedActivity by remember { mutableStateOf<ObdActivityEntry?>(null) }
    var selectedTrip by remember { mutableStateOf<TripRecord?>(null) }
    var selectedServiceId by remember { mutableStateOf<String?>(null) }
    var showAddServiceForm by remember { mutableStateOf(false) }
    var showRecordServiceForm by remember { mutableStateOf(false) }

    val dueServiceItems = remember(maintenance) { maintenance.dueOrOverdueItems() }
    val hasOverdueService = dueServiceItems.any {
        it.evaluateStatus(
            System.currentTimeMillis(),
            maintenance.currentOdometerKm,
            maintenance.remindDaysBefore,
            maintenance.remindKmBefore
        ) == MaintenanceStatus.OVERDUE
    }

    val s = appStrings()

    val headerTitle = when {
        activeSection == ControlsTab.ERROR_LOG && selectedError != null -> s.errorDetailTitle
        activeSection == ControlsTab.ACTIVITY_LOG && selectedActivity != null -> s.activityDetailTitle
        activeSection == ControlsTab.TRIP_HISTORY && selectedTrip != null -> s.tripDetailTitle
        activeSection == ControlsTab.TRIP_HISTORY -> s.tripHistoryTitle
        activeSection == ControlsTab.SERVICE && showAddServiceForm -> s.newPartTitle
        activeSection == ControlsTab.SERVICE && showRecordServiceForm -> s.recordServiceTitle
        activeSection == ControlsTab.SERVICE && selectedServiceId != null -> s.serviceDetailTitle
        activeSection == ControlsTab.SERVICE -> s.serviceTitle
        activeSection == ControlsTab.SETTINGS -> s.settingsTitle
        activeSection == ControlsTab.ERROR_LOG -> s.errorLogTitle
        activeSection == ControlsTab.ACTIVITY_LOG -> s.activityLogTitle
        else -> s.controlsTitle
    }

    val headerSubtitle = when {
        activeSection == ControlsTab.ERROR_LOG && selectedError != null -> selectedError!!.code
        activeSection == ControlsTab.ACTIVITY_LOG && selectedActivity != null -> "PID ${selectedActivity!!.pid}"
        activeSection == ControlsTab.TRIP_HISTORY && selectedTrip != null -> s.tripDetailSubtitle
        activeSection == ControlsTab.TRIP_HISTORY -> s.tripHistorySubtitle
        activeSection == ControlsTab.SERVICE && showAddServiceForm -> s.newPartSubtitle
        activeSection == ControlsTab.SERVICE && showRecordServiceForm -> s.recordServiceSubtitle
        activeSection == ControlsTab.SERVICE && selectedServiceId != null -> s.serviceDetailSubtitle
        activeSection == ControlsTab.SERVICE -> s.serviceSubtitle
        activeSection == ControlsTab.SETTINGS -> s.settingsSubtitle
        activeSection == ControlsTab.ERROR_LOG -> s.errorLogSubtitle
        activeSection == ControlsTab.ACTIVITY_LOG -> s.activityLogSubtitle
        else -> s.controlsSubtitle
    }

    fun navigateBackOneLevel() {
        when {
            activeSection == ControlsTab.ERROR_LOG && selectedError != null -> selectedError = null
            activeSection == ControlsTab.ACTIVITY_LOG && selectedActivity != null -> selectedActivity = null
            activeSection == ControlsTab.TRIP_HISTORY && selectedTrip != null -> selectedTrip = null
            activeSection == ControlsTab.SERVICE && showAddServiceForm -> showAddServiceForm = false
            activeSection == ControlsTab.SERVICE && showRecordServiceForm -> showRecordServiceForm = false
            activeSection == ControlsTab.SERVICE && selectedServiceId != null -> selectedServiceId = null
            activeSection != null -> {
                activeSection = null
                selectedError = null
                selectedActivity = null
                selectedTrip = null
                selectedServiceId = null
                showAddServiceForm = false
                showRecordServiceForm = false
            }
            else -> onClose()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth(0.58f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0E1015))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header: Top-Left Back Arrow (steps back 1 level at a time) + Dynamic Section Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181B22))
                    .clickable { navigateBackOneLevel() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    val w = size.width
                    val h = size.height
                    val cy = h * 0.5f
                    val leftX = w * 0.16f
                    val rightX = w * 0.84f
                    val headX = w * 0.46f
                    val stroke = 1.6.dp.toPx()
                    drawLine(
                        color = ObsidianColors.PureSilver,
                        start = Offset(rightX, cy),
                        end = Offset(leftX, cy),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = ObsidianColors.PureSilver,
                        start = Offset(headX, h * 0.22f),
                        end = Offset(leftX, cy),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = ObsidianColors.PureSilver,
                        start = Offset(headX, h * 0.78f),
                        end = Offset(leftX, cy),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headerTitle,
                    color = ObsidianColors.PureSilver,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = headerSubtitle,
                    color = ObsidianColors.DimSilver,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (activeSection == null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181B22))
                        .clickable { activeSection = ControlsTab.SETTINGS },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(15.dp)) {
                        val cx = size.width * 0.5f
                        val cy = size.height * 0.5f
                        val rOuter = size.minDimension * 0.44f
                        val rRing = size.minDimension * 0.30f
                        val rInner = size.minDimension * 0.13f
                        val stroke = 1.45.dp.toPx()
                        val tint = if (telemetry.isCustomAlertSettings) {
                            ObsidianColors.CobaltAccent
                        } else {
                            ObsidianColors.PureSilver
                        }

                        // 8 gear teeth radiating from the outer ring
                        for (i in 0 until 8) {
                            val angle = (i * 45.0) * (PI / 180.0)
                            val cosA = cos(angle).toFloat()
                            val sinA = sin(angle).toFloat()
                            drawLine(
                                color = tint,
                                start = Offset(cx + cosA * (rRing * 0.92f), cy + sinA * (rRing * 0.92f)),
                                end = Offset(cx + cosA * rOuter, cy + sinA * rOuter),
                                strokeWidth = 1.7.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        // Main gear body ring & center hub
                        drawCircle(
                            color = tint,
                            radius = rRing,
                            center = Offset(cx, cy),
                            style = Stroke(width = stroke)
                        )
                        drawCircle(
                            color = Color(0xFF181B22),
                            radius = rInner,
                            center = Offset(cx, cy)
                        )
                    }
                }
            }
        }

        // Content Body: Clean Flat Menu List OR Full-Sheet Active Section
        when (activeSection) {
            null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    ControlsMenuRowItem(
                        title = s.serviceTitle,
                        subtitle = s.serviceSubtitle,
                        statusText = when {
                            hasOverdueService -> s.statusOverdue(dueServiceItems.size)
                            dueServiceItems.isNotEmpty() -> s.statusDueSoon(dueServiceItems.size)
                            else -> s.statusAllOk
                        },
                        statusColor = when {
                            hasOverdueService -> ObsidianColors.CrimsonAlert
                            dueServiceItems.isNotEmpty() -> ObsidianColors.AmberCaution
                            else -> ObsidianColors.DimSilver
                        },
                        showDivider = true,
                        onClick = { activeSection = ControlsTab.SERVICE }
                    )

                    ControlsMenuRowItem(
                        title = s.tripHistoryTitle,
                        subtitle = s.tripHistorySubtitle,
                        statusText = if (activeTrip != null) {
                            s.tripRecordingStatus(String.format("%.1f", activeTrip.distanceKm))
                        } else {
                            s.tripCountStatus(tripHistory.size)
                        },
                        statusColor = if (activeTrip != null) ObsidianColors.EmeraldNominal else ObsidianColors.DimSilver,
                        showDivider = true,
                        onClick = { activeSection = ControlsTab.TRIP_HISTORY }
                    )

                    ControlsMenuRowItem(
                        title = s.errorLogTitle,
                        subtitle = s.errorLogSubtitle,
                        statusText = if (errorLogs.isNotEmpty()) s.statusDetected(errorLogs.size) else s.statusClean,
                        statusColor = if (errorLogs.isNotEmpty()) ObsidianColors.CrimsonAlert else ObsidianColors.DimSilver,
                        showDivider = true,
                        onClick = { activeSection = ControlsTab.ERROR_LOG }
                    )

                    ControlsMenuRowItem(
                        title = s.activityLogTitle,
                        subtitle = s.activityLogSubtitle,
                        statusText = if (isActivityPaused) s.statusPaused else s.statusLive(activityLogs.size),
                        statusColor = if (isActivityPaused) ObsidianColors.DimSilver else ObsidianColors.EmeraldNominal,
                        showDivider = false,
                        onClick = { activeSection = ControlsTab.ACTIVITY_LOG }
                    )
                }
            }

            ControlsTab.TRIP_HISTORY -> {
                val detail = selectedTrip
                if (detail != null) {
                    TripHistoryDetailView(
                        trip = detail,
                        paintColor = telemetry.paintColor,
                        onDelete = {
                            onDeleteTrip(detail.id)
                            selectedTrip = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    TripHistoryListView(
                        trips = tripHistory,
                        onSelectTrip = { selectedTrip = it },
                        onLoadSampleTrips = onLoadSampleTrips,
                        onClearAll = onClearTripHistory,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            ControlsTab.ERROR_LOG -> {
                val detail = selectedError
                if (detail != null) {
                    ErrorLogDetailView(
                        entry = detail,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ErrorLogListView(
                        entries = errorLogs,
                        onSelect = { selectedError = it },
                        onClear = onClearErrorLogs,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            ControlsTab.ACTIVITY_LOG -> {
                val detail = selectedActivity
                if (detail != null) {
                    ActivityLogDetailView(
                        entry = detail,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ActivityLogListView(
                        entries = activityLogs,
                        isPaused = isActivityPaused,
                        onTogglePause = onToggleActivityPause,
                        onSelect = { selectedActivity = it },
                        onClear = onClearActivityLogs,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            ControlsTab.SERVICE -> {
                MaintenanceServiceTabContent(
                    maintenance = maintenance,
                    selectedItemId = selectedServiceId,
                    showAddForm = showAddServiceForm,
                    showRecordForm = showRecordServiceForm,
                    onShowAddFormChange = { showAddServiceForm = it },
                    onShowRecordFormChange = { showRecordServiceForm = it },
                    onSelectItem = { selectedServiceId = it },
                    onUpdateOdometer = onUpdateOdometer,
                    onRemindDaysChange = onRemindDaysChange,
                    onRemindKmChange = onRemindKmChange,
                    onLogServiceRecord = onLogServiceRecord,
                    onDeleteHistoryRecord = onDeleteHistoryRecord,
                    onUpdateSchedule = onUpdateServiceSchedule,
                    onAddCustomItem = onAddCustomServiceItem,
                    onDeleteCustomItem = onDeleteCustomServiceItem,
                    onResetStandard = onResetMaintenanceToStandard,
                    modifier = Modifier.weight(1f)
                )
            }

            ControlsTab.SETTINGS -> {
                AlarmSettingsView(
                    telemetry = telemetry,
                    currentLanguage = currentLanguage,
                    onLanguageChange = onLanguageChange,
                    onWelcomeSoundModeChange = onWelcomeSoundModeChange,
                    onEngineOffVoiceReminderChange = onEngineOffVoiceReminderChange,
                    onVoiceButtonStyleChange = onVoiceButtonStyleChange,
                    onTripRecordingEnabledChange = onTripRecordingEnabledChange,
                    onTopBarBrandLogoChange = onTopBarBrandLogoChange,
                    onRoadEffectEnabledChange = onRoadEffectEnabledChange,
                    launcherSettings = launcherSettings,
                    hasOverlayPermission = hasOverlayPermission,
                    onDockEnabledChange = onDockEnabledChange,
                    onDockDisplayModeChange = onDockDisplayModeChange,
                    onDockPositionChange = onDockPositionChange,
                    onAppIconSizeChange = onAppIconSizeChange,
                    onOpenBackgroundPicker = onOpenBackgroundPicker,
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onOpenHomeLauncherSettings = onOpenHomeLauncherSettings,
                    onSpeedLockThresholdChange = onSpeedLockThresholdChange,
                    onTpmsLowPsiChange = onTpmsLowPsiChange,
                    onTpmsHighPsiChange = onTpmsHighPsiChange,
                    onTpmsHighTempChange = onTpmsHighTempChange,
                    onCoolantOverheatChange = onCoolantOverheatChange,
                    onBatteryLowChange = onBatteryLowChange,
                    onBatteryHighChange = onBatteryHighChange,
                    onResetToStandard = onResetSettingsToStandard,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ControlsMenuRowItem(
    title: String,
    subtitle: String,
    statusText: String,
    statusColor: Color,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = ObsidianColors.PureSilver,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    color = ObsidianColors.DimSilver,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "›",
                    color = ObsidianColors.DimSilver,
                    fontSize = 16.sp
                )
            }
        }

        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.7.dp)
                    .background(Color(0xFF1A1E27))
            )
        }
    }
}

@Composable
private fun ErrorLogListView(
    entries: List<ObdErrorEntry>,
    onSelect: (ObdErrorEntry) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = appStrings()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (entries.isEmpty()) s.noErrorsDetected else s.detectedTapDetails(entries.size),
                color = ObsidianColors.DimSilver,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )
            if (entries.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ObsidianColors.CrimsonSurface)
                        .clickable { onClear() }
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
        SettingsHairlineDivider()

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s.allModulesClean,
                    color = ObsidianColors.DimSilver,
                    fontSize = 11.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(entries, key = { _, item -> item.id }) { index, item ->
                    val accentColor = severityColor(item.severity)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = item.code,
                                    color = accentColor,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = item.title,
                                    color = ObsidianColors.PureSilver,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatDateTimeCompact(item.timestampMs),
                                color = ObsidianColors.DimSilver,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            text = "›",
                            color = ObsidianColors.DimSilver,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    if (index < entries.lastIndex) {
                        SettingsHairlineDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorLogDetailView(
    entry: ObdErrorEntry,
    modifier: Modifier = Modifier
) {
    val accent = severityColor(entry.severity)
    val s = appStrings()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Metadata Group (ObsidianPanel + 1dp ObsidianBorder)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianColors.ObsidianPanel)
                .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = entry.code,
                        color = accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = entry.title,
                        color = ObsidianColors.PureSilver,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = entry.severity.label.uppercase(),
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            SettingsHairlineDivider()
            DetailMetaRow(label = s.timestamp, value = formatDateTimeFull(entry.timestampMs), mono = true)
            SettingsHairlineDivider()
            DetailMetaRow(label = s.ecuModule, value = entry.subsystem)
            SettingsHairlineDivider()
            DetailMetaRow(label = s.servicePid, value = entry.pid, mono = true)
            SettingsHairlineDivider()
            DetailMetaRow(label = s.rawFrame, value = entry.rawFrame, mono = true)
            SettingsHairlineDivider()
            DetailMetaRow(label = s.source, value = entry.source)
        }

        // 2. Description & Recommended Action Group
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianColors.ObsidianPanel)
                .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = s.description,
                color = ObsidianColors.DimSilver,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = entry.description,
                color = ObsidianColors.PureSilver,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(2.dp))
            SettingsHairlineDivider()
            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = s.recommendedAction,
                color = ObsidianColors.DimSilver,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = entry.recommendedAction,
                color = ObsidianColors.MutedSilver,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ActivityLogListView(
    entries: List<ObdActivityEntry>,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onSelect: (ObdActivityEntry) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = appStrings()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isPaused) ObsidianColors.DimSilver else ObsidianColors.EmeraldNominal)
                )
                Text(
                    text = if (isPaused) s.streamPausedTap else s.liveStreamTap,
                    color = ObsidianColors.DimSilver,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPaused) ObsidianColors.CobaltSurface else ObsidianColors.ObsidianSubPanel)
                        .clickable { onTogglePause() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isPaused) s.resume else s.pause,
                        color = if (isPaused) ObsidianColors.CobaltAccent else ObsidianColors.PureSilver,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (entries.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ObsidianColors.CrimsonSurface)
                            .clickable { onClear() }
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
        }
        SettingsHairlineDivider()

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s.waitingFrames,
                    color = ObsidianColors.DimSilver,
                    fontSize = 11.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(entries, key = { _, item -> item.id }) { index, item ->
                    val isTx = item.direction == ObdDirection.TX
                    val dirColor = if (isTx) ObsidianColors.CobaltAccent else ObsidianColors.EmeraldNominal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(dirColor.copy(alpha = 0.14f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.direction.tag,
                                    color = dirColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = item.pid,
                                        color = ObsidianColors.PureSilver,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = item.quickValue,
                                        color = ObsidianColors.MutedSilver,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatDateTimeCompact(item.timestampMs),
                                    color = ObsidianColors.DimSilver,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Text(
                            text = "›",
                            color = ObsidianColors.DimSilver,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    if (index < entries.lastIndex) {
                        SettingsHairlineDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityLogDetailView(
    entry: ObdActivityEntry,
    modifier: Modifier = Modifier
) {
    val isTx = entry.direction == ObdDirection.TX
    val dirColor = if (isTx) ObsidianColors.CobaltAccent else ObsidianColors.EmeraldNominal
    val sDetail = appStrings()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Frame Metadata Group (ObsidianPanel + 1dp ObsidianBorder)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianColors.ObsidianPanel)
                .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "PID ${entry.pid}",
                        color = dirColor,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = entry.parameterName,
                        color = ObsidianColors.PureSilver,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (isTx) sDetail.txSentToEcu else sDetail.rxReceivedFromEcu,
                    color = dirColor,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            SettingsHairlineDivider()
            DetailMetaRow(label = sDetail.timestamp, value = formatDateTimeFull(entry.timestampMs), mono = true)
            SettingsHairlineDivider()
            DetailMetaRow(label = sDetail.quickValue, value = entry.quickValue)
            SettingsHairlineDivider()
            DetailMetaRow(label = sDetail.ecuHeader, value = entry.ecuHeader, mono = true)
            SettingsHairlineDivider()
            DetailMetaRow(label = sDetail.rawFrame, value = entry.rawFrame, mono = true)
            SettingsHairlineDivider()
            DetailMetaRow(label = sDetail.formula, value = entry.formula, mono = true)
            SettingsHairlineDivider()
            DetailMetaRow(label = sDetail.source, value = entry.source)
        }

        // 2. Decoded Detail Group
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianColors.ObsidianPanel)
                .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = sDetail.decodedData,
                color = ObsidianColors.DimSilver,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = entry.decodedDetail,
                color = ObsidianColors.PureSilver,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun DetailMetaRow(
    label: String,
    value: String,
    mono: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = ObsidianColors.DimSilver,
            fontSize = 10.sp
        )
        Text(
            text = value,
            color = ObsidianColors.PureSilver,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        )
    }
}

private fun severityColor(severity: ObdErrorSeverity): Color = when (severity) {
    ObdErrorSeverity.CRITICAL -> ObsidianColors.CrimsonAlert
    ObdErrorSeverity.WARNING -> ObsidianColors.AmberCaution
    ObdErrorSeverity.INFO -> ObsidianColors.MutedSilver
}

private fun formatDateTimeCompact(timestampMs: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return sdf.format(Date(timestampMs))
}

private fun formatDateTimeFull(timestampMs: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    return sdf.format(Date(timestampMs))
}

/**
 * Checks whether Android's On-Device Offline Speech Packs for Indonesian (`id-ID`)
 * and English (`en-US`) are already installed on Android 13+.
 */
private fun checkOnDeviceOfflinePacks(
    context: Context,
    isId: Boolean,
    onResult: (statusText: String, allReady: Boolean) -> Unit
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    ) {
        onResult(
            if (isId) "Paket offline: Kelola di Android" else "Offline packs: Manage in Android",
            false
        )
        return
    }
    val recognizer = runCatching {
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context.applicationContext)
    }.getOrNull() ?: return

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    runCatching {
        recognizer.checkRecognitionSupport(
            intent,
            context.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                    val installed = recognitionSupport.installedOnDeviceLanguages
                        .map { it.lowercase(Locale.US) }
                    val pending = recognitionSupport.pendingOnDeviceLanguages
                        .map { it.lowercase(Locale.US) }
                    val supported = recognitionSupport.supportedOnDeviceLanguages
                        .map { it.lowercase(Locale.US) }

                    val hasId = installed.any { it.startsWith("id") }
                    val hasEn = installed.any { it.startsWith("en") }
                    val pendingId = pending.any { it.startsWith("id") }
                    val supportsId = supported.any { it.startsWith("id") } || hasId || pendingId
                    val supportsEn = supported.any { it.startsWith("en") } || hasEn

                    val idLabel = when {
                        hasId -> if (isId) "ID: Terpasang ✓" else "ID: Installed ✓"
                        pendingId -> if (isId) "ID: Mengunduh..." else "ID: Downloading..."
                        supportsId -> if (isId) "ID: Belum diunduh" else "ID: Not downloaded"
                        else -> if (isId) "ID: Tidak didukung offline di ROM ini" else "ID: Unsupported offline on this ROM"
                    }
                    val enLabel = when {
                        hasEn -> if (isId) "EN: Terpasang ✓" else "EN: Installed ✓"
                        supportsEn -> if (isId) "EN: Belum diunduh" else "EN: Not downloaded"
                        else -> if (isId) "EN: Tidak didukung offline" else "EN: Unsupported offline"
                    }
                    onResult("$idLabel  ·  $enLabel", hasId && hasEn)
                    runCatching { recognizer.destroy() }
                }

                override fun onError(error: Int) {
                    runCatching { recognizer.destroy() }
                }
            }
        )
    }.onFailure {
        runCatching { recognizer.destroy() }
    }
}

/**
 * Triggers Android 13/14+ official On-Device Speech Model download for Indonesian (`id-ID`)
 * and English (`en-US`) directly without requiring the user to hunt through Settings.
 * Falls back to Android Voice Input settings on Android 12 and below.
 */
private fun triggerOrOpenOfflineSpeechPacks(
    context: Context,
    isId: Boolean,
    onStatusUpdate: (statusText: String, allReady: Boolean) -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    ) {
        val recognizer = runCatching {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context.applicationContext)
        }.getOrNull()

        if (recognizer != null) {
            onStatusUpdate(
                if (isId) "Memeriksa ketersediaan paket offline..." else "Checking offline pack support...",
                false
            )
            val checkIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            runCatching {
                recognizer.checkRecognitionSupport(
                    checkIntent,
                    context.mainExecutor,
                    object : RecognitionSupportCallback {
                        override fun onSupportResult(support: RecognitionSupport) {
                            val installed = support.installedOnDeviceLanguages.map { it.lowercase(Locale.US) }
                            val supported = support.supportedOnDeviceLanguages.map { it.lowercase(Locale.US) }
                            val targetLocale = if (isId) "id-ID" else "en-US"
                            val prefix = if (isId) "id" else "en"

                            val alreadyInstalled = installed.any { it.startsWith(prefix) }
                            if (alreadyInstalled) {
                                onStatusUpdate(
                                    if (isId) "Paket offline ${targetLocale.uppercase(Locale.US)} sudah terpasang ✓"
                                    else "Offline pack ${targetLocale.uppercase(Locale.US)} already installed ✓",
                                    true
                                )
                                runCatching { recognizer.destroy() }
                                return
                            }

                            val exactMatch = supported.firstOrNull { it.startsWith(prefix) }
                            if (exactMatch == null) {
                                onStatusUpdate(
                                    if (isId) "ROM HP ini tidak menyediakan paket offline Bahasa Indonesia via API Android. Gunakan mode Otomatis."
                                    else "This device ROM does not offer an offline pack for $targetLocale. Use Auto mode.",
                                    false
                                )
                                runCatching { recognizer.destroy() }
                                return
                            }

                            val dlIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, exactMatch)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                onStatusUpdate(
                                    if (isId) "Mengunduh paket offline ($exactMatch)..." else "Downloading offline pack ($exactMatch)...",
                                    false
                                )
                                recognizer.triggerModelDownload(
                                    dlIntent,
                                    context.mainExecutor,
                                    object : ModelDownloadListener {
                                        override fun onProgress(completedPercent: Int) {
                                            onStatusUpdate(
                                                if (isId) "Mengunduh paket offline ($exactMatch): $completedPercent%"
                                                else "Downloading offline pack ($exactMatch): $completedPercent%",
                                                false
                                            )
                                        }

                                        override fun onSuccess() {
                                            checkOnDeviceOfflinePacks(context, isId, onStatusUpdate)
                                            runCatching { recognizer.destroy() }
                                        }

                                        override fun onScheduled() {
                                            onStatusUpdate(
                                                if (isId) "Unduhan dijadwalkan oleh Android (pastikan Wi-Fi aktif)."
                                                else "Download scheduled by Android (ensure Wi-Fi is connected).",
                                                false
                                            )
                                            runCatching { recognizer.destroy() }
                                        }

                                        override fun onError(error: Int) {
                                            onStatusUpdate(
                                                if (isId) "Gagal mengunduh (kode $error). Pastikan internet aktif."
                                                else "Download failed (code $error). Ensure internet is active.",
                                                false
                                            )
                                            runCatching { recognizer.destroy() }
                                        }
                                    }
                                )
                            } else {
                                recognizer.triggerModelDownload(dlIntent)
                                onStatusUpdate(
                                    if (isId) "Unduhan paket offline ($exactMatch) diminta ke Android (cek notifikasi)."
                                    else "Offline pack download ($exactMatch) requested (check notifications).",
                                    false
                                )
                                runCatching { recognizer.destroy() }
                            }
                        }

                        override fun onError(error: Int) {
                            onStatusUpdate(
                                if (isId) "Tidak dapat memeriksa paket offline (kode $error). Gunakan mode Otomatis."
                                else "Unable to check offline packs (code $error). Use Auto mode.",
                                false
                            )
                            runCatching { recognizer.destroy() }
                        }
                    }
                )
            }.onFailure {
                runCatching { recognizer.destroy() }
                openOfflineSpeechPackSettings(context)
            }
            return
        }
    }

    openOfflineSpeechPackSettings(context)
}

/**
 * Fallback for Android < 13: opens Android's Voice Input / Speech settings.
 */
private fun openOfflineSpeechPackSettings(context: Context) {
    val candidates = listOf(
        // 1. Direct Google Speech Recognition & Synthesis offline language manager (Android 12+)
        Intent().apply {
            component = ComponentName(
                "com.google.android.tts",
                "com.google.android.apps.speech.tts.googletts.settings.OfflineSpeechRecognitionSettingsActivity"
            )
        },
        // 2. Google App Voice / Offline Speech settings
        Intent("com.google.android.googlequicksearchbox.action.VOICE_SETTINGS"),
        // 3. Android system Voice Input settings (tap gear icon next to Google Speech Services)
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
        // 4. Google Speech Recognition & Synthesis app info (Storage / Offline packs)
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.google.android.tts")),
        // 5. Text-to-Speech / Speech Services settings fallback
        Intent("com.android.settings.TTS_SETTINGS"),
        // 5. System Languages fallback
        Intent(Settings.ACTION_LOCALE_SETTINGS),
        // 6. Final fallback to Android Settings
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

@Composable
private fun AlarmSettingsView(
    telemetry: VehicleTelemetry,
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onWelcomeSoundModeChange: (WelcomeSoundMode) -> Unit,
    onEngineOffVoiceReminderChange: (Boolean) -> Unit,
    onVoiceButtonStyleChange: (VoiceButtonStyle) -> Unit,
    onTripRecordingEnabledChange: (Boolean) -> Unit,
    onTopBarBrandLogoChange: (TopBarBrandLogo) -> Unit,
    onRoadEffectEnabledChange: (Boolean) -> Unit,
    launcherSettings: LauncherSettings,
    hasOverlayPermission: Boolean,
    onDockEnabledChange: (Boolean) -> Unit,
    onDockDisplayModeChange: (DockDisplayMode) -> Unit,
    onDockPositionChange: (DockPosition) -> Unit,
    onAppIconSizeChange: (AppIconSize) -> Unit,
    onOpenBackgroundPicker: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenHomeLauncherSettings: () -> Unit,
    onSpeedLockThresholdChange: (Int) -> Unit,
    onTpmsLowPsiChange: (Float) -> Unit,
    onTpmsHighPsiChange: (Float) -> Unit,
    onTpmsHighTempChange: (Int) -> Unit,
    onCoolantOverheatChange: (Int) -> Unit,
    onBatteryLowChange: (Float) -> Unit,
    onBatteryHighChange: (Float) -> Unit,
    onResetToStandard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = appStrings()
    val sliderColors = SliderDefaults.colors(
        thumbColor = ObsidianColors.PureSilver,
        activeTrackColor = ObsidianColors.PureSilver,
        inactiveTrackColor = Color(0xFF1E222B)
    )

    val context = LocalContext.current
    val isIdLang = currentLanguage == AppLanguage.ID
    var offlinePackStatus by remember {
        mutableStateOf(
            if (isIdLang) "Memeriksa paket offline..." else "Checking offline packs..."
        )
    }
    var offlinePackReady by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(currentLanguage) {
        checkOnDeviceOfflinePacks(context, isIdLang) { statusText, allReady ->
            offlinePackStatus = statusText
            offlinePackReady = allReady
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // 1. Language (flat row, no box, no redundant title)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s.languageLabel,
                color = ObsidianColors.PureSilver,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0E1015))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppLanguage.entries.forEach { lang ->
                    val selected = lang == currentLanguage
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) Color(0xFF212836) else Color.Transparent)
                            .clickable { onLanguageChange(lang) }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = lang.displayName,
                            color = if (selected) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
        SettingsHairlineDivider()

        // 2. Welcome Sound on Engine Start (flat row)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s.welcomeSoundLabel,
                color = ObsidianColors.PureSilver,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 2
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0E1015))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    WelcomeSoundMode.OFF to s.offLabel,
                    WelcomeSoundMode.CHIME to s.chimeModeLabel,
                    WelcomeSoundMode.VOICE to s.voiceModeLabel
                ).forEach { (mode, label) ->
                    val selected = telemetry.welcomeSoundMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) Color(0xFF212836) else Color.Transparent)
                            .clickable { onWelcomeSoundModeChange(mode) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (selected) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
        SettingsHairlineDivider()

        // 3. Engine-OFF Voice Reminder Toggle (flat row)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s.voiceReminderLabel,
                color = ObsidianColors.PureSilver,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 2
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0E1015))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(true to s.onLabel, false to s.offLabel).forEach { (enabled, label) ->
                    val selected = telemetry.engineOffVoiceReminderEnabled == enabled
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) Color(0xFF212836) else Color.Transparent)
                            .clickable { onEngineOffVoiceReminderChange(enabled) }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (selected) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
        SettingsHairlineDivider()

        // 4. Voice Button Style (flat row: Top Bar [default] | Floating AssistiveTouch Glass)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s.voiceButtonStyleLabel,
                color = ObsidianColors.PureSilver,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 2
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0E1015))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    VoiceButtonStyle.TOP_BAR to s.voiceButtonTopBar,
                    VoiceButtonStyle.FLOATING to s.voiceButtonFloating
                ).forEach { (style, label) ->
                    val selected = telemetry.voiceButtonStyle == style
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) Color(0xFF212836) else Color.Transparent)
                            .clickable { onVoiceButtonStyleChange(style) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (selected) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
        SettingsHairlineDivider()

        // 5. Trip History GPS Recording Toggle (flat row: On | Off)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s.tripRecordingLabel,
                color = ObsidianColors.PureSilver,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 2
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0E1015))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(true to s.onLabel, false to s.offLabel).forEach { (enabled, label) ->
                    val selected = telemetry.tripRecordingEnabled == enabled
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) Color(0xFF212836) else Color.Transparent)
                            .clickable { onTripRecordingEnabledChange(enabled) }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (selected) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
        SettingsHairlineDivider()

        // 6. Road Motion Effect Toggle (flat row: On | Off)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s.roadEffectLabel,
                color = ObsidianColors.PureSilver,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 2
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0E1015))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(true to s.onLabel, false to s.offLabel).forEach { (enabled, label) ->
                    val selected = telemetry.roadEffectEnabled == enabled
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) Color(0xFF212836) else Color.Transparent)
                            .clickable { onRoadEffectEnabledChange(enabled) }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (selected) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
        SettingsHairlineDivider()

        // 7. Top Bar Brand Logo Selector (flat row: RioSense | Kia Classic | Kia Modern)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s.brandLogoLabel,
                color = ObsidianColors.PureSilver,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 2
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0E1015))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopBarBrandLogo.entries.forEach { logo ->
                    val selected = telemetry.topBarBrandLogo == logo
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) Color(0xFF212836) else Color.Transparent)
                            .clickable { onTopBarBrandLogoChange(logo) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (logo) {
                            TopBarBrandLogo.RIOSENSE -> Text(
                                text = "RioSense",
                                color = if (selected) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )

                            TopBarBrandLogo.KIA_CLASSIC -> Image(
                                painter = painterResource(id = R.drawable.ic_kia_classic),
                                contentDescription = "Kia Classic",
                                contentScale = ContentScale.Fit,
                                alpha = if (selected) 1f else 0.55f,
                                modifier = Modifier.height(14.dp)
                            )

                            TopBarBrandLogo.KIA_MODERN -> Image(
                                painter = painterResource(id = R.drawable.ic_kia_modern),
                                contentDescription = "Kia Modern",
                                contentScale = ContentScale.Fit,
                                alpha = if (selected) 1f else 0.55f,
                                modifier = Modifier.height(10.dp)
                            )
                        }
                    }
                }
            }
        }
        SettingsHairlineDivider()

        // 6. Voice Command Offline Pack (flat row: inline status left + Download / Delete pill right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (isIdLang) "Perintah suara offline" else "Offline voice pack",
                    color = ObsidianColors.PureSilver,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = offlinePackStatus,
                    color = if (offlinePackReady) ObsidianColors.EmeraldNominal else ObsidianColors.DimSilver,
                    fontSize = 10.sp,
                    maxLines = 2
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (offlinePackReady) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianColors.CrimsonSurface)
                        .clickable { showDeleteConfirm = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s.voiceSttDeleteBtn,
                        color = ObsidianColors.CrimsonAlert,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF161B24))
                        .clickable {
                            triggerOrOpenOfflineSpeechPacks(
                                context = context,
                                isId = isIdLang,
                                onStatusUpdate = { statusText, allReady ->
                                    offlinePackStatus = statusText
                                    offlinePackReady = allReady
                                }
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s.voiceSttDownloadBtn,
                        color = ObsidianColors.CobaltAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = Color(0xFF14171F),
                titleContentColor = ObsidianColors.PureSilver,
                textContentColor = ObsidianColors.MutedSilver,
                title = {
                    Text(
                        text = s.voiceSttDeleteConfirmTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Text(
                        text = s.voiceSttDeleteConfirmBody,
                        fontSize = 12.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            openOfflineSpeechPackSettings(context)
                        }
                    ) {
                        Text(
                            text = s.voiceSttDeleteBtn,
                            color = ObsidianColors.CrimsonAlert,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(
                            text = s.cancel,
                            color = ObsidianColors.DimSilver
                        )
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Grouped Launcher & Global Dock Container
        val isDefaultHome = remember(launcherSettings, hasOverlayPermission) {
            InstalledAppsRepository.isRioSenseDefaultLauncher(context)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianColors.ObsidianPanel)
                .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                text = s.launcherSectionTitle,
                color = ObsidianColors.DimSilver,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.7.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // 1. Global Dock On / Off
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.globalDockLabel,
                    color = ObsidianColors.PureSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0E1015))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(true to s.onLabel, false to s.offLabel).forEach { (enabled, label) ->
                        val selected = launcherSettings.dockEnabled == enabled
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) Color(0xFF212836) else Color.Transparent)
                                .clickable { onDockEnabledChange(enabled) }
                                .padding(horizontal = 11.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (selected) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (launcherSettings.dockEnabled) {
                SettingsHairlineDivider()

                // 2. Display Mode: Always | Edge Swipe (4 sides)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = s.dockModeLabel,
                        color = ObsidianColors.PureSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0E1015))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            DockDisplayMode.ALWAYS_VISIBLE to s.dockModeAlways,
                            DockDisplayMode.AUTO_HIDE_SWIPE to s.dockModeAutoHide
                        ).forEach { (mode, label) ->
                            val selected = launcherSettings.dockDisplayMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) Color(0xFF212836) else Color.Transparent)
                                    .clickable { onDockDisplayModeChange(mode) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                SettingsHairlineDivider()

                // 3. Position selector (when Always) or 4-edge hint (when Auto-Hide)
                if (launcherSettings.dockDisplayMode == DockDisplayMode.ALWAYS_VISIBLE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = s.dockPositionLabel,
                            color = ObsidianColors.PureSilver,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0E1015))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            listOf(
                                DockPosition.BOTTOM to s.dockPosBottom,
                                DockPosition.TOP to s.dockPosTop,
                                DockPosition.LEFT to s.dockPosLeft,
                                DockPosition.RIGHT to s.dockPosRight
                            ).forEach { (pos, label) ->
                                val selected = launcherSettings.dockPosition == pos
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) Color(0xFF212836) else Color.Transparent)
                                        .clickable { onDockPositionChange(pos) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                                        fontSize = 9.5.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = s.dockSwipeAnyEdgeHint,
                        color = ObsidianColors.CobaltAccent,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                SettingsHairlineDivider()

                // 4. Global Overlay Permission Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = s.overlayPermissionLabel,
                        color = ObsidianColors.PureSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (hasOverlayPermission) Color(0xFF152820) else Color(0xFF1D2430))
                            .clickable { onRequestOverlayPermission() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (hasOverlayPermission) s.overlayPermissionGranted else s.overlayPermissionAction,
                            color = if (hasOverlayPermission) ObsidianColors.EmeraldNominal else ObsidianColors.CobaltAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            SettingsHairlineDivider()

            // 5. App Drawer Icon Size (Small | Medium | Large)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.appIconSizeLabel,
                    color = ObsidianColors.PureSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0E1015))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        AppIconSize.SMALL to s.iconSizeSmall,
                        AppIconSize.MEDIUM to s.iconSizeMedium,
                        AppIconSize.LARGE to s.iconSizeLarge
                    ).forEach { (size, label) ->
                        val selected = launcherSettings.appIconSize == size
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) Color(0xFF212836) else Color.Transparent)
                                .clickable { onAppIconSizeChange(size) }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (selected) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                                fontSize = 9.5.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            SettingsHairlineDivider()

            // 6. Background Row with Live Preview Swatch (opens slide-up selector over App List)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBackgroundPicker() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.backgroundTitle,
                    color = ObsidianColors.PureSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = appDrawerBackgroundLabel(launcherSettings.appDrawerBackground, s),
                        color = ObsidianColors.MutedSilver,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    AppDrawerBackgroundCanvas(
                        preset = launcherSettings.appDrawerBackground,
                        isPreview = true,
                        modifier = Modifier
                            .size(width = 54.dp, height = 24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(0.8.dp, ObsidianColors.ObsidianSubBorder, RoundedCornerShape(6.dp))
                    )
                    Text(
                        text = "›",
                        color = ObsidianColors.DimSilver,
                        fontSize = 15.sp
                    )
                }
            }

            SettingsHairlineDivider()

            // 7. Default Android Home Launcher Shortcut
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.defaultLauncherLabel,
                    color = ObsidianColors.PureSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isDefaultHome) Color(0xFF152820) else Color(0xFF1D2430))
                        .clickable { onOpenHomeLauncherSettings() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isDefaultHome) s.defaultLauncherActive else s.defaultLauncherAction,
                        color = if (isDefaultHome) ObsidianColors.EmeraldNominal else ObsidianColors.PureSilver,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Grouped Alarm Thresholds Container (subtle low-contrast Obsidian border box)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianColors.ObsidianPanel)
                .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Status & Reset Standard Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (telemetry.isCustomAlertSettings) {
                        s.customThresholdsActive
                    } else {
                        s.standardDefaultsActive
                    },
                    color = if (telemetry.isCustomAlertSettings) ObsidianColors.CobaltAccent else ObsidianColors.DimSilver,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (telemetry.isCustomAlertSettings) Color(0xFF1D2430) else Color(0xFF161920))
                        .clickable { onResetToStandard() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = s.resetStandard,
                        color = if (telemetry.isCustomAlertSettings) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            SettingsHairlineDivider()

            // Single-item threshold: Door Lock Speed
            ThresholdSliderItem(
                label = s.doorLockLabel,
                valueText = "${telemetry.speedLockAlarmThresholdKmh} km/h",
                standardText = "${s.stdPrefix}: ${VehicleTelemetry.DEFAULT_SPEED_LOCK_THRESHOLD_KMH} km/h",
                value = telemetry.speedLockAlarmThresholdKmh.toFloat(),
                valueRange = 10f..60f,
                onValueChange = { onSpeedLockThresholdChange(it.toInt()) },
                sliderColors = sliderColors
            )

            SettingsHairlineDivider()

            // Single-item threshold: Engine Coolant Overheat
            ThresholdSliderItem(
                label = s.coolantLabel,
                valueText = "${telemetry.coolantOverheatThresholdC} °C",
                standardText = "${s.stdPrefix}: ${VehicleTelemetry.DEFAULT_COOLANT_OVERHEAT_C} °C",
                value = telemetry.coolantOverheatThresholdC.toFloat(),
                valueRange = 92f..118f,
                onValueChange = { onCoolantOverheatChange(it.toInt()) },
                sliderColors = sliderColors
            )

            // Titled Multi-item Group: Tire TPMS Alarm (subtle Obsidian border box)
            SettingsGroupSection(title = s.tpmsSection) {
                ThresholdSliderItem(
                    label = s.tpmsLowLabel,
                    valueText = "${String.format("%.1f", telemetry.tpmsLowThresholdPsi)} PSI",
                    standardText = "${s.stdPrefix}: ${String.format("%.1f", VehicleTelemetry.DEFAULT_TPMS_LOW_PSI)} PSI",
                    value = telemetry.tpmsLowThresholdPsi,
                    valueRange = 18f..32f,
                    onValueChange = onTpmsLowPsiChange,
                    sliderColors = sliderColors
                )
                ThresholdSliderItem(
                    label = s.tpmsHighLabel,
                    valueText = "${String.format("%.1f", telemetry.tpmsHighThresholdPsi)} PSI",
                    standardText = "${s.stdPrefix}: ${String.format("%.1f", VehicleTelemetry.DEFAULT_TPMS_HIGH_PSI)} PSI",
                    value = telemetry.tpmsHighThresholdPsi,
                    valueRange = 34f..45f,
                    onValueChange = onTpmsHighPsiChange,
                    sliderColors = sliderColors
                )
                ThresholdSliderItem(
                    label = s.tpmsTempLabel,
                    valueText = "${telemetry.tpmsHighTempC} °C",
                    standardText = "${s.stdPrefix}: ${VehicleTelemetry.DEFAULT_TPMS_HIGH_TEMP_C} °C",
                    value = telemetry.tpmsHighTempC.toFloat(),
                    valueRange = 35f..65f,
                    onValueChange = { onTpmsHighTempChange(it.toInt()) },
                    sliderColors = sliderColors
                )
            }

            // Titled Multi-item Group: Battery Voltage Alarm (subtle Obsidian border box)
            SettingsGroupSection(title = s.batterySection) {
                ThresholdSliderItem(
                    label = s.batteryLowLabel,
                    valueText = "${String.format("%.1f", telemetry.batteryLowThresholdV)} V",
                    standardText = "${s.stdPrefix}: ${String.format("%.1f", VehicleTelemetry.DEFAULT_BATTERY_LOW_V)} V",
                    value = telemetry.batteryLowThresholdV,
                    valueRange = 10.5f..12.6f,
                    onValueChange = onBatteryLowChange,
                    sliderColors = sliderColors
                )
                ThresholdSliderItem(
                    label = s.batteryHighLabel,
                    valueText = "${String.format("%.1f", telemetry.batteryHighThresholdV)} V",
                    standardText = "${s.stdPrefix}: ${String.format("%.1f", VehicleTelemetry.DEFAULT_BATTERY_HIGH_V)} V",
                    value = telemetry.batteryHighThresholdV,
                    valueRange = 14.4f..16.0f,
                    onValueChange = onBatteryHighChange,
                    sliderColors = sliderColors
                )
            }
        }
    }
}

@Composable
private fun SettingsHairlineDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ObsidianColors.ObsidianHairline)
    )
}

@Composable
private fun SettingsGroupSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianColors.ObsidianSubPanel)
            .border(1.dp, ObsidianColors.ObsidianSubBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            color = ObsidianColors.DimSilver,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.7.sp
        )
        content()
    }
}

@Composable
private fun ThresholdSliderItem(
    label: String,
    valueText: String,
    standardText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    sliderColors: androidx.compose.material3.SliderColors
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = ObsidianColors.MutedSilver,
                fontSize = 10.5.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = standardText,
                    color = ObsidianColors.DimSilver,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = valueText,
                    color = ObsidianColors.PureSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = sliderColors,
            modifier = Modifier.height(30.dp)
        )
    }
}
