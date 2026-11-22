package id.bangsadigital.riosense.data

import android.content.Context
import id.bangsadigital.riosense.model.DoorState
import id.bangsadigital.riosense.model.KiaRioPaintColor
import id.bangsadigital.riosense.model.LightState
import id.bangsadigital.riosense.model.MaintenanceItem
import id.bangsadigital.riosense.model.MaintenanceState
import id.bangsadigital.riosense.model.ServiceRecord
import id.bangsadigital.riosense.model.ObdActivityEntry
import id.bangsadigital.riosense.model.ObdDirection
import id.bangsadigital.riosense.model.ObdErrorEntry
import id.bangsadigital.riosense.model.ObdErrorSeverity
import id.bangsadigital.riosense.model.TireInfo
import id.bangsadigital.riosense.model.TpmsState
import id.bangsadigital.riosense.model.VehicleTelemetry
import id.bangsadigital.riosense.ui.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class VehicleRepository(context: Context? = null) {

    companion object {
        private const val MAX_ERROR_LOGS = 100
        private const val MAX_ACTIVITY_LOGS = 250
    }

    private val prefs: RioSensePreferences? = context?.let { RioSensePreferences(it) }
    private val idSeq = AtomicLong(1L)
    private var pollingCycleIndex = 0
    private var accumulatedTripKm = 0.0

    // Initial showcase state merged with persisted user settings & paint finish
    private val _telemetry = MutableStateFlow(
        VehicleTelemetry(
            speedKmh = 45,
            rpm = 2400,
            coolantTempC = 88,
            batteryVoltage = 14.2f,
            gear = "3",
            centralLocked = false,
            doors = DoorState(rearRightOpen = true),
            tpms = TpmsState(
                frontLeft = TireInfo("FL", "Front Left", 32.0f, 34),
                frontRight = TireInfo("FR", "Front Right", 32.0f, 35),
                rearLeft = TireInfo("RL", "Rear Left", 22.0f, 41),
                rearRight = TireInfo("RR", "Rear Right", 31.0f, 34)
            ),
            paintColor = KiaRioPaintColor.SNOW_WHITE_PEARL,
            obdConnected = true,
            simulatorMode = true
        ).let { base ->
            prefs?.loadPersistedTelemetryOverrides(base) ?: base
        }
    )
    val telemetry: StateFlow<VehicleTelemetry> = _telemetry.asStateFlow()

    private val _maintenance = MutableStateFlow(
        prefs?.loadMaintenanceState() ?: MaintenanceState(
            items = MaintenanceState.defaultKiaRioItems()
        )
    )
    val maintenance: StateFlow<MaintenanceState> = _maintenance.asStateFlow()

    private val _appLanguage = MutableStateFlow(
        prefs?.loadAppLanguage() ?: AppLanguage.EN
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    fun setAppLanguage(language: AppLanguage) {
        _appLanguage.value = language
        prefs?.saveAppLanguage(language)
    }

    private val _errorLogs = MutableStateFlow<List<ObdErrorEntry>>(emptyList())
    val errorLogs: StateFlow<List<ObdErrorEntry>> = _errorLogs.asStateFlow()

    private val _activityLogs = MutableStateFlow<List<ObdActivityEntry>>(emptyList())
    val activityLogs: StateFlow<List<ObdActivityEntry>> = _activityLogs.asStateFlow()

    private val _activityLogPaused = MutableStateFlow(false)
    val activityLogPaused: StateFlow<Boolean> = _activityLogPaused.asStateFlow()

    init {
        seedInitialObdLogs()
    }

    private fun persistTelemetrySettings() {
        prefs?.saveTelemetrySettings(_telemetry.value)
    }

    private fun updateMaintenance(transform: (MaintenanceState) -> MaintenanceState) {
        _maintenance.update { current ->
            val next = transform(current)
            prefs?.saveMaintenanceState(next)
            next
        }
    }

    private fun currentSourceLabel(state: VehicleTelemetry = _telemetry.value): String =
        if (state.simulatorMode) "Simulator" else "USB OBD-II"

    private fun seedInitialObdLogs() {
        val now = System.currentTimeMillis()
        val src = currentSourceLabel()

        // Seed initial ELM327 / CAN-Bus handshake & first telemetry poll
        val initEntries = listOf(
            ObdActivityEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now - 4200L,
                direction = ObdDirection.TX,
                pid = "ATSP6",
                parameterName = "Select CAN Protocol",
                quickValue = "ISO 15765-4 (500 kbps)",
                ecuHeader = "ELM327",
                rawFrame = "ATSP6",
                decodedDetail = "Configure adapter for Kia Rio UB C-CAN 11-bit 500 kbaud bus.",
                formula = "AT Command -> OK",
                source = src
            ),
            ObdActivityEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now - 4120L,
                direction = ObdDirection.RX,
                pid = "ATSP6",
                parameterName = "Protocol Ready",
                quickValue = "OK",
                ecuHeader = "ELM327",
                rawFrame = "OK>",
                decodedDetail = "CAN 11-bit 500 kbaud protocol active and synchronized.",
                formula = "Adapter ACK",
                source = src
            ),
            ObdActivityEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now - 2500L,
                direction = ObdDirection.RX,
                pid = "010C",
                parameterName = "Engine RPM",
                quickValue = "2400 RPM",
                ecuHeader = "7E8 (ECM)",
                rawFrame = formatRpmFrame(2400),
                decodedDetail = "Engine speed 2400 RPM (Estimated Gear: 3)",
                formula = "((A * 256) + B) / 4",
                source = src
            ),
            ObdActivityEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now - 1800L,
                direction = ObdDirection.RX,
                pid = "010D",
                parameterName = "Vehicle Speed",
                quickValue = "45 km/h",
                ecuHeader = "7E8 (ECM)",
                rawFrame = formatSpeedFrame(45),
                decodedDetail = "Vehicle road speed 45 km/h",
                formula = "Byte A (km/h)",
                source = src
            ),
            ObdActivityEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now - 900L,
                direction = ObdDirection.RX,
                pid = "2101",
                parameterName = "BCM Door & Lock Status",
                quickValue = "RR Open · Unlocked",
                ecuHeader = "778 (BCM)",
                rawFrame = formatBcmDoorFrame(_telemetry.value.doors, _telemetry.value.centralLocked),
                decodedDetail = "Rear-Right door ajar bit set (0x08); Central Lock unlocked (0x00).",
                formula = "Bitmask: FL=01 FR=02 RL=04 RR=08 TRK=10",
                source = src
            )
        ).sortedByDescending { it.timestampMs }

        _activityLogs.value = initEntries

        // Seed initial active faults corresponding to showcase state
        val initialErrors = listOf(
            ObdErrorEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now - 1200L,
                code = "B1603",
                title = "Rear-Right Door Ajar",
                subsystem = "BCM (770 / 778)",
                pid = "Service 21 PID 01",
                rawFrame = formatBcmDoorFrame(_telemetry.value.doors, _telemetry.value.centralLocked),
                severity = ObdErrorSeverity.CRITICAL,
                description = "Body Control Module detected Rear-Right (RR) door latch open while vehicle speed is 45 km/h.",
                recommendedAction = "Stop safely and verify Rear-Right door is firmly closed. Inspect door jamb switch if warning persists.",
                source = src
            ),
            ObdErrorEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now - 2400L,
                code = "C2103",
                title = "Rear-Left Tire Pressure & Temp",
                subsystem = "TPMS Module",
                pid = "Service 22 PID 01",
                rawFrame = "7A8 06 62 01 03 16 29 00",
                severity = ObdErrorSeverity.WARNING,
                description = "Rear-Left (RL) tire pressure dropped to 22.0 PSI (threshold 26.0 PSI) and temperature rose to 41°C.",
                recommendedAction = "Inspect Rear-Left tire for puncture or slow leak and inflate to 32 PSI.",
                source = src
            )
        )
        _errorLogs.value = initialErrors
    }

    fun toggleActivityLogPaused() {
        _activityLogPaused.update { !it }
    }

    fun clearErrorLogs() {
        _errorLogs.value = emptyList()
    }

    fun clearActivityLogs() {
        _activityLogs.value = emptyList()
    }

    fun recordActivity(entry: ObdActivityEntry) {
        if (_activityLogPaused.value) return
        _activityLogs.update { list ->
            (listOf(entry) + list).take(MAX_ACTIVITY_LOGS)
        }
    }

    fun recordError(entry: ObdErrorEntry) {
        _errorLogs.update { list ->
            (listOf(entry) + list).take(MAX_ERROR_LOGS)
        }
    }

    /**
     * Emits a real-time OBD-II TX/RX polling pair matching current telemetry.
     * Called periodically while the app is active so the Activity Log streams continuously.
     */
    fun emitRealtimePollingTick() {
        val state = _telemetry.value
        if (!state.obdConnected) return

        // Auto-accumulate live odometer distance from OBD-II vehicle speed when connected to real car
        if (!state.simulatorMode && state.speedKmh > 0) {
            accumulatedTripKm += (state.speedKmh / 3600.0) * 1.5
            if (accumulatedTripKm >= 1.0) {
                val wholeKm = accumulatedTripKm.toInt()
                accumulatedTripKm -= wholeKm
                updateMaintenance { it.copy(currentOdometerKm = it.currentOdometerKm + wholeKm) }
            }
        }

        if (_activityLogPaused.value) return

        val now = System.currentTimeMillis()
        val src = currentSourceLabel(state)

        when (pollingCycleIndex % 6) {
            0 -> {
                recordTxRx(
                    now = now,
                    pid = "010C",
                    parameterName = "Engine RPM",
                    txHeader = "7E0 (ECM)",
                    txFrame = "7E0 02 01 0C",
                    rxHeader = "7E8 (ECM)",
                    rxFrame = formatRpmFrame(state.rpm),
                    quickValue = "${state.rpm} RPM",
                    decodedDetail = "Engine speed ${state.rpm} RPM · Active Manual Gear: ${state.gear}",
                    formula = "((A * 256) + B) / 4",
                    source = src
                )
            }
            1 -> {
                recordTxRx(
                    now = now,
                    pid = "010D",
                    parameterName = "Vehicle Speed",
                    txHeader = "7E0 (ECM)",
                    txFrame = "7E0 02 01 0D",
                    rxHeader = "7E8 (ECM)",
                    rxFrame = formatSpeedFrame(state.speedKmh),
                    quickValue = "${state.speedKmh} km/h",
                    decodedDetail = "Vehicle speed ${state.speedKmh} km/h (Lock alarm threshold: ${state.speedLockAlarmThresholdKmh} km/h)",
                    formula = "Byte A (km/h)",
                    source = src
                )
            }
            2 -> {
                recordTxRx(
                    now = now,
                    pid = "0105",
                    parameterName = "Coolant Temperature",
                    txHeader = "7E0 (ECM)",
                    txFrame = "7E0 02 01 05",
                    rxHeader = "7E8 (ECM)",
                    rxFrame = formatCoolantFrame(state.coolantTempC),
                    quickValue = "${state.coolantTempC} °C",
                    decodedDetail = "Engine coolant temperature ${state.coolantTempC} °C (${if (state.isCoolantOverheat) "OVERHEAT" else "Nominal"})",
                    formula = "Byte A - 40 (°C)",
                    source = src
                )
            }
            3 -> {
                val doorSummary = if (state.doors.anyDoorOpen) {
                    "${state.doors.openDoorNames().size} Open"
                } else {
                    "All Closed"
                }
                val lockSummary = if (state.centralLocked) "Locked" else "Unlocked"
                recordTxRx(
                    now = now,
                    pid = "2101",
                    parameterName = "BCM Doors & Lock",
                    txHeader = "770 (BCM)",
                    txFrame = "770 02 21 01",
                    rxHeader = "778 (BCM)",
                    rxFrame = formatBcmDoorFrame(state.doors, state.centralLocked),
                    quickValue = "$doorSummary · $lockSummary",
                    decodedDetail = "Doors: ${if (state.doors.anyDoorOpen) state.doors.openDoorNames().joinToString(", ") else "All Closed"} · Central Lock: $lockSummary",
                    formula = "Bitmask: FL=01 FR=02 RL=04 RR=08 TRK=10 | Lock=01",
                    source = src
                )
            }
            4 -> {
                val activeLights = summarizeLights(state.lights)
                recordTxRx(
                    now = now,
                    pid = "2102",
                    parameterName = "BCM Exterior Lights",
                    txHeader = "770 (BCM)",
                    txFrame = "770 02 21 02",
                    rxHeader = "778 (BCM)",
                    rxFrame = formatBcmLightFrame(state.lights),
                    quickValue = activeLights,
                    decodedDetail = "Exterior lamp state: $activeLights",
                    formula = "Bitmask: Park=01 Low=02 High=04 Fog=08 LT=10 RT=20 Haz=40 Brk=80",
                    source = src
                )
            }
            else -> {
                val voltStr = String.format("%.1f V", state.batteryVoltage)
                recordTxRx(
                    now = now,
                    pid = "ATRV",
                    parameterName = "Battery Voltage",
                    txHeader = "ELM327",
                    txFrame = "ATRV",
                    rxHeader = "ELM327",
                    rxFrame = String.format("%.1fV>", state.batteryVoltage),
                    quickValue = voltStr,
                    decodedDetail = "OBD-II Pin 16 battery voltage reading: $voltStr",
                    formula = "Adapter ADC voltage reading",
                    source = src
                )
            }
        }
        pollingCycleIndex = (pollingCycleIndex + 1) % 6
    }

    private fun recordTxRx(
        now: Long,
        pid: String,
        parameterName: String,
        txHeader: String,
        txFrame: String,
        rxHeader: String,
        rxFrame: String,
        quickValue: String,
        decodedDetail: String,
        formula: String,
        source: String
    ) {
        val tx = ObdActivityEntry(
            id = idSeq.getAndIncrement(),
            timestampMs = now,
            direction = ObdDirection.TX,
            pid = pid,
            parameterName = parameterName,
            quickValue = "Request $pid",
            ecuHeader = txHeader,
            rawFrame = txFrame,
            decodedDetail = "Polling request sent for $parameterName (PID $pid).",
            formula = formula,
            source = source
        )
        val rx = ObdActivityEntry(
            id = idSeq.getAndIncrement(),
            timestampMs = now + 18L,
            direction = ObdDirection.RX,
            pid = pid,
            parameterName = parameterName,
            quickValue = quickValue,
            ecuHeader = rxHeader,
            rawFrame = rxFrame,
            decodedDetail = decodedDetail,
            formula = formula,
            source = source
        )
        _activityLogs.update { list ->
            (listOf(rx, tx) + list).take(MAX_ACTIVITY_LOGS)
        }
    }

    /**
     * Injects a standard Kia Rio UB OBD-II Diagnostic Trouble Code (DTC) for simulator testing.
     */
    fun injectSimulatedDtc(dtcCode: String) {
        val now = System.currentTimeMillis()
        val src = currentSourceLabel()
        val entry = when (dtcCode) {
            "P0300" -> ObdErrorEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now,
                code = "P0300",
                title = "Random / Multiple Cylinder Misfire",
                subsystem = "ECM (7E0 / 7E8)",
                pid = "Mode 03 DTC",
                rawFrame = "7E8 04 43 01 03 00",
                severity = ObdErrorSeverity.CRITICAL,
                description = "Powertrain ECU detected irregular combustion misfires across multiple cylinders on 1.4L Gamma engine.",
                recommendedAction = "Inspect ignition coils, spark plugs, fuel injectors, and intake vacuum lines.",
                source = src
            )
            "P0118" -> ObdErrorEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now,
                code = "P0118",
                title = "ECT Sensor Circuit High Input",
                subsystem = "ECM (7E0 / 7E8)",
                pid = "Mode 03 DTC",
                rawFrame = "7E8 04 43 01 01 18",
                severity = ObdErrorSeverity.CRITICAL,
                description = "Engine Coolant Temperature (ECT) sensor voltage exceeded upper specification (> 4.8V).",
                recommendedAction = "Check ECT sensor connector, wiring harness continuity, and coolant level.",
                source = src
            )
            "C1202" -> ObdErrorEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now,
                code = "C1202",
                title = "ABS Front-Left Wheel Speed Sensor",
                subsystem = "ABS / ESC (7D1)",
                pid = "Mode 03 DTC",
                rawFrame = "7D9 04 43 01 52 02",
                severity = ObdErrorSeverity.WARNING,
                description = "Intermittent or invalid signal from Front-Left wheel speed sensor tone ring.",
                recommendedAction = "Clean wheel speed sensor tip and inspect front-left ABS wiring harness.",
                source = src
            )
            else -> ObdErrorEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now,
                code = "B1213",
                title = "BCM Central Lock Actuator Fault",
                subsystem = "BCM (770 / 778)",
                pid = "Service 19 DTC",
                rawFrame = "778 05 59 02 92 13 08",
                severity = ObdErrorSeverity.WARNING,
                description = "Body Control Module detected delayed feedback from driver door central locking actuator.",
                recommendedAction = "Verify door lock actuator operation and check BCM fuse box relay.",
                source = src
            )
        }
        recordError(entry)
        recordActivity(
            ObdActivityEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now,
                direction = ObdDirection.RX,
                pid = "03",
                parameterName = "Stored DTC Response",
                quickValue = "${entry.code} (${entry.severity.label})",
                ecuHeader = entry.subsystem,
                rawFrame = entry.rawFrame,
                decodedDetail = "${entry.code}: ${entry.title} — ${entry.description}",
                formula = "OBD-II Mode 03 / UDS 19 DTC Decode",
                source = src
            )
        )
    }

    private fun updateWithAutoWake(transform: (VehicleTelemetry) -> VehicleTelemetry) {
        _telemetry.update { current ->
            val hadAlertBefore = current.hasAnyCriticalAlert
            val next = transform(current)
            // Auto-expand dashboard from minimized state when a new critical alert triggers
            if (current.isMinimized && !hadAlertBefore && next.hasAnyCriticalAlert) {
                next.copy(isMinimized = false)
            } else {
                next
            }
        }
    }

    fun setSpeedAndRpm(speedKmh: Int, rpm: Int) {
        val prev = _telemetry.value
        val clampedSpeed = speedKmh.coerceIn(0, 220)
        val clampedRpm = rpm.coerceIn(0, 7000)
        val gear = ObdCommandEngine.estimateManualGear(clampedSpeed, clampedRpm)
        updateWithAutoWake {
            it.copy(speedKmh = clampedSpeed, rpm = clampedRpm, gear = gear)
        }
        val next = _telemetry.value
        val now = System.currentTimeMillis()
        val src = currentSourceLabel(next)
        recordActivity(
            ObdActivityEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now,
                direction = ObdDirection.RX,
                pid = "010D",
                parameterName = "Vehicle Speed",
                quickValue = "$clampedSpeed km/h · $clampedRpm RPM",
                ecuHeader = "7E8 (ECM)",
                rawFrame = formatSpeedFrame(clampedSpeed),
                decodedDetail = "Speed $clampedSpeed km/h, RPM $clampedRpm, Estimated Gear: $gear",
                formula = "Speed=A | RPM=((A*256)+B)/4",
                source = src
            )
        )
        if (!prev.isSpeedUnlockAlarm && next.isSpeedUnlockAlarm) {
            recordError(
                ObdErrorEntry(
                    id = idSeq.getAndIncrement(),
                    timestampMs = now,
                    code = "B1610",
                    title = "Doors Unlocked at Speed",
                    subsystem = "BCM (770 / 778)",
                    pid = "Service 21 PID 01",
                    rawFrame = formatBcmDoorFrame(next.doors, next.centralLocked),
                    severity = ObdErrorSeverity.WARNING,
                    description = "Vehicle reached $clampedSpeed km/h (threshold ${next.speedLockAlarmThresholdKmh} km/h) with central door locks disengaged.",
                    recommendedAction = "Engage central door lock switch for passenger safety.",
                    source = src
                )
            )
        }
    }

    fun setCoolantTemp(tempC: Int) {
        val prev = _telemetry.value
        val clamped = tempC.coerceIn(40, 130)
        updateWithAutoWake {
            it.copy(coolantTempC = clamped)
        }
        val next = _telemetry.value
        val now = System.currentTimeMillis()
        val src = currentSourceLabel(next)
        recordActivity(
            ObdActivityEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now,
                direction = ObdDirection.RX,
                pid = "0105",
                parameterName = "Coolant Temperature",
                quickValue = "$clamped °C",
                ecuHeader = "7E8 (ECM)",
                rawFrame = formatCoolantFrame(clamped),
                decodedDetail = "Engine coolant temperature updated to $clamped °C",
                formula = "Byte A - 40 (°C)",
                source = src
            )
        )
        if (!prev.isCoolantOverheat && next.isCoolantOverheat) {
            recordError(
                ObdErrorEntry(
                    id = idSeq.getAndIncrement(),
                    timestampMs = now,
                    code = "P0217",
                    title = "Engine Coolant Overtemperature",
                    subsystem = "ECM (7E0 / 7E8)",
                    pid = "PID 0105",
                    rawFrame = formatCoolantFrame(clamped),
                    severity = ObdErrorSeverity.CRITICAL,
                    description = "Engine coolant temperature reached $clamped °C (threshold ${next.coolantOverheatThresholdC} °C).",
                    recommendedAction = "Pull over safely, idle engine to cool down, and check radiator fan & coolant reservoir.",
                    source = src
                )
            )
        }
    }

    fun toggleCentralLock() {
        updateWithAutoWake {
            it.copy(centralLocked = !it.centralLocked)
        }
        val next = _telemetry.value
        val now = System.currentTimeMillis()
        val src = currentSourceLabel(next)
        recordActivity(
            ObdActivityEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now,
                direction = ObdDirection.RX,
                pid = "2101",
                parameterName = "BCM Central Lock",
                quickValue = if (next.centralLocked) "Doors Locked" else "Doors Unlocked",
                ecuHeader = "778 (BCM)",
                rawFrame = formatBcmDoorFrame(next.doors, next.centralLocked),
                decodedDetail = "Central lock state changed to ${if (next.centralLocked) "LOCKED (0x01)" else "UNLOCKED (0x00)"}.",
                formula = "Lock Byte & 0x01",
                source = src
            )
        )
    }

    fun toggleDoor(doorCode: String) {
        updateWithAutoWake { state ->
            val d = state.doors
            val updated = when (doorCode) {
                "FL" -> d.copy(frontLeftOpen = !d.frontLeftOpen)
                "FR" -> d.copy(frontRightOpen = !d.frontRightOpen)
                "RL" -> d.copy(rearLeftOpen = !d.rearLeftOpen)
                "RR" -> d.copy(rearRightOpen = !d.rearRightOpen)
                "TRUNK" -> d.copy(trunkOpen = !d.trunkOpen)
                else -> d
            }
            state.copy(doors = updated)
        }
        val next = _telemetry.value
        val now = System.currentTimeMillis()
        val src = currentSourceLabel(next)
        val isNowOpen = when (doorCode) {
            "FL" -> next.doors.frontLeftOpen
            "FR" -> next.doors.frontRightOpen
            "RL" -> next.doors.rearLeftOpen
            "RR" -> next.doors.rearRightOpen
            "TRUNK" -> next.doors.trunkOpen
            else -> false
        }
        val doorLabel = when (doorCode) {
            "FL" -> "Front-Left Door (FL)"
            "FR" -> "Front-Right Door (FR)"
            "RL" -> "Rear-Left Door (RL)"
            "RR" -> "Rear-Right Door (RR)"
            "TRUNK" -> "Tailgate / Trunk"
            else -> doorCode
        }
        recordActivity(
            ObdActivityEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now,
                direction = ObdDirection.RX,
                pid = "2101",
                parameterName = "BCM Door Switch",
                quickValue = "$doorCode ${if (isNowOpen) "OPEN" else "CLOSED"}",
                ecuHeader = "778 (BCM)",
                rawFrame = formatBcmDoorFrame(next.doors, next.centralLocked),
                decodedDetail = "$doorLabel switched to ${if (isNowOpen) "OPEN" else "CLOSED"}.",
                formula = "Bitmask: FL=01 FR=02 RL=04 RR=08 TRK=10",
                source = src
            )
        )
        if (isNowOpen) {
            val dtc = when (doorCode) {
                "FL" -> "B1600"
                "FR" -> "B1601"
                "RL" -> "B1602"
                "RR" -> "B1603"
                else -> "B1604"
            }
            recordError(
                ObdErrorEntry(
                    id = idSeq.getAndIncrement(),
                    timestampMs = now,
                    code = dtc,
                    title = "$doorLabel Ajar",
                    subsystem = "BCM (770 / 778)",
                    pid = "Service 21 PID 01",
                    rawFrame = formatBcmDoorFrame(next.doors, next.centralLocked),
                    severity = if (next.speedKmh > 3) ObdErrorSeverity.CRITICAL else ObdErrorSeverity.WARNING,
                    description = "$doorLabel switch reported open state at ${next.speedKmh} km/h.",
                    recommendedAction = "Ensure $doorLabel is closed securely.",
                    source = src
                )
            )
        }
    }

    fun setTirePressure(code: String, psi: Float) {
        val prevTire = getTire(_telemetry.value.tpms, code)
        val clamped = psi.coerceIn(15.0f, 42.0f)
        updateWithAutoWake { state ->
            val t = state.tpms
            val updated = when (code) {
                "FL" -> t.copy(frontLeft = t.frontLeft.copy(pressurePsi = clamped))
                "FR" -> t.copy(frontRight = t.frontRight.copy(pressurePsi = clamped))
                "RL" -> t.copy(rearLeft = t.rearLeft.copy(pressurePsi = clamped))
                "RR" -> t.copy(rearRight = t.rearRight.copy(pressurePsi = clamped))
                else -> t
            }
            state.copy(tpms = updated)
        }
        val next = _telemetry.value
        val nextTire = getTire(next.tpms, code)
        val now = System.currentTimeMillis()
        val src = currentSourceLabel(next)
        val wasAlert = prevTire.isPressureAlert(next.tpmsLowThresholdPsi, next.tpmsHighThresholdPsi)
        val isAlert = nextTire.isPressureAlert(next.tpmsLowThresholdPsi, next.tpmsHighThresholdPsi)
        if (!wasAlert && isAlert) {
            val psiStr = String.format("%.1f PSI", clamped)
            val isLow = clamped < next.tpmsLowThresholdPsi
            recordError(
                ObdErrorEntry(
                    id = idSeq.getAndIncrement(),
                    timestampMs = now,
                    code = tpmsDtcCode(code),
                    title = "${nextTire.label} ${if (isLow) "Low" else "High"} Pressure",
                    subsystem = "TPMS Module",
                    pid = "Service 22 PID 01",
                    rawFrame = String.format("7A8 06 62 01 %02X %02X %02X 00", tpmsIndex(code), clamped.toInt(), nextTire.tempC),
                    severity = ObdErrorSeverity.WARNING,
                    description = "${nextTire.label} ($code) pressure is $psiStr (safe window ${next.tpmsLowThresholdPsi.toInt()}–${next.tpmsHighThresholdPsi.toInt()} PSI).",
                    recommendedAction = "Check ${nextTire.label} tire and adjust inflation pressure to 32 PSI.",
                    source = src
                )
            )
        }
    }

    fun setTireTemp(code: String, tempC: Int) {
        val prevTire = getTire(_telemetry.value.tpms, code)
        val clampedTemp = tempC.coerceIn(20, 70)
        updateWithAutoWake { state ->
            val t = state.tpms
            val updated = when (code) {
                "FL" -> t.copy(frontLeft = t.frontLeft.copy(tempC = clampedTemp))
                "FR" -> t.copy(frontRight = t.frontRight.copy(tempC = clampedTemp))
                "RL" -> t.copy(rearLeft = t.rearLeft.copy(tempC = clampedTemp))
                "RR" -> t.copy(rearRight = t.rearRight.copy(tempC = clampedTemp))
                else -> t
            }
            state.copy(tpms = updated)
        }
        val next = _telemetry.value
        val nextTire = getTire(next.tpms, code)
        val now = System.currentTimeMillis()
        val src = currentSourceLabel(next)
        if (!prevTire.isTempAlert(next.tpmsHighTempC) && nextTire.isTempAlert(next.tpmsHighTempC)) {
            recordError(
                ObdErrorEntry(
                    id = idSeq.getAndIncrement(),
                    timestampMs = now,
                    code = tpmsDtcCode(code),
                    title = "${nextTire.label} High Tire Temp",
                    subsystem = "TPMS Module",
                    pid = "Service 22 PID 01",
                    rawFrame = String.format("7A8 06 62 01 %02X %02X %02X 00", tpmsIndex(code), nextTire.pressurePsi.toInt(), clampedTemp),
                    severity = ObdErrorSeverity.WARNING,
                    description = "${nextTire.label} ($code) temperature reached $clampedTemp °C (threshold ${next.tpmsHighTempC} °C).",
                    recommendedAction = "Reduce speed and inspect ${nextTire.label} tire & brake caliper for excessive heat.",
                    source = src
                )
            )
        }
    }

    fun setPaintColor(color: KiaRioPaintColor) {
        _telemetry.update { it.copy(paintColor = color) }
        persistTelemetrySettings()
    }

    fun toggleLight(lightCode: String) {
        _telemetry.update { state ->
            val l = state.lights
            val updated = when (lightCode) {
                "PARKING" -> l.copy(parkingLights = !l.parkingLights)
                "LOW_BEAM" -> l.copy(lowBeam = !l.lowBeam)
                "HIGH_BEAM" -> l.copy(highBeam = !l.highBeam)
                "FOG" -> l.copy(fogLights = !l.fogLights)
                "LEFT_TURN" -> {
                    val next = !l.leftTurn
                    l.copy(leftTurn = next, rightTurn = if (next) false else l.rightTurn)
                }
                "RIGHT_TURN" -> {
                    val next = !l.rightTurn
                    l.copy(rightTurn = next, leftTurn = if (next) false else l.leftTurn)
                }
                "HAZARD" -> l.copy(hazard = !l.hazard)
                "BRAKE" -> l.copy(brake = !l.brake)
                else -> l
            }
            state.copy(lights = updated)
        }
        val next = _telemetry.value
        val now = System.currentTimeMillis()
        recordActivity(
            ObdActivityEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now,
                direction = ObdDirection.RX,
                pid = "2102",
                parameterName = "BCM Exterior Lights",
                quickValue = "$lightCode · ${summarizeLights(next.lights)}",
                ecuHeader = "778 (BCM)",
                rawFrame = formatBcmLightFrame(next.lights),
                decodedDetail = "Toggled $lightCode. Active lamps: ${summarizeLights(next.lights)}",
                formula = "Bitmask: Park=01 Low=02 High=04 Fog=08 LT=10 RT=20 Haz=40 Brk=80",
                source = currentSourceLabel(next)
            )
        )
    }

    fun setMinimized(minimized: Boolean) {
        _telemetry.update { it.copy(isMinimized = minimized) }
    }

    fun toggleSimulatorMode() {
        _telemetry.update { it.copy(simulatorMode = !it.simulatorMode) }
    }

    fun toggleAudioMute() {
        _telemetry.update { it.copy(audioAlarmMuted = !it.audioAlarmMuted) }
        persistTelemetrySettings()
    }

    fun setSpeedLockThreshold(kmh: Int) {
        updateWithAutoWake {
            it.copy(speedLockAlarmThresholdKmh = kmh.coerceIn(10, 60))
        }
        persistTelemetrySettings()
    }

    fun setTpmsLowThreshold(psi: Float) {
        updateWithAutoWake {
            val clamped = ((psi.coerceIn(18f, 32f) * 2f).toInt() / 2f)
            it.copy(tpmsLowThresholdPsi = clamped)
        }
        persistTelemetrySettings()
    }

    fun setTpmsHighThreshold(psi: Float) {
        updateWithAutoWake {
            val clamped = ((psi.coerceIn(34f, 45f) * 2f).toInt() / 2f)
            it.copy(tpmsHighThresholdPsi = clamped)
        }
        persistTelemetrySettings()
    }

    fun setTpmsHighTempThreshold(tempC: Int) {
        updateWithAutoWake {
            it.copy(tpmsHighTempC = tempC.coerceIn(35, 65))
        }
        persistTelemetrySettings()
    }

    fun setCoolantOverheatThreshold(tempC: Int) {
        updateWithAutoWake {
            it.copy(coolantOverheatThresholdC = tempC.coerceIn(92, 118))
        }
        persistTelemetrySettings()
    }

    fun setBatteryLowThreshold(volts: Float) {
        updateWithAutoWake {
            val clamped = ((volts.coerceIn(10.5f, 12.6f) * 10f).toInt() / 10f)
            it.copy(batteryLowThresholdV = clamped)
        }
        persistTelemetrySettings()
    }

    fun setBatteryHighThreshold(volts: Float) {
        updateWithAutoWake {
            val clamped = ((volts.coerceIn(14.4f, 16.0f) * 10f).toInt() / 10f)
            it.copy(batteryHighThresholdV = clamped)
        }
        persistTelemetrySettings()
    }

    /**
     * Updates simulated or live OBD-II Pin 16 battery voltage and logs alert transitions.
     */
    fun setBatteryVoltage(volts: Float) {
        val prev = _telemetry.value
        val clamped = ((volts.coerceIn(10.0f, 16.0f) * 10f).toInt() / 10f)
        updateWithAutoWake {
            it.copy(batteryVoltage = clamped)
        }
        val next = _telemetry.value
        val now = System.currentTimeMillis()
        val src = currentSourceLabel(next)
        val voltStr = String.format("%.1f V", clamped)
        recordActivity(
            ObdActivityEntry(
                id = idSeq.getAndIncrement(),
                timestampMs = now,
                direction = ObdDirection.RX,
                pid = "ATRV",
                parameterName = "Battery Voltage",
                quickValue = voltStr,
                ecuHeader = "ELM327",
                rawFrame = String.format("%.1fV>", clamped),
                decodedDetail = "Battery voltage updated to $voltStr (safe window ${String.format("%.1f", next.batteryLowThresholdV)}–${String.format("%.1f", next.batteryHighThresholdV)} V).",
                formula = "Adapter ADC voltage reading",
                source = src
            )
        )
        if (!prev.isBatteryLow && next.isBatteryLow) {
            recordError(
                ObdErrorEntry(
                    id = idSeq.getAndIncrement(),
                    timestampMs = now,
                    code = "P0562",
                    title = "System Voltage Low",
                    subsystem = "ECM / Charging System",
                    pid = "ATRV",
                    rawFrame = String.format("%.1fV>", clamped),
                    severity = ObdErrorSeverity.CRITICAL,
                    description = "Battery voltage dropped to $voltStr (at or below ${String.format("%.1f", next.batteryLowThresholdV)} V threshold).",
                    recommendedAction = "Inspect battery health, alternator charging output, and battery terminal clamps.",
                    source = src
                )
            )
        } else if (!prev.isBatteryHigh && next.isBatteryHigh) {
            recordError(
                ObdErrorEntry(
                    id = idSeq.getAndIncrement(),
                    timestampMs = now,
                    code = "P0563",
                    title = "System Voltage High",
                    subsystem = "ECM / Charging System",
                    pid = "ATRV",
                    rawFrame = String.format("%.1fV>", clamped),
                    severity = ObdErrorSeverity.CRITICAL,
                    description = "Battery voltage spiked to $voltStr (at or above ${String.format("%.1f", next.batteryHighThresholdV)} V threshold).",
                    recommendedAction = "Have alternator voltage regulator checked to prevent ECU/lamp overvoltage damage.",
                    source = src
                )
            )
        }
    }

    /**
     * Restores all alarm thresholds to Kia Rio UB recommended standard defaults and persists them.
     */
    fun resetAlertSettingsToStandard() {
        updateWithAutoWake {
            it.copy(
                speedLockAlarmThresholdKmh = VehicleTelemetry.DEFAULT_SPEED_LOCK_THRESHOLD_KMH,
                tpmsLowThresholdPsi = VehicleTelemetry.DEFAULT_TPMS_LOW_PSI,
                tpmsHighThresholdPsi = VehicleTelemetry.DEFAULT_TPMS_HIGH_PSI,
                tpmsHighTempC = VehicleTelemetry.DEFAULT_TPMS_HIGH_TEMP_C,
                coolantOverheatThresholdC = VehicleTelemetry.DEFAULT_COOLANT_OVERHEAT_C,
                batteryLowThresholdV = VehicleTelemetry.DEFAULT_BATTERY_LOW_V,
                batteryHighThresholdV = VehicleTelemetry.DEFAULT_BATTERY_HIGH_V
            )
        }
        persistTelemetrySettings()
    }

    // --- Fast-Moving Parts & Maintenance Reminder Management (Persisted Locally) ---

    fun updateOdometerKm(odometerKm: Int) = updateMaintenance {
        it.copy(currentOdometerKm = odometerKm.coerceIn(0, 999_999))
    }

    fun setMaintenanceRemindDays(days: Int) = updateMaintenance {
        it.copy(remindDaysBefore = days.coerceIn(1, 30))
    }

    fun setMaintenanceRemindKm(km: Int) = updateMaintenance {
        it.copy(remindKmBefore = km.coerceIn(100, 3000))
    }

    /**
     * Logs a new permanent service history record (supports today or backdated entries,
     * exact odometer, optional cost in IDR, and optional part/brand note).
     */
    fun logMaintenanceServiceRecord(
        itemId: String,
        dateMs: Long,
        odometerKm: Int,
        costIdr: Long,
        note: String
    ) {
        val clampedOdo = odometerKm.coerceIn(0, 999_999)
        val clampedCost = costIdr.coerceAtLeast(0L)
        val trimmedNote = note.trim()
        val record = ServiceRecord(
            id = "srv_${UUID.randomUUID().toString().take(8)}",
            dateMs = dateMs,
            odometerKm = clampedOdo,
            costIdr = clampedCost,
            note = trimmedNote
        )
        updateMaintenance { state ->
            val updatedOdometer = maxOf(state.currentOdometerKm, clampedOdo)
            state.copy(
                currentOdometerKm = updatedOdometer,
                items = state.items.map { item ->
                    if (item.id == itemId) {
                        val nextHistory = (listOf(record) + item.history)
                            .sortedByDescending { it.dateMs }
                        val latest = nextHistory.first()
                        item.copy(
                            lastServiceDateMs = latest.dateMs,
                            lastServiceOdometerKm = latest.odometerKm,
                            history = nextHistory
                        )
                    } else {
                        item
                    }
                }
            )
        }
    }

    /**
     * Deletes a specific history record from a part and recalculates its latest service date/odometer.
     */
    fun deleteMaintenanceHistoryRecord(itemId: String, recordId: String) = updateMaintenance { state ->
        state.copy(
            items = state.items.map { item ->
                if (item.id == itemId) {
                    val remaining = item.history.filterNot { it.id == recordId }
                    val latest = remaining.firstOrNull()
                    item.copy(
                        lastServiceDateMs = latest?.dateMs ?: item.lastServiceDateMs,
                        lastServiceOdometerKm = latest?.odometerKm ?: item.lastServiceOdometerKm,
                        history = remaining
                    )
                } else {
                    item
                }
            }
        )
    }

    fun updateMaintenanceItemSchedule(
        itemId: String,
        intervalMonths: Int,
        intervalKm: Int
    ) = updateMaintenance { state ->
        state.copy(
            items = state.items.map { item ->
                if (item.id == itemId) {
                    item.copy(
                        intervalMonths = intervalMonths.coerceIn(0, 60),
                        intervalKm = intervalKm.coerceIn(0, 100_000)
                    )
                } else {
                    item
                }
            }
        )
    }

    fun addCustomMaintenanceItem(
        name: String,
        intervalMonths: Int,
        intervalKm: Int,
        initialDateMs: Long,
        initialOdometerKm: Int,
        initialCostIdr: Long,
        initialNote: String
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val clampedOdo = initialOdometerKm.coerceIn(0, 999_999)
        val clampedCost = initialCostIdr.coerceAtLeast(0L)
        val firstRecord = ServiceRecord(
            id = "srv_${UUID.randomUUID().toString().take(8)}",
            dateMs = initialDateMs,
            odometerKm = clampedOdo,
            costIdr = clampedCost,
            note = initialNote.trim()
        )
        updateMaintenance { state ->
            val newItem = MaintenanceItem(
                id = "custom_${UUID.randomUUID().toString().take(8)}",
                name = trimmed,
                category = "Custom",
                lastServiceDateMs = initialDateMs,
                lastServiceOdometerKm = clampedOdo,
                intervalMonths = intervalMonths.coerceIn(0, 60),
                intervalKm = intervalKm.coerceIn(0, 100_000),
                isCustom = true,
                history = listOf(firstRecord)
            )
            state.copy(
                currentOdometerKm = maxOf(state.currentOdometerKm, clampedOdo),
                items = state.items + newItem
            )
        }
    }

    fun deleteCustomMaintenanceItem(itemId: String) = updateMaintenance { state ->
        state.copy(items = state.items.filterNot { it.id == itemId && it.isCustom })
    }

    fun resetMaintenanceToStandard() = updateMaintenance { state ->
        MaintenanceState(
            currentOdometerKm = state.currentOdometerKm,
            remindDaysBefore = MaintenanceState.DEFAULT_REMIND_DAYS_BEFORE,
            remindKmBefore = MaintenanceState.DEFAULT_REMIND_KM_BEFORE,
            items = MaintenanceState.defaultKiaRioItems(baseOdometerKm = state.currentOdometerKm)
        )
    }

    /** Preset 1: All Systems Nominal & Locked */
    fun applyNormalPreset() {
        _telemetry.update {
            it.copy(
                speedKmh = 62,
                rpm = 1950,
                coolantTempC = 88,
                batteryVoltage = 14.1f,
                gear = ObdCommandEngine.estimateManualGear(62, 1950),
                centralLocked = true,
                doors = DoorState(),
                tpms = TpmsState(
                    frontLeft = TireInfo("FL", "Front Left", 32.0f, 34),
                    frontRight = TireInfo("FR", "Front Right", 32.0f, 34),
                    rearLeft = TireInfo("RL", "Rear Left", 31.5f, 34),
                    rearRight = TireInfo("RR", "Rear Right", 31.5f, 34)
                )
            )
        }
    }

    /** Preset 2: Demo Rear-Right Door Open + Rear-Left Low Tire Pressure/High Temp + Unlocked */
    fun applyAlertShowcasePreset() {
        updateWithAutoWake {
            it.copy(
                speedKmh = 45,
                rpm = 2400,
                coolantTempC = 88,
                gear = ObdCommandEngine.estimateManualGear(45, 2400),
                centralLocked = false,
                doors = DoorState(rearRightOpen = true),
                tpms = TpmsState(
                    frontLeft = TireInfo("FL", "Front Left", 32.0f, 34),
                    frontRight = TireInfo("FR", "Front Right", 32.0f, 35),
                    rearLeft = TireInfo("RL", "Rear Left", 22.0f, 41),
                    rearRight = TireInfo("RR", "Rear Right", 31.0f, 34)
                )
            )
        }
    }

    private fun getTire(tpms: TpmsState, code: String): TireInfo = when (code) {
        "FL" -> tpms.frontLeft
        "FR" -> tpms.frontRight
        "RL" -> tpms.rearLeft
        else -> tpms.rearRight
    }

    private fun tpmsDtcCode(code: String): String = when (code) {
        "FL" -> "C2101"
        "FR" -> "C2102"
        "RL" -> "C2103"
        else -> "C2104"
    }

    private fun tpmsIndex(code: String): Int = when (code) {
        "FL" -> 1
        "FR" -> 2
        "RL" -> 3
        else -> 4
    }

    private fun formatRpmFrame(rpm: Int): String {
        val raw = (rpm * 4).coerceIn(0, 65535)
        val a = (raw shr 8) and 0xFF
        val b = raw and 0xFF
        return String.format("7E8 04 41 0C %02X %02X", a, b)
    }

    private fun formatSpeedFrame(speedKmh: Int): String {
        val a = speedKmh.coerceIn(0, 255)
        return String.format("7E8 03 41 0D %02X", a)
    }

    private fun formatCoolantFrame(tempC: Int): String {
        val a = (tempC + 40).coerceIn(0, 255)
        return String.format("7E8 03 41 05 %02X", a)
    }

    private fun formatBcmDoorFrame(doors: DoorState, locked: Boolean): String {
        var doorByte = 0
        if (doors.frontLeftOpen) doorByte = doorByte or 0x01
        if (doors.frontRightOpen) doorByte = doorByte or 0x02
        if (doors.rearLeftOpen) doorByte = doorByte or 0x04
        if (doors.rearRightOpen) doorByte = doorByte or 0x08
        if (doors.trunkOpen) doorByte = doorByte or 0x10
        val lockByte = if (locked) 0x01 else 0x00
        return String.format("778 04 61 01 %02X %02X", doorByte, lockByte)
    }

    private fun formatBcmLightFrame(lights: LightState): String {
        var mask = 0
        if (lights.parkingLights) mask = mask or 0x01
        if (lights.lowBeam) mask = mask or 0x02
        if (lights.highBeam) mask = mask or 0x04
        if (lights.fogLights) mask = mask or 0x08
        if (lights.leftTurn) mask = mask or 0x10
        if (lights.rightTurn) mask = mask or 0x20
        if (lights.hazard) mask = mask or 0x40
        if (lights.brake) mask = mask or 0x80
        return String.format("778 03 61 02 %02X", mask)
    }

    private fun summarizeLights(lights: LightState): String {
        val active = buildList {
            if (lights.parkingLights) add("Park")
            if (lights.lowBeam) add("Low")
            if (lights.highBeam) add("High")
            if (lights.fogLights) add("Fog")
            if (lights.hazard) add("Hazard")
            else {
                if (lights.leftTurn) add("L-Turn")
                if (lights.rightTurn) add("R-Turn")
            }
            if (lights.brake) add("Brake")
        }
        return if (active.isEmpty()) "All Off" else active.joinToString(", ")
    }
}
