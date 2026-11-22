package id.bangsadigital.riosense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bangsadigital.riosense.data.VehicleRepository
import id.bangsadigital.riosense.model.KiaRioPaintColor
import id.bangsadigital.riosense.model.MaintenanceState
import id.bangsadigital.riosense.model.MaintenanceStatus
import id.bangsadigital.riosense.model.VehicleTelemetry
import id.bangsadigital.riosense.ui.components.ControlsMenuSheet
import id.bangsadigital.riosense.ui.components.ControlsTab
import id.bangsadigital.riosense.ui.components.KiaRioInteractiveStage
import id.bangsadigital.riosense.ui.components.LeftSpeedAndSecurityPanel
import id.bangsadigital.riosense.ui.components.MinimizedFloatingPillView
import id.bangsadigital.riosense.ui.components.RightEngineTelemetryPanel
import id.bangsadigital.riosense.ui.components.SimulatorAndSettingsSheet
import id.bangsadigital.riosense.ui.theme.ObsidianColors

/**
 * Single feature flag to hide the Simulator badge and drawer once live USB OBD-II testing is complete.
 */
private const val SHOW_SIMULATOR_TOOLS = true

@Composable
fun RioSenseDashboardScreen(
    telemetry: VehicleTelemetry,
    repository: VehicleRepository,
    onEnterPipMode: () -> Unit
) {
    var showSimulatorDrawer by remember { mutableStateOf(false) }
    var showControlsDrawer by remember { mutableStateOf(false) }
    var controlsInitialTab by remember { mutableStateOf<ControlsTab?>(null) }

    val errorLogs by repository.errorLogs.collectAsState()
    val activityLogs by repository.activityLogs.collectAsState()
    val isActivityPaused by repository.activityLogPaused.collectAsState()
    val maintenance by repository.maintenance.collectAsState()
    val dueServiceItems = remember(maintenance) { maintenance.dueOrOverdueItems() }

    if (telemetry.isMinimized) {
        MinimizedFloatingPillView(
            telemetry = telemetry,
            onExpandDashboard = { repository.setMinimized(false) },
            onTriggerSimulatedDoorAlarm = {
                repository.applyNormalPreset()
                repository.setMinimized(true)
                repository.toggleDoor("RR")
            },
            onEnterNativePip = onEnterPipMode
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianColors.DeepObsidian)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. BORDERLESS OEM TOP BAR
            TopObsidianBar(
                telemetry = telemetry,
                showSimulatorEntry = SHOW_SIMULATOR_TOOLS && telemetry.simulatorMode,
                hasServiceReminder = dueServiceItems.isNotEmpty(),
                onSelectPaint = { repository.setPaintColor(it) },
                onToggleMute = { repository.toggleAudioMute() },
                onOpenSimulator = {
                    showControlsDrawer = false
                    showSimulatorDrawer = !showSimulatorDrawer
                },
                onOpenControls = {
                    showSimulatorDrawer = false
                    controlsInitialTab = null
                    showControlsDrawer = !showControlsDrawer
                },
                onMinimize = { repository.setMinimized(true) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 2. SEAMLESS 3-COLUMN STAGE (Zero borders on Speed, Car Stage, and Engine Telemetry)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LeftSpeedAndSecurityPanel(
                    telemetry = telemetry,
                    onToggleLock = { repository.toggleCentralLock() },
                    modifier = Modifier.weight(0.26f)
                )

                KiaRioInteractiveStage(
                    telemetry = telemetry,
                    onToggleDoor = { doorCode -> repository.toggleDoor(doorCode) },
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight()
                )

                RightEngineTelemetryPanel(
                    telemetry = telemetry,
                    modifier = Modifier.weight(0.26f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. BORDERLESS MINIMALIST STATUS FOOTER (Live Alerts > Service Reminders > Nominal)
            BottomAlertBanner(
                telemetry = telemetry,
                maintenance = maintenance,
                onAcknowledgeOrFix = { repository.applyNormalPreset() },
                onOpenServiceReminders = {
                    showSimulatorDrawer = false
                    controlsInitialTab = ControlsTab.SERVICE
                    showControlsDrawer = true
                }
            )
        }

        // 4. SIMULATOR SHEET (Opened from top-left "Simulator" badge; easily hidden via SHOW_SIMULATOR_TOOLS)
        if (SHOW_SIMULATOR_TOOLS && showSimulatorDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable { showSimulatorDrawer = false },
                contentAlignment = Alignment.CenterStart
            ) {
                SimulatorAndSettingsSheet(
                    telemetry = telemetry,
                    onSpeedRpmChange = { sp, rpm -> repository.setSpeedAndRpm(sp, rpm) },
                    onCoolantChange = { temp -> repository.setCoolantTemp(temp) },
                    onToggleDoor = { code -> repository.toggleDoor(code) },
                    onToggleLock = { repository.toggleCentralLock() },
                    onToggleLight = { code -> repository.toggleLight(code) },
                    onTirePressureChange = { code, psi -> repository.setTirePressure(code, psi) },
                    onTireTempChange = { code, temp -> repository.setTireTemp(code, temp) },
                    onPaintColorSelect = { color -> repository.setPaintColor(color) },
                    onBatteryVoltageChange = { volts -> repository.setBatteryVoltage(volts) },
                    onApplyNormalPreset = { repository.applyNormalPreset() },
                    onApplyAlertPreset = { repository.applyAlertShowcasePreset() },
                    onInjectDtc = { code -> repository.injectSimulatedDtc(code) },
                    onClose = { showSimulatorDrawer = false },
                    modifier = Modifier.clickable(enabled = false) {}
                )
            }
        }

        // 5. CONTROLS MENU SHEET (OBD-II Diagnostics: Error Log, Activity Log, Service & Settings)
        if (showControlsDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable { showControlsDrawer = false },
                contentAlignment = Alignment.CenterEnd
            ) {
                ControlsMenuSheet(
                    telemetry = telemetry,
                    maintenance = maintenance,
                    initialTab = controlsInitialTab,
                    errorLogs = errorLogs,
                    activityLogs = activityLogs,
                    isActivityPaused = isActivityPaused,
                    onToggleActivityPause = { repository.toggleActivityLogPaused() },
                    onClearErrorLogs = { repository.clearErrorLogs() },
                    onClearActivityLogs = { repository.clearActivityLogs() },
                    onUpdateOdometer = { km -> repository.updateOdometerKm(km) },
                    onRemindDaysChange = { days -> repository.setMaintenanceRemindDays(days) },
                    onRemindKmChange = { km -> repository.setMaintenanceRemindKm(km) },
                    onLogServiceRecord = { id, dateMs, odoKm, costIdr, note ->
                        repository.logMaintenanceServiceRecord(id, dateMs, odoKm, costIdr, note)
                    },
                    onDeleteHistoryRecord = { id, recId ->
                        repository.deleteMaintenanceHistoryRecord(id, recId)
                    },
                    onUpdateServiceSchedule = { id, months, km ->
                        repository.updateMaintenanceItemSchedule(id, months, km)
                    },
                    onAddCustomServiceItem = { name, months, km, dateMs, odoKm, costIdr, note ->
                        repository.addCustomMaintenanceItem(name, months, km, dateMs, odoKm, costIdr, note)
                    },
                    onDeleteCustomServiceItem = { id -> repository.deleteCustomMaintenanceItem(id) },
                    onResetMaintenanceToStandard = { repository.resetMaintenanceToStandard() },
                    onSpeedLockThresholdChange = { kmh -> repository.setSpeedLockThreshold(kmh) },
                    onTpmsLowPsiChange = { psi -> repository.setTpmsLowThreshold(psi) },
                    onTpmsHighPsiChange = { psi -> repository.setTpmsHighThreshold(psi) },
                    onTpmsHighTempChange = { tempC -> repository.setTpmsHighTempThreshold(tempC) },
                    onCoolantOverheatChange = { tempC -> repository.setCoolantOverheatThreshold(tempC) },
                    onBatteryLowChange = { volts -> repository.setBatteryLowThreshold(volts) },
                    onBatteryHighChange = { volts -> repository.setBatteryHighThreshold(volts) },
                    onResetSettingsToStandard = { repository.resetAlertSettingsToStandard() },
                    onClose = { showControlsDrawer = false },
                    modifier = Modifier.clickable(enabled = false) {}
                )
            }
        }
    }
}

@Composable
private fun TopObsidianBar(
    telemetry: VehicleTelemetry,
    showSimulatorEntry: Boolean,
    hasServiceReminder: Boolean,
    onSelectPaint: (KiaRioPaintColor) -> Unit,
    onToggleMute: () -> Unit,
    onOpenSimulator: () -> Unit,
    onOpenControls: () -> Unit,
    onMinimize: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brand & Clickable Simulator Entry (or OBD Status when Simulator is hidden/off)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "RioSense",
                color = ObsidianColors.PureSilver,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )

            if (showSimulatorEntry) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF15181F))
                        .clickable { onOpenSimulator() }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF63B3ED))
                    )
                    Text(
                        text = "Simulator",
                        color = ObsidianColors.PureSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (telemetry.obdConnected) Color(0xFF38A169) else Color(0xFF646C7E))
                    )
                    Text(
                        text = if (telemetry.obdConnected) "OBD Connected" else "OBD Standby",
                        color = ObsidianColors.DimSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        // Paint Swatches (Clean circles without noisy labels)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KiaRioPaintColor.entries.forEach { paint ->
                val isSelected = telemetry.paintColor == paint
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 16.dp else 12.dp)
                        .clip(CircleShape)
                        .background(paint.primaryHex)
                        .then(
                            if (isSelected) {
                                Modifier.border(1.5.dp, Color.White, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onSelectPaint(paint) }
                )
            }
        }

        // Borderless Matte Charcoal Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0E1014))
                    .clickable { onToggleMute() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (telemetry.audioAlarmMuted) "Muted" else "Chime",
                    color = if (telemetry.audioAlarmMuted) ObsidianColors.DimSilver else ObsidianColors.MutedSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF15181F))
                    .clickable { onOpenControls() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (hasServiceReminder) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE6A23C))
                    )
                }
                Text(
                    text = "Controls",
                    color = ObsidianColors.PureSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0E1014))
                    .clickable { onMinimize() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Minimize",
                    color = ObsidianColors.MutedSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun BottomAlertBanner(
    telemetry: VehicleTelemetry,
    maintenance: MaintenanceState,
    onAcknowledgeOrFix: () -> Unit,
    onOpenServiceReminders: () -> Unit
) {
    val alerts = telemetry.activeAlerts
    val hasAlert = alerts.isNotEmpty()
    val nowMs = System.currentTimeMillis()
    val dueServices = maintenance.dueOrOverdueItems(nowMs)
    val hasServiceReminder = !hasAlert && dueServices.isNotEmpty()
    val hasOverdueService = dueServices.any {
        it.evaluateStatus(
            nowMs,
            maintenance.currentOdometerKm,
            maintenance.remindDaysBefore,
            maintenance.remindKmBefore
        ) == MaintenanceStatus.OVERDUE
    }

    val bg = when {
        hasAlert -> ObsidianColors.AlertSurface
        hasServiceReminder -> Color(0xFF17140E)
        else -> Color(0xFF0B0C0F)
    }

    val dotColor = when {
        hasAlert -> ObsidianColors.CrimsonAlert
        hasServiceReminder && hasOverdueService -> ObsidianColors.CrimsonAlert
        hasServiceReminder -> Color(0xFFE6A23C)
        else -> Color(0xFF4C5260)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .then(
                if (hasServiceReminder) Modifier.clickable { onOpenServiceReminders() }
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = when {
                    hasAlert -> alerts.joinToString("  ·  ")
                    hasServiceReminder -> {
                        val summary = dueServices.take(2).joinToString("  ·  ") { item ->
                            "${item.name} (${item.quickSummary(nowMs, maintenance.currentOdometerKm)})"
                        }
                        val extra = if (dueServices.size > 2) " +${dueServices.size - 2} more" else ""
                        "SERVICE REMINDER: $summary$extra"
                    }
                    else -> "Kia Rio UB 2015  ·  ${telemetry.paintColor.displayName}  ·  All systems nominal"
                },
                color = if (hasAlert || hasServiceReminder) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                fontSize = 11.sp,
                fontWeight = if (hasAlert || hasServiceReminder) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (hasAlert) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF281A1D))
                    .clickable { onAcknowledgeOrFix() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Dismiss",
                    color = ObsidianColors.PureSilver,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (hasServiceReminder) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2A2214))
                    .clickable { onOpenServiceReminders() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Service",
                    color = Color(0xFFE6A23C),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
