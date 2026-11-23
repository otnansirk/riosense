package id.bangsadigital.riosense.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bangsadigital.riosense.model.MaintenanceState
import id.bangsadigital.riosense.model.MaintenanceStatus
import id.bangsadigital.riosense.model.ObdActivityEntry
import id.bangsadigital.riosense.model.ObdDirection
import id.bangsadigital.riosense.model.ObdErrorEntry
import id.bangsadigital.riosense.model.ObdErrorSeverity
import id.bangsadigital.riosense.model.VehicleTelemetry
import id.bangsadigital.riosense.model.WelcomeSoundMode
import id.bangsadigital.riosense.ui.i18n.AppLanguage
import id.bangsadigital.riosense.ui.i18n.appStrings
import id.bangsadigital.riosense.ui.theme.ObsidianColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ControlsTab(val label: String, val subtitle: String) {
    ERROR_LOG("Error Log", "OBD-II detected faults & DTC history"),
    ACTIVITY_LOG("Activity Log", "Real-time OBD-II TX/RX stream & PIDs"),
    SERVICE("Service", "Fast-moving parts & maintenance reminders"),
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
    initialTab: ControlsTab? = null,
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
            activeSection == ControlsTab.SERVICE && showAddServiceForm -> showAddServiceForm = false
            activeSection == ControlsTab.SERVICE && showRecordServiceForm -> showRecordServiceForm = false
            activeSection == ControlsTab.SERVICE && selectedServiceId != null -> selectedServiceId = null
            activeSection != null -> {
                activeSection = null
                selectedError = null
                selectedActivity = null
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
                            dueServiceItems.isNotEmpty() -> Color(0xFFE6A23C)
                            else -> ObsidianColors.DimSilver
                        },
                        showDivider = true,
                        onClick = { activeSection = ControlsTab.SERVICE }
                    )

                    ControlsMenuRowItem(
                        title = s.settingsTitle,
                        subtitle = s.settingsSubtitle,
                        statusText = if (telemetry.isCustomAlertSettings) s.statusCustom else s.statusStandard,
                        statusColor = if (telemetry.isCustomAlertSettings) Color(0xFF63B3ED) else ObsidianColors.DimSilver,
                        showDivider = true,
                        onClick = { activeSection = ControlsTab.SETTINGS }
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
                        statusColor = if (isActivityPaused) ObsidianColors.DimSilver else Color(0xFF68D391),
                        showDivider = false,
                        onClick = { activeSection = ControlsTab.ACTIVITY_LOG }
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (entries.isEmpty()) s.noErrorsDetected else s.detectedTapDetails(entries.size),
                color = ObsidianColors.DimSilver,
                fontSize = 10.sp
            )
            if (entries.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF181B22))
                        .clickable { onClear() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = s.clear,
                        color = ObsidianColors.MutedSilver,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF12151B)),
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
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries, key = { it.id }) { item ->
                    val dotColor = severityColor(item.severity)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF14171E))
                            .clickable { onSelect(item) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = item.code,
                                        color = dotColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = item.title,
                                        color = ObsidianColors.PureSilver,
                                        fontSize = 11.sp,
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
                        }

                        Text(
                            text = "›",
                            color = ObsidianColors.DimSilver,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF14171E))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = entry.title,
                        color = ObsidianColors.PureSilver,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = entry.severity.label.uppercase(),
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val s = appStrings()
            DetailMetaRow(label = s.timestamp, value = formatDateTimeFull(entry.timestampMs), mono = true)
            DetailMetaRow(label = s.ecuModule, value = entry.subsystem)
            DetailMetaRow(label = s.servicePid, value = entry.pid, mono = true)
            DetailMetaRow(label = s.rawFrame, value = entry.rawFrame, mono = true)
            DetailMetaRow(label = s.source, value = entry.source)

            Spacer(modifier = Modifier.height(2.dp))
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                        .background(if (isPaused) ObsidianColors.DimSilver else Color(0xFF38A169))
                )
                Text(
                    text = if (isPaused) s.streamPausedTap else s.liveStreamTap,
                    color = ObsidianColors.DimSilver,
                    fontSize = 10.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF181B22))
                        .clickable { onTogglePause() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isPaused) s.resume else s.pause,
                        color = ObsidianColors.PureSilver,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (entries.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF181B22))
                            .clickable { onClear() }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = s.clear,
                            color = ObsidianColors.MutedSilver,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF12151B)),
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
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(entries, key = { it.id }) { item ->
                    val isTx = item.direction == ObdDirection.TX
                    val dirColor = if (isTx) Color(0xFF63B3ED) else Color(0xFF68D391)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFF14171E))
                            .clickable { onSelect(item) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
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
                                    .background(dirColor.copy(alpha = 0.16f))
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
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Text(
                            text = "›",
                            color = ObsidianColors.DimSilver,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
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
    val dirColor = if (isTx) Color(0xFF63B3ED) else Color(0xFF68D391)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF14171E))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = entry.parameterName,
                        color = ObsidianColors.PureSilver,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val s = appStrings()
                Text(
                    text = if (isTx) s.txSentToEcu else s.rxReceivedFromEcu,
                    color = dirColor,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            val sDetail = appStrings()
            DetailMetaRow(label = sDetail.timestamp, value = formatDateTimeFull(entry.timestampMs), mono = true)
            DetailMetaRow(label = sDetail.quickValue, value = entry.quickValue)
            DetailMetaRow(label = sDetail.ecuHeader, value = entry.ecuHeader, mono = true)
            DetailMetaRow(label = sDetail.rawFrame, value = entry.rawFrame, mono = true)
            DetailMetaRow(label = sDetail.formula, value = entry.formula, mono = true)
            DetailMetaRow(label = sDetail.source, value = entry.source)

            Spacer(modifier = Modifier.height(2.dp))
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
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF0E1015))
            .padding(horizontal = 10.dp, vertical = 7.dp),
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
    ObdErrorSeverity.WARNING -> Color(0xFFE6A23C)
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

