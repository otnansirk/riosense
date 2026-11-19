package id.bangsadigital.riosense.data

import id.bangsadigital.riosense.model.DoorState
import id.bangsadigital.riosense.model.KiaRioPaintColor
import id.bangsadigital.riosense.model.LightState
import id.bangsadigital.riosense.model.TireInfo
import id.bangsadigital.riosense.model.TpmsState
import id.bangsadigital.riosense.model.VehicleTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class VehicleRepository {

    // Initial showcase state: Kia Rio Snow White Pearl, Rear-Right door open & Rear-Left tire at 22 PSI / 41°C
    private val _telemetry = MutableStateFlow(
        VehicleTelemetry(
            speedKmh = 45,
            rpm = 2400,
            coolantTempC = 88,
            batteryVoltage = 14.2f,
            gear = "D",
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
        )
    )
    val telemetry: StateFlow<VehicleTelemetry> = _telemetry.asStateFlow()

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

    fun setSpeedAndRpm(speedKmh: Int, rpm: Int) = updateWithAutoWake {
        val gear = when {
            speedKmh == 0 -> "P"
            else -> "D"
        }
        it.copy(speedKmh = speedKmh.coerceIn(0, 220), rpm = rpm.coerceIn(0, 7000), gear = gear)
    }

    fun setCoolantTemp(tempC: Int) = updateWithAutoWake {
        it.copy(coolantTempC = tempC.coerceIn(40, 130))
    }

    fun toggleCentralLock() = updateWithAutoWake {
        it.copy(centralLocked = !it.centralLocked)
    }

    fun toggleDoor(doorCode: String) = updateWithAutoWake { state ->
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

    fun setTirePressure(code: String, psi: Float) = updateWithAutoWake { state ->
        val t = state.tpms
        val clamped = psi.coerceIn(15.0f, 42.0f)
        val updated = when (code) {
            "FL" -> t.copy(frontLeft = t.frontLeft.copy(pressurePsi = clamped))
            "FR" -> t.copy(frontRight = t.frontRight.copy(pressurePsi = clamped))
            "RL" -> t.copy(rearLeft = t.rearLeft.copy(pressurePsi = clamped))
            "RR" -> t.copy(rearRight = t.rearRight.copy(pressurePsi = clamped))
            else -> t
        }
        state.copy(tpms = updated)
    }

    fun setTireTemp(code: String, tempC: Int) = updateWithAutoWake { state ->
        val t = state.tpms
        val clampedTemp = tempC.coerceIn(20, 70)
        val updated = when (code) {
            "FL" -> t.copy(frontLeft = t.frontLeft.copy(tempC = clampedTemp))
            "FR" -> t.copy(frontRight = t.frontRight.copy(tempC = clampedTemp))
            "RL" -> t.copy(rearLeft = t.rearLeft.copy(tempC = clampedTemp))
            "RR" -> t.copy(rearRight = t.rearRight.copy(tempC = clampedTemp))
            else -> t
        }
        state.copy(tpms = updated)
    }

    fun setPaintColor(color: KiaRioPaintColor) {
        _telemetry.update { it.copy(paintColor = color) }
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
    }

    fun setMinimized(minimized: Boolean) {
        _telemetry.update { it.copy(isMinimized = minimized) }
    }

    fun toggleSimulatorMode() {
        _telemetry.update { it.copy(simulatorMode = !it.simulatorMode) }
    }

    fun toggleAudioMute() {
        _telemetry.update { it.copy(audioAlarmMuted = !it.audioAlarmMuted) }
    }

    fun setSpeedLockThreshold(kmh: Int) = updateWithAutoWake {
        it.copy(speedLockAlarmThresholdKmh = kmh.coerceIn(10, 60))
    }

    fun setTpmsThreshold(psi: Float) = updateWithAutoWake {
        it.copy(tpmsLowThresholdPsi = psi.coerceIn(20f, 32f))
    }

    /** Preset 1: All Systems Nominal & Locked */
    fun applyNormalPreset() {
        _telemetry.update {
            it.copy(
                speedKmh = 62,
                rpm = 1950,
                coolantTempC = 88,
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
}
