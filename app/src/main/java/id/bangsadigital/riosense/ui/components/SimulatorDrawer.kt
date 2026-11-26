package id.bangsadigital.riosense.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bangsadigital.riosense.model.KiaRioPaintColor
import id.bangsadigital.riosense.model.VehicleTelemetry
import id.bangsadigital.riosense.ui.theme.ObsidianColors

@Composable
fun SimulatorAndSettingsSheet(
    telemetry: VehicleTelemetry,
    onSpeedRpmChange: (Int, Int) -> Unit,
    onCoolantChange: (Int) -> Unit,
    onToggleDoor: (String) -> Unit,
    onToggleLock: () -> Unit,
    onToggleLight: (String) -> Unit,
    onToggleTpmsSignal: (Boolean) -> Unit,
    onTirePressureChange: (String, Float) -> Unit,
    onTireTempChange: (String, Int) -> Unit,
    onPaintColorSelect: (KiaRioPaintColor) -> Unit,
    onBatteryVoltageChange: (Float) -> Unit,
    onApplyNormalPreset: () -> Unit,
    onApplyAlertPreset: () -> Unit,
    onSimulateEngineStart: () -> Unit,
    onSimulateEngineOff: () -> Unit,
    onInjectDtc: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val monochromeSliderColors = SliderDefaults.colors(
        thumbColor = ObsidianColors.PureSilver,
        activeTrackColor = ObsidianColors.PureSilver,
        inactiveTrackColor = Color(0xFF1A1D24)
    )

    Column(
        modifier = modifier
            .fillMaxWidth(0.54f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0E1015))
            .padding(18.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Simulator",
                    color = ObsidianColors.PureSilver,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Kia Rio UB 2015 Telemetry, Sensors & OBD Fault Simulator",
                    color = ObsidianColors.DimSilver,
                    fontSize = 11.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181B22))
                    .clickable { onClose() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Done", color = ObsidianColors.PureSilver, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Preset Scenarios (Borderless Matte Surfaces)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161920))
                    .clickable { onApplyNormalPreset() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "All Normal",
                    color = ObsidianColors.PureSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianColors.AlertSurface)
                    .clickable { onApplyAlertPreset() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Demo Door & TPMS Warning",
                    color = ObsidianColors.PureSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Simulate Ignition START & Engine OFF to test Welcome Greeting & Voice Reminder
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF15241D))
                    .border(0.7.dp, Color(0xFF68D391).copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .clickable { onSimulateEngineStart() }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Simulate Engine START",
                    color = Color(0xFF68D391),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF151E28))
                    .border(0.7.dp, Color(0xFF63B3ED).copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .clickable { onSimulateEngineOff() }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Simulate Engine OFF",
                    color = Color(0xFF63B3ED),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 1. Exterior Paint Finish
        Text(
            text = "Exterior Finish",
            color = ObsidianColors.MutedSilver,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KiaRioPaintColor.entries.forEach { paint ->
                val selected = telemetry.paintColor == paint
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Color(0xFF1C2029) else Color(0xFF13151B))
                        .clickable { onPaintColorSelect(paint) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(paint.primaryHex)
                            .then(
                                if (selected) Modifier.border(1.5.dp, Color.White, CircleShape)
                                else Modifier
                            )
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = paint.displayName.substringBefore(" "),
                        color = if (selected) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }

        // 2. Telemetry Sliders
        Column {
            Text(
                text = "Speed ${telemetry.speedKmh} km/h  ·  ${telemetry.rpm} r/min  ·  Gear ${telemetry.gear} (6MT)",
                color = ObsidianColors.MutedSilver,
                fontSize = 11.sp
            )
            Slider(
                value = telemetry.speedKmh.toFloat(),
                onValueChange = { newSpeed ->
                    val sp = newSpeed.toInt()
                    val ratio = when {
                        sp == 0 -> 0f
                        sp <= 20 -> 7.8f
                        sp <= 40 -> 14.0f
                        sp <= 65 -> 20.0f
                        sp <= 90 -> 26.2f
                        sp <= 120 -> 32.5f
                        else -> 39.5f
                    }
                    val calcRpm = if (sp == 0) 780 else ((sp * 1000f) / ratio).toInt().coerceIn(900, 6600)
                    onSpeedRpmChange(sp, calcRpm)
                },
                valueRange = 0f..160f,
                colors = monochromeSliderColors
            )

            Text(
                text = "Coolant Temperature ${telemetry.coolantTempC}°C",
                color = ObsidianColors.MutedSilver,
                fontSize = 11.sp
            )
            Slider(
                value = telemetry.coolantTempC.toFloat(),
                onValueChange = { onCoolantChange(it.toInt()) },
                valueRange = 50f..118f,
                colors = monochromeSliderColors
            )
        }

        // 3. Doors & Central Lock
        Text(
            text = "Doors & Central Lock",
            color = ObsidianColors.MutedSilver,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "FL" to telemetry.doors.frontLeftOpen,
                "FR" to telemetry.doors.frontRightOpen,
                "RL" to telemetry.doors.rearLeftOpen,
                "RR" to telemetry.doors.rearRightOpen,
                "TRUNK" to telemetry.doors.trunkOpen
            ).forEach { (code, isOpen) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isOpen) ObsidianColors.AlertSurface else Color(0xFF161920))
                        .clickable { onToggleDoor(code) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = code,
                        color = if (isOpen) ObsidianColors.CrimsonAlert else ObsidianColors.PureSilver,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (telemetry.centralLocked) Color(0xFF1C2029) else ObsidianColors.AlertSurface)
                    .clickable { onToggleLock() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (telemetry.centralLocked) "Locked" else "Unlocked",
                    color = ObsidianColors.PureSilver,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 4. Exterior Lights Simulator (Visible on Front / Back View)
        Text(
            text = "Exterior Lighting (Visible on Front / Back View)",
            color = ObsidianColors.MutedSilver,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        val lights = telemetry.lights
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Triple("PARKING", "Position", lights.parkingLights),
                    Triple("LOW_BEAM", "Low Beam", lights.lowBeam),
                    Triple("HIGH_BEAM", "High Beam", lights.highBeam),
                    Triple("FOG", "Fog Lamp", lights.fogLights)
                ).forEach { (code, label, active) ->
                    LightSimulatorPill(
                        label = label,
                        active = active,
                        activeColor = Color(0xFFE3ECFF),
                        onClick = { onToggleLight(code) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Triple("LEFT_TURN", "Turn L", lights.leftTurn),
                    Triple("RIGHT_TURN", "Turn R", lights.rightTurn),
                    Triple("HAZARD", "Hazard", lights.hazard),
                    Triple("BRAKE", "Brake", lights.brake)
                ).forEach { (code, label, active) ->
                    val tint = if (code == "BRAKE") ObsidianColors.CrimsonAlert else Color(0xFFFFA726)
                    LightSimulatorPill(
                        label = label,
                        active = active,
                        activeColor = tint,
                        onClick = { onToggleLight(code) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 5. External TPMS Receiver Signal & Tire Sliders
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TPMS Sensor Signal",
                color = ObsidianColors.MutedSilver,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF13151B))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(false to "No Signal", true to "Connected").forEach { (connected, label) ->
                    val selected = telemetry.tpmsSignalAvailable == connected
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) {
                                    if (connected) Color(0xFF152820) else Color(0xFF212836)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .clickable { onToggleTpmsSignal(connected) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            color = when {
                                selected && connected -> ObsidianColors.EmeraldNominal
                                selected -> ObsidianColors.PureSilver
                                else -> ObsidianColors.DimSilver
                            },
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (telemetry.tpmsSignalAvailable) {
            Text(
                text = "Rear-Left Tire Pressure ${String.format("%.1f", telemetry.tpms.rearLeft.pressurePsi)} psi",
                color = ObsidianColors.MutedSilver,
                fontSize = 11.sp
            )
            Slider(
                value = telemetry.tpms.rearLeft.pressurePsi,
                onValueChange = { onTirePressureChange("RL", it) },
                valueRange = 18f..42f,
                colors = monochromeSliderColors
            )

            Text(
                text = "Rear-Left Tire Temp ${telemetry.tpms.rearLeft.tempC}°C",
                color = ObsidianColors.MutedSilver,
                fontSize = 11.sp
            )
            Slider(
                value = telemetry.tpms.rearLeft.tempC.toFloat(),
                onValueChange = { onTireTempChange("RL", it.toInt()) },
                valueRange = 25f..60f,
                colors = monochromeSliderColors
            )
        }

        Text(
            text = "Battery Voltage ${String.format("%.1f", telemetry.batteryVoltage)} V",
            color = if (telemetry.isBatteryAlert) ObsidianColors.CrimsonAlert else ObsidianColors.MutedSilver,
            fontSize = 11.sp
        )
        Slider(
            value = telemetry.batteryVoltage,
            onValueChange = { onBatteryVoltageChange(it) },
            valueRange = 10.5f..15.8f,
            colors = monochromeSliderColors
        )

        // 6. Simulate OBD-II Diagnostic Trouble Codes (DTC) for Error Log testing
        Text(
            text = "Inject OBD-II Fault (DTC)",
            color = ObsidianColors.MutedSilver,
            fontSize = 11.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("P0300", "P0118", "C1202", "B1213").forEach { dtc ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1417))
                        .border(0.7.dp, ObsidianColors.CrimsonAlert.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .clickable { onInjectDtc(dtc) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dtc,
                        color = ObsidianColors.CrimsonAlert,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun LightSimulatorPill(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) activeColor.copy(alpha = 0.18f) else Color(0xFF161920))
            .then(
                if (active) Modifier.border(0.8.dp, activeColor.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) activeColor else ObsidianColors.PureSilver,
            fontSize = 9.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
