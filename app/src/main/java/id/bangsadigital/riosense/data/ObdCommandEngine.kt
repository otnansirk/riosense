package id.bangsadigital.riosense.data

import id.bangsadigital.riosense.model.DoorState

/**
 * OBD2 Protocol Parser Engine (ELM327 / STN1170 USB Serial) for Kia Rio UB (2012-2016).
 *
 * 1. Powertrain ECU (Header 7E0 -> Response 7E8):
 *    - PID 010C : Engine RPM              -> ((A * 256) + B) / 4
 *    - PID 010D : Vehicle Speed (km/h)    -> A
 *    - PID 0105 : Engine Coolant Temp (C) -> A - 40
 *    - ATRV     : Battery Voltage         -> "14.1V"
 *
 * 2. Kia Rio UB Body Control Module / BCM (Header 770/7A0 -> Response 778/7A8):
 *    - Service 21 / 22 Extended PIDs for reading per-door bitmasks (FL, FR, RL, RR, Trunk) & Central Lock.
 */
object ObdCommandEngine {

    val INIT_SEQUENCE = listOf(
        "ATZ",      // Reset adapter
        "ATE0",     // Echo off
        "ATL0",     // Linefeeds off
        "ATS0",     // Spaces off
        "ATH1",     // Headers on (required to distinguish ECU 7E8 vs BCM 778 frames)
        "ATSP6"     // ISO 15765-4 CAN (11-bit ID, 500 kbaud - Kia Rio UB C-CAN)
    )

    fun parseRpm(hexResponse: String): Int? {
        val clean = hexResponse.replace(" ", "").uppercase()
        val idx = clean.indexOf("410C")
        if (idx == -1 || clean.length < idx + 8) return null
        val a = clean.substring(idx + 4, idx + 6).toIntOrNull(16) ?: return null
        val b = clean.substring(idx + 6, idx + 8).toIntOrNull(16) ?: return null
        return ((a * 256) + b) / 4
    }

    fun parseSpeedKmh(hexResponse: String): Int? {
        val clean = hexResponse.replace(" ", "").uppercase()
        val idx = clean.indexOf("410D")
        if (idx == -1 || clean.length < idx + 6) return null
        return clean.substring(idx + 4, idx + 6).toIntOrNull(16)
    }

    fun parseCoolantTempC(hexResponse: String): Int? {
        val clean = hexResponse.replace(" ", "").uppercase()
        val idx = clean.indexOf("4105")
        if (idx == -1 || clean.length < idx + 6) return null
        val raw = clean.substring(idx + 4, idx + 6).toIntOrNull(16) ?: return null
        return raw - 40
    }

    /**
     * Decodes Kia Rio UB BCM bitmasks for per-door open states & Central Lock status.
     */
    fun parseKiaRioBcmDoors(doorByte: Int, lockByte: Int): Pair<DoorState, Boolean> {
        val doors = DoorState(
            frontLeftOpen = (doorByte and 0x01) != 0,
            frontRightOpen = (doorByte and 0x02) != 0,
            rearLeftOpen = (doorByte and 0x04) != 0,
            rearRightOpen = (doorByte and 0x08) != 0,
            trunkOpen = (doorByte and 0x10) != 0
        )
        val isLocked = (lockByte and 0x01) != 0
        return doors to isLocked
    }
}
