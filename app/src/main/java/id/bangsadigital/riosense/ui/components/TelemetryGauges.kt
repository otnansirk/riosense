package id.bangsadigital.riosense.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bangsadigital.riosense.model.VehicleTelemetry
import id.bangsadigital.riosense.ui.theme.ObsidianColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LeftSpeedAndSecurityPanel(
    telemetry: VehicleTelemetry,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedSpeed by animateIntAsState(
        targetValue = telemetry.speedKmh,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "speedAnim"
    )
    val speedSweep by animateFloatAsState(
        targetValue = (telemetry.speedKmh / 200f).coerceIn(0f, 1f) * 230f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "speedSweep"
    )

    // Seamless borderless OEM cluster column
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Minimalist Gear Indicator at top of cluster
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("P", "R", "N", "D").forEach { g ->
                val active = telemetry.gear == g
                Text(
                    text = g,
                    color = if (active) ObsidianColors.PureSilver else Color(0xFF383C47),
                    fontSize = if (active) 15.sp else 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                )
            }
        }

        // Precision OEM Dial (Thin arc + fine graduation ticks + crisp typography)
        Box(
            modifier = Modifier.size(168.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 4.5.dp.toPx()
                val pad = 10.dp.toPx()
                val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
                val topLeft = Offset(pad, pad)
                val cx = size.width / 2f
                val cy = size.height / 2f
                val outerRadius = (size.width / 2f) - 2.dp.toPx()
                val innerTickRadius = outerRadius - 5.dp.toPx()

                // Fine precision tick marks around the dial
                val tickCount = 24
                for (i in 0..tickCount) {
                    val deg = 155f + (i.toFloat() / tickCount) * 230f
                    val rad = Math.toRadians(deg.toDouble())
                    val isMajor = i % 4 == 0
                    val rStart = if (isMajor) innerTickRadius - 2.dp.toPx() else innerTickRadius
                    drawLine(
                        color = if (isMajor) Color(0xFF3E4452) else Color(0xFF20242C),
                        start = Offset(
                            cx + (rStart * cos(rad)).toFloat(),
                            cy + (rStart * sin(rad)).toFloat()
                        ),
                        end = Offset(
                            cx + (outerRadius * cos(rad)).toFloat(),
                            cy + (outerRadius * sin(rad)).toFloat()
                        ),
                        strokeWidth = if (isMajor) 1.8f else 1f
                    )
                }

                // Subtle Track Arc
                drawArc(
                    color = Color(0xFF15181F),
                    startAngle = 155f,
                    sweepAngle = 230f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                // Active Speed Arc
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF525866),
                            Color(0xFFD8DEE9),
                            Color(0xFFFFFFFF)
                        )
                    ),
                    startAngle = 155f,
                    sweepAngle = speedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$animatedSpeed",
                    color = ObsidianColors.PureSilver,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1.5).sp
                )
                Text(
                    text = "km/h",
                    color = ObsidianColors.DimSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            }
        }

        // Borderless Door Lock Status Pill (Clean matte surface, zero outline)
        val isAlarm = telemetry.isSpeedUnlockAlarm
        val lockBg = if (isAlarm) ObsidianColors.AlertSurface else Color(0xFF0E1014)

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(lockBg)
                .clickable { onToggleLock() }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isAlarm -> ObsidianColors.CrimsonAlert
                            telemetry.centralLocked -> ObsidianColors.PureSilver
                            else -> Color(0xFF525866)
                        }
                    )
            )
            Column {
                Text(
                    text = if (telemetry.centralLocked) "Doors Locked" else "Doors Unlocked",
                    color = if (isAlarm) ObsidianColors.CrimsonAlert else ObsidianColors.PureSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.mediumWeight(isAlarm)
                )
                Text(
                    text = if (isAlarm) {
                        "Speed > ${telemetry.speedLockAlarmThresholdKmh} km/h warning"
                    } else {
                        "Auto-alert at ${telemetry.speedLockAlarmThresholdKmh} km/h"
                    },
                    color = ObsidianColors.DimSilver,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
fun RightEngineTelemetryPanel(
    telemetry: VehicleTelemetry,
    modifier: Modifier = Modifier
) {
    val animatedRpm by animateIntAsState(
        targetValue = telemetry.rpm,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "rpmAnim"
    )
    val rpmSweep by animateFloatAsState(
        targetValue = (telemetry.rpm / 7000f).coerceIn(0f, 1f) * 230f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "rpmSweep"
    )

    // Seamless borderless OEM cluster column
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Subtle Engine Spec Header
        Text(
            text = if (telemetry.rpm >= 5800) "REDLINE" else "RPM ×1000",
            color = if (telemetry.rpm >= 5800) ObsidianColors.CrimsonAlert else Color(0xFF4C5260),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )

        // Precision OEM Tachometer Dial
        Box(
            modifier = Modifier.size(168.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 4.5.dp.toPx()
                val pad = 10.dp.toPx()
                val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
                val topLeft = Offset(pad, pad)
                val cx = size.width / 2f
                val cy = size.height / 2f
                val outerRadius = (size.width / 2f) - 2.dp.toPx()
                val innerTickRadius = outerRadius - 5.dp.toPx()

                val tickCount = 28 // 0..7000 RPM in 250 RPM increments
                for (i in 0..tickCount) {
                    val deg = 155f + (i.toFloat() / tickCount) * 230f
                    val rad = Math.toRadians(deg.toDouble())
                    val isMajor = i % 4 == 0
                    val isRedline = i >= 24
                    val rStart = if (isMajor) innerTickRadius - 2.dp.toPx() else innerTickRadius
                    drawLine(
                        color = when {
                            isRedline -> ObsidianColors.CrimsonAlert.copy(alpha = 0.65f)
                            isMajor -> Color(0xFF3E4452)
                            else -> Color(0xFF20242C)
                        },
                        start = Offset(
                            cx + (rStart * cos(rad)).toFloat(),
                            cy + (rStart * sin(rad)).toFloat()
                        ),
                        end = Offset(
                            cx + (outerRadius * cos(rad)).toFloat(),
                            cy + (outerRadius * sin(rad)).toFloat()
                        ),
                        strokeWidth = if (isMajor) 1.8f else 1f
                    )
                }

                drawArc(
                    color = Color(0xFF15181F),
                    startAngle = 155f,
                    sweepAngle = 230f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                val rpmColor = if (telemetry.rpm >= 5800) {
                    ObsidianColors.CrimsonAlert
                } else {
                    Color(0xFFD8DEE9)
                }

                drawArc(
                    color = rpmColor,
                    startAngle = 155f,
                    sweepAngle = rpmSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%,d", animatedRpm),
                    color = ObsidianColors.PureSilver,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1).sp
                )
                Text(
                    text = "r/min",
                    color = ObsidianColors.DimSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp
                )
            }
        }

        // Borderless Minimalist Coolant & Voltage Readout
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0E1014))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Coolant",
                    color = ObsidianColors.DimSilver,
                    fontSize = 10.sp
                )
                Text(
                    text = "${telemetry.coolantTempC}°C",
                    color = if (telemetry.isCoolantOverheat) ObsidianColors.CrimsonAlert else ObsidianColors.PureSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Battery",
                    color = ObsidianColors.DimSilver,
                    fontSize = 10.sp
                )
                Text(
                    text = "${String.format("%.1f", telemetry.batteryVoltage)}V",
                    color = ObsidianColors.PureSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Hairline Coolant Progress Bar
            val progress = ((telemetry.coolantTempC - 40f) / 90f).coerceIn(0.05f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF1A1D24))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(
                            if (telemetry.isCoolantOverheat) ObsidianColors.CrimsonAlert
                            else Color(0xFF8E95A5)
                        )
                )
            }
        }
    }
}

private fun FontWeight.Companion.mediumWeight(isAlert: Boolean): FontWeight =
    if (isAlert) FontWeight.Bold else FontWeight.Medium
