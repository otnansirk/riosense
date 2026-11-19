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
    onTirePressureChange: (String, Float) -> Unit,
    onTireTempChange: (String, Int) -> Unit,
    onPaintColorSelect: (KiaRioPaintColor) -> Unit,
    onSpeedThresholdChange: (Int) -> Unit,
    onApplyNormalPreset: () -> Unit,
    onApplyAlertPreset: () -> Unit,
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
                    text = "Vehicle Controls & Finish",
                    color = ObsidianColors.PureSilver,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Kia Rio UB 2015 Telemetry & Exterior Configuration",
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
                    text = "All Nominal",
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
                text = "Speed ${telemetry.speedKmh} km/h  ·  ${telemetry.rpm} r/min",
                color = ObsidianColors.MutedSilver,
                fontSize = 11.sp
            )
            Slider(
                value = telemetry.speedKmh.toFloat(),
                onValueChange = { newSpeed ->
                    val sp = newSpeed.toInt()
                    val calcRpm = if (sp == 0) 780 else (950 + sp * 32).coerceAtMost(6600)
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

        // 4. TPMS & Speed Lock Threshold
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

        Text(
            text = "Speed Lock Warning Threshold ${telemetry.speedLockAlarmThresholdKmh} km/h",
            color = ObsidianColors.MutedSilver,
            fontSize = 11.sp
        )
        Slider(
            value = telemetry.speedLockAlarmThresholdKmh.toFloat(),
            onValueChange = { onSpeedThresholdChange(it.toInt()) },
            valueRange = 10f..40f,
            colors = monochromeSliderColors
        )
    }
}
