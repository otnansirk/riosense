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
        if (frontLeftOpen) add("Pintu Depan Kiri (FL)")
        if (frontRightOpen) add("Pintu Depan Kanan (FR)")
        if (rearLeftOpen) add("Pintu Belakang Kiri (RL)")
        if (rearRightOpen) add("Pintu Belakang Kanan (RR)")
        if (trunkOpen) add("Bagasi Belakang")
    }
}

data class TireInfo(
    val code: String,
    val label: String,
    val pressurePsi: Float,
    val tempC: Int
)

data class TpmsState(
    val frontLeft: TireInfo = TireInfo("FL", "Depan Kiri", 32.0f, 34),
    val frontRight: TireInfo = TireInfo("FR", "Depan Kanan", 32.0f, 35),
    val rearLeft: TireInfo = TireInfo("RL", "Belakang Kiri", 31.5f, 34),
    val rearRight: TireInfo = TireInfo("RR", "Belakang Kanan", 31.5f, 34)
) {
    fun lowPressureTires(thresholdPsi: Float): List<TireInfo> =
        listOf(frontLeft, frontRight, rearLeft, rearRight).filter { it.pressurePsi < thresholdPsi }
}

data class VehicleTelemetry(
    val speedKmh: Int = 0,
    val rpm: Int = 780,
    val coolantTempC: Int = 88,
    val batteryVoltage: Float = 14.1f,
    val gear: String = "D",
    val centralLocked: Boolean = true,
    val doors: DoorState = DoorState(),
    val tpms: TpmsState = TpmsState(),
    val paintColor: KiaRioPaintColor = KiaRioPaintColor.SNOW_WHITE_PEARL,
    val speedLockAlarmThresholdKmh: Int = 20,
    val tpmsLowThresholdPsi: Float = 26.0f,
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

    val lowTires: List<TireInfo>
        get() = tpms.lowPressureTires(tpmsLowThresholdPsi)

    val hasAnyCriticalAlert: Boolean
        get() = doors.anyDoorOpen || isSpeedUnlockAlarm || isCoolantOverheat || lowTires.isNotEmpty()

    val activeAlerts: List<String>
        get() = buildList {
            doors.openDoorNames().forEach { doorName ->
                add("PERINGATAN: $doorName Belum Rapat!")
            }
            if (isSpeedUnlockAlarm) {
                add("ALARM KEAMANAN: Door Lock Belum Terkunci pada Kecepatan $speedKmh km/h (> $speedLockAlarmThresholdKmh km/h)!")
            }
            lowTires.forEach { tire ->
                add("TPMS ALERT: Ban ${tire.label} (${tire.code}) Tekanan Rendah (${String.format("%.1f", tire.pressurePsi)} PSI)!")
            }
            if (isCoolantOverheat) {
                add("ENGINE OVERHEAT: Suhu Coolant Tinggi ($coolantTempC°C)!")
            }
        }
}
