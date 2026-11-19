package id.bangsadigital.riosense.model

import androidx.compose.ui.graphics.Color

enum class KiaRioPaintColor(
    val displayName: String,
    val primaryHex: Color,
    val highlightHex: Color,
    val shadowHex: Color
) {
    SNOW_WHITE_PEARL(
        displayName = "Snow White Pearl",
        primaryHex = Color(0xFFF3F5F8),
        highlightHex = Color(0xFFFFFFFF),
        shadowHex = Color(0xFFB8C0CC)
    ),
    BRIGHT_SILVER(
        displayName = "Bright Silver",
        primaryHex = Color(0xFFBFC6CF),
        highlightHex = Color(0xFFE3E8EE),
        shadowHex = Color(0xFF7D8590)
    ),
    SIGNAL_RED(
        displayName = "Signal Red",
        primaryHex = Color(0xFFC81D25),
        highlightHex = Color(0xFFFF4D58),
        shadowHex = Color(0xFF750B10)
    ),
    WENDY_BROWN(
        displayName = "Wendy Brown",
        primaryHex = Color(0xFF6E5343),
        highlightHex = Color(0xFF987662),
        shadowHex = Color(0xFF3E2D23)
    ),
    AURORA_BLACK(
        displayName = "Aurora Black",
        primaryHex = Color(0xFF23272F),
        highlightHex = Color(0xFF454C59),
        shadowHex = Color(0xFF0E1015)
    )
}

data class LightState(
    val parkingLights: Boolean = false,
    val lowBeam: Boolean = false,
    val highBeam: Boolean = false,
    val fogLights: Boolean = false,
    val leftTurn: Boolean = false,
    val rightTurn: Boolean = false,
    val hazard: Boolean = false,
    val brake: Boolean = false
)

data class DoorState(
    val frontLeftOpen: Boolean = false,
    val frontRightOpen: Boolean = false,
    val rearLeftOpen: Boolean = false,
    val rearRightOpen: Boolean = false,
    val trunkOpen: Boolean = false
) {
    val anyDoorOpen: Boolean
        get() = frontLeftOpen || frontRightOpen || rearLeftOpen || rearRightOpen || trunkOpen

    fun openDoorNames(): List<String> = buildList {
        if (frontLeftOpen) add("Front-Left Door (FL)")
        if (frontRightOpen) add("Front-Right Door (FR)")
        if (rearLeftOpen) add("Rear-Left Door (RL)")
        if (rearRightOpen) add("Rear-Right Door (RR)")
        if (trunkOpen) add("Tailgate / Trunk")
    }
}

data class TireInfo(
    val code: String,
    val label: String,
    val pressurePsi: Float,
    val tempC: Int
) {
    fun isPressureAlert(lowPsi: Float, highPsi: Float): Boolean =
        pressurePsi < lowPsi || pressurePsi > highPsi

    fun isTempAlert(highTempC: Int): Boolean =
        tempC >= highTempC

    fun isAlert(lowPsi: Float, highPsi: Float, highTempC: Int): Boolean =
        isPressureAlert(lowPsi, highPsi) || isTempAlert(highTempC)
}

data class TpmsState(
    val frontLeft: TireInfo = TireInfo("FL", "Front Left", 32.0f, 34),
    val frontRight: TireInfo = TireInfo("FR", "Front Right", 32.0f, 35),
    val rearLeft: TireInfo = TireInfo("RL", "Rear Left", 31.5f, 34),
    val rearRight: TireInfo = TireInfo("RR", "Rear Right", 31.5f, 34)
) {
    fun alertTires(lowPsi: Float, highPsi: Float, highTempC: Int): List<TireInfo> =
        listOf(frontLeft, frontRight, rearLeft, rearRight).filter {
            it.isAlert(lowPsi, highPsi, highTempC)
        }
}

data class VehicleTelemetry(
    val speedKmh: Int = 0,
    val rpm: Int = 780,
    val coolantTempC: Int = 88,
    val batteryVoltage: Float = 14.1f,
    val gear: String = "N",
    val centralLocked: Boolean = true,
    val doors: DoorState = DoorState(),
    val lights: LightState = LightState(),
    val tpms: TpmsState = TpmsState(),
    val paintColor: KiaRioPaintColor = KiaRioPaintColor.SNOW_WHITE_PEARL,
    val speedLockAlarmThresholdKmh: Int = 20,
    val tpmsLowThresholdPsi: Float = 26.0f,
    val tpmsHighThresholdPsi: Float = 38.0f,
    val tpmsHighTempC: Int = 40,
    val obdConnected: Boolean = false,
    val simulatorMode: Boolean = true,
    val isMinimized: Boolean = false,
    val audioAlarmMuted: Boolean = false
) {
    val isSpeedUnlockAlarm: Boolean
        get() = speedKmh >= speedLockAlarmThresholdKmh && !centralLocked

    val isMovingWithDoorOpen: Boolean
        get() = speedKmh > 3 && doors.anyDoorOpen

    val isCoolantOverheat: Boolean
        get() = coolantTempC >= 102

    val alertTires: List<TireInfo>
        get() = tpms.alertTires(tpmsLowThresholdPsi, tpmsHighThresholdPsi, tpmsHighTempC)

    val hasAnyCriticalAlert: Boolean
        get() = doors.anyDoorOpen || isSpeedUnlockAlarm || isCoolantOverheat || alertTires.isNotEmpty()

    val activeAlerts: List<String>
        get() = buildList {
            doors.openDoorNames().forEach { doorName ->
                add("DOOR ALERT: $doorName Open")
            }
            if (isSpeedUnlockAlarm) {
                add("SECURITY ALERT: Doors Unlocked at $speedKmh km/h (> $speedLockAlarmThresholdKmh km/h)")
            }
            alertTires.forEach { tire ->
                val parts = buildList {
                    if (tire.pressurePsi < tpmsLowThresholdPsi) {
                        add("Low Pressure (${String.format("%.1f", tire.pressurePsi)} PSI)")
                    } else if (tire.pressurePsi > tpmsHighThresholdPsi) {
                        add("High Pressure (${String.format("%.1f", tire.pressurePsi)} PSI)")
                    }
                    if (tire.tempC >= tpmsHighTempC) {
                        add("High Temp (${tire.tempC}°C)")
                    }
                }
                add("TPMS ALERT: ${tire.label} (${tire.code}) ${parts.joinToString(" & ")}")
            }
            if (isCoolantOverheat) {
                add("ENGINE OVERHEAT: High Coolant Temp ($coolantTempC°C)")
            }
        }
}
