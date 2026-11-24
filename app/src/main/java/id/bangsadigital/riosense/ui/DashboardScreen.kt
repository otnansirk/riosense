package id.bangsadigital.riosense.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bangsadigital.riosense.VoiceDashboardNavigation
import id.bangsadigital.riosense.data.VehicleRepository
import id.bangsadigital.riosense.model.KiaRioPaintColor
import id.bangsadigital.riosense.model.MaintenanceState
import id.bangsadigital.riosense.model.MaintenanceStatus
import id.bangsadigital.riosense.model.VehicleTelemetry
import id.bangsadigital.riosense.model.VoiceButtonStyle
import kotlin.math.roundToInt
import id.bangsadigital.riosense.ui.components.ControlsMenuSheet
import id.bangsadigital.riosense.ui.components.ControlsTab
import id.bangsadigital.riosense.ui.components.KiaRioInteractiveStage
import id.bangsadigital.riosense.ui.components.LeftSpeedAndSecurityPanel
import id.bangsadigital.riosense.ui.components.MinimizedFloatingPillView
import id.bangsadigital.riosense.ui.components.RightEngineTelemetryPanel
import id.bangsadigital.riosense.ui.components.SimulatorAndSettingsSheet
import id.bangsadigital.riosense.ui.i18n.AppLanguage
import id.bangsadigital.riosense.ui.i18n.LocalAppLanguage
import id.bangsadigital.riosense.ui.i18n.appStrings
import id.bangsadigital.riosense.ui.theme.ObsidianColors
import id.bangsadigital.riosense.voice.VoiceUiState

/**
 * Single feature flag to hide the Simulator badge and drawer once live USB OBD-II testing is complete.
 */
private const val SHOW_SIMULATOR_TOOLS = true