@Composable
private fun AlarmSettingsView(
    telemetry: VehicleTelemetry,
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onWelcomeSoundModeChange: (WelcomeSoundMode) -> Unit,
    onEngineOffVoiceReminderChange: (Boolean) -> Unit,
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 0. Language Selector (English / Indonesia)
        SettingsGroupCard(title = s.languageSectionTitle) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.languageLabel,
                    color = ObsidianColors.MutedSilver,
                    fontSize = 10.5.sp
                )
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
        }

        // 0a. Welcome Sound on Engine Start (Off | Chime | Voice)
        SettingsGroupCard(title = s.welcomeSoundSectionTitle) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.welcomeSoundLabel,
                    color = ObsidianColors.MutedSilver,
                    fontSize = 10.5.sp,
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
        }

        // 0b. Engine-OFF Voice Reminder Toggle (TTS)
        SettingsGroupCard(title = s.voiceReminderSectionTitle) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.voiceReminderLabel,
                    color = ObsidianColors.MutedSilver,
                    fontSize = 10.5.sp,
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
        }

        // Status & Reset Standard Button
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
                color = if (telemetry.isCustomAlertSettings) Color(0xFF63B3ED) else ObsidianColors.DimSilver,
                fontSize = 10.sp
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

        // 1. Door Lock Speed Alarm
        SettingsGroupCard(title = s.doorLockSection) {
            ThresholdSliderItem(
                label = s.doorLockLabel,
                valueText = "${telemetry.speedLockAlarmThresholdKmh} km/h",
                standardText = "${s.stdPrefix}: ${VehicleTelemetry.DEFAULT_SPEED_LOCK_THRESHOLD_KMH} km/h",
                value = telemetry.speedLockAlarmThresholdKmh.toFloat(),
                valueRange = 10f..60f,
                onValueChange = { onSpeedLockThresholdChange(it.toInt()) },
                sliderColors = sliderColors
            )
        }

        // 2. Tire TPMS Alarm (PSI & Temperature)
        SettingsGroupCard(title = s.tpmsSection) {
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

        // 3. Engine Coolant Overheat Alarm
        SettingsGroupCard(title = s.coolantSection) {
            ThresholdSliderItem(
                label = s.coolantLabel,
                valueText = "${telemetry.coolantOverheatThresholdC} °C",
                standardText = "${s.stdPrefix}: ${VehicleTelemetry.DEFAULT_COOLANT_OVERHEAT_C} °C",
                value = telemetry.coolantOverheatThresholdC.toFloat(),
                valueRange = 92f..118f,
                onValueChange = { onCoolantOverheatChange(it.toInt()) },
                sliderColors = sliderColors
            )
        }

        // 4. Battery Voltage Alarm
        SettingsGroupCard(title = s.batterySection) {
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

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF14171E))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