@Composable
fun RioSenseDashboardScreen(
    telemetry: VehicleTelemetry,
    repository: VehicleRepository,
    voiceUiState: VoiceUiState,
    voiceNavigation: VoiceDashboardNavigation?,
    onToggleVoiceListening: () -> Unit,
    onDismissVoiceFeedback: () -> Unit,
    onEnterPipMode: () -> Unit
) {
    var showSimulatorDrawer by remember { mutableStateOf(false) }
    var showControlsDrawer by remember { mutableStateOf(false) }
    var controlsInitialTab by remember { mutableStateOf<ControlsTab?>(null) }

    LaunchedEffect(voiceNavigation) {
        when (voiceNavigation) {
            is VoiceDashboardNavigation.OpenControls -> {
                showSimulatorDrawer = false
                controlsInitialTab = voiceNavigation.tab
                showControlsDrawer = true
            }
            is VoiceDashboardNavigation.CloseMenus -> {
                showSimulatorDrawer = false
                showControlsDrawer = false
            }
            null -> Unit
        }
    }

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
                isVoiceListening = voiceUiState.isListening,
                onSelectPaint = { repository.setPaintColor(it) },
                onToggleVoiceListening = onToggleVoiceListening,
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

        // 3b. FLOATING OBSIDIAN VOICE COMMAND OVERLAY (Listening Indicator & Feedback Card)
        if (voiceUiState.isListening || voiceUiState.feedbackCard != null) {
            VoiceCommandFloatingOverlay(
                voiceUiState = voiceUiState,
                onStopListening = onToggleVoiceListening,
                onDismissCard = onDismissVoiceFeedback,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 46.dp)
            )
        }

        // 3c. OPTIONAL ASSISTIVETOUCH-STYLE FLOATING GLASS MIC BUTTON (Draggable with Edge Snap)
        if (telemetry.voiceButtonStyle == VoiceButtonStyle.FLOATING) {
            FloatingGlassMicButton(
                isListening = voiceUiState.isListening,
                onClick = onToggleVoiceListening
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
                    onSimulateEngineStart = { repository.simulateEngineStart() },
                    onSimulateEngineOff = { repository.simulateEngineOff() },
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
                    currentLanguage = LocalAppLanguage.current,
                    onLanguageChange = { lang -> repository.setAppLanguage(lang) },
                    onWelcomeSoundModeChange = { mode ->
                        repository.setWelcomeSoundMode(mode)
                    },
                    onEngineOffVoiceReminderChange = { enabled ->
                        repository.setEngineOffVoiceReminderEnabled(enabled)
                    },
                    onVoiceButtonStyleChange = { style ->
                        repository.setVoiceButtonStyle(style)
                    },
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
    isVoiceListening: Boolean,
    onSelectPaint: (KiaRioPaintColor) -> Unit,
    onToggleVoiceListening: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenSimulator: () -> Unit,
    onOpenControls: () -> Unit,
    onMinimize: () -> Unit
) {
    val s = appStrings()
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
                        text = s.simulator,
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
                        text = if (telemetry.obdConnected) s.obdConnected else s.obdStandby,
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
            // Voice Command Microphone Button (shown in Top Bar by default; hidden when Floating style is active)
            if (telemetry.voiceButtonStyle == VoiceButtonStyle.TOP_BAR) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isVoiceListening) Color(0xFF152536) else Color(0xFF0E1014))
                        .then(
                            if (isVoiceListening) {
                                Modifier.border(0.9.dp, Color(0xFF63B3ED), RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onToggleVoiceListening() },
                    contentAlignment = Alignment.Center
                ) {
                    MicrophoneVectorIcon(
                        tint = if (isVoiceListening) Color(0xFF63B3ED) else ObsidianColors.MutedSilver,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0E1014))
                    .clickable { onToggleMute() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (telemetry.audioAlarmMuted) s.muted else s.chime,
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
                    text = s.controls,
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
                    text = s.minimize,
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
    val s = appStrings()
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
                        val extra = if (dueServices.size > 2) s.moreSuffix(dueServices.size - 2) else ""
                        "${s.serviceReminderPrefix}: $summary$extra"
                    }
                    else -> "Kia Rio UB 2015  ·  ${telemetry.paintColor.displayName}  ·  ${s.allSystemsNominal}"
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
                    text = s.dismiss,
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
                    text = s.serviceBtn,
                    color = Color(0xFFE6A23C),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun VoiceCommandFloatingOverlay(
    voiceUiState: VoiceUiState,
    onStopListening: () -> Unit,
    onDismissCard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isId = LocalAppLanguage.current == AppLanguage.ID

    if (voiceUiState.isListening) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF011161F))
                .border(0.9.dp, Color(0xFF63B3ED).copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                .clickable { onStopListening() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF63B3ED))
            )
            Column {
                Text(
                    text = if (isId) "Mendengarkan..." else "Listening...",
                    color = Color(0xFF63B3ED),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (voiceUiState.partialTranscript.isNotBlank()) {
                    Text(
                        text = "\"${voiceUiState.partialTranscript}\"",
                        color = ObsidianColors.PureSilver,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        return
    }

    val card = voiceUiState.feedbackCard ?: return
    val accent = if (card.isError) ObsidianColors.CrimsonAlert else Color(0xFF68D391)

    Column(
        modifier = modifier
            .fillMaxWidth(0.56f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xF2101319))
            .border(0.8.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable { onDismissCard() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = card.title,
                color = ObsidianColors.PureSilver,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = card.subtitle,
            color = if (card.isError) ObsidianColors.MutedSilver else ObsidianColors.PureSilver,
            fontSize = 11.sp
        )
        card.detailLines.forEach { line ->
            Text(
                text = line,
                color = ObsidianColors.DimSilver,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * AssistiveTouch-inspired draggable frosted-glass rounded square microphone button.
 * Snaps smoothly to the nearest horizontal screen edge on release so it never obscures
 * the central 360-degree Kia Rio viewport.
 */
@Composable
private fun FloatingGlassMicButton(
    isListening: Boolean,
    onClick: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val buttonSizeDp = 52.dp
        val edgeMarginDp = 12.dp
        val buttonSizePx = with(density) { buttonSizeDp.toPx() }
        val edgeMarginPx = with(density) { edgeMarginDp.toPx() }
        val maxXPx = (with(density) { maxWidth.toPx() } - buttonSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
        val maxYPx = (with(density) { maxHeight.toPx() } - buttonSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx)

        var isDragging by remember { mutableStateOf(false) }
        var targetX by remember { mutableFloatStateOf(-1f) }
        var targetY by remember { mutableFloatStateOf(-1f) }

        // Default position: right edge, slightly below vertical center (ergonomic thumb reach)
        val resolvedX = if (targetX < 0f) maxXPx else targetX.coerceIn(edgeMarginPx, maxXPx)
        val resolvedY = if (targetY < 0f) (maxYPx * 0.58f).coerceIn(edgeMarginPx, maxYPx) else targetY.coerceIn(edgeMarginPx, maxYPx)

        val animatedX by animateFloatAsState(
            targetValue = resolvedX,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "floatingMicSnapX"
        )

        val displayX = if (isDragging) resolvedX else animatedX
        val shape = RoundedCornerShape(15.dp)

        val glassBgBrush = if (isListening) {
            Brush.verticalGradient(
                colors = listOf(Color(0xDE193B5A), Color(0xCC0E2236))
            )
        } else if (isDragging) {
            Brush.verticalGradient(
                colors = listOf(Color(0xD42C3648), Color(0xC2181F2B))
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(Color(0xB8252D3D), Color(0xA6141923))
            )
        }

        val glassRimBrush = if (isListening) {
            Brush.verticalGradient(
                colors = listOf(Color(0xEB63B3ED), Color(0x8863B3ED))
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.14f))
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(displayX.roundToInt(), resolvedY.roundToInt()) }
                .size(buttonSizeDp)
                .clip(shape)
                .background(glassBgBrush)
                .border(width = 1.dp, brush = glassRimBrush, shape = shape)
                .pointerInput(maxXPx, maxYPx) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            if (targetX < 0f) targetX = maxXPx
                            if (targetY < 0f) targetY = (maxYPx * 0.58f).coerceIn(edgeMarginPx, maxYPx)
                        },
                        onDragEnd = {
                            isDragging = false
                            // Snap smoothly to nearest horizontal edge like iPhone AssistiveTouch
                            val midX = (edgeMarginPx + maxXPx) * 0.5f
                            targetX = if (targetX < midX) edgeMarginPx else maxXPx
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            targetX = (targetX + dragAmount.x).coerceIn(edgeMarginPx, maxXPx)
                            targetY = (targetY + dragAmount.y).coerceIn(edgeMarginPx, maxYPx)
                        }
                    )
                }
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // Subtle frosted inner glass square (AssistiveTouch depth cue)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isListening) 0.18f else 0.15f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .border(
                        width = 0.7.dp,
                        color = if (isListening) Color(0x8863B3ED) else Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(11.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                MicrophoneVectorIcon(
                    tint = if (isListening) {
                        Color(0xFF63B3ED)
                    } else {
                        ObsidianColors.PureSilver.copy(alpha = 0.94f)
                    },
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

/**
 * Pixel-centered vector microphone icon drawn on Canvas.
 */
@Composable
private fun MicrophoneVectorIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.4.dp.toPx()

        // Capsule head
        val capW = w * 0.38f
        val capH = h * 0.50f
        val capLeft = (w - capW) * 0.5f
        drawRoundRect(
            color = tint,
            topLeft = Offset(capLeft, h * 0.08f),
            size = Size(capW, capH),
            cornerRadius = CornerRadius(capW * 0.5f, capW * 0.5f),
            style = Stroke(width = stroke)
        )

        // U-shaped receiver arc
        val arcW = w * 0.68f
        val arcH = h * 0.44f
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset((w - arcW) * 0.5f, h * 0.28f),
            size = Size(arcW, arcH),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        // Stand stem + base
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.72f),
            end = Offset(w * 0.5f, h * 0.90f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.30f, h * 0.90f),
            end = Offset(w * 0.70f, h * 0.90f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
