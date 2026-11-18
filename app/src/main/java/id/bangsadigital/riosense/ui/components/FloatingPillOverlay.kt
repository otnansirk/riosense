package id.bangsadigital.riosense.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bangsadigital.riosense.model.VehicleTelemetry
import id.bangsadigital.riosense.ui.theme.ObsidianColors

@Composable
fun MinimizedFloatingPillView(
    telemetry: VehicleTelemetry,
    onExpandDashboard: () -> Unit,
    onTriggerSimulatedDoorAlarm: () -> Unit,
    onEnterNativePip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianColors.DeepObsidian)
            .padding(24.dp)
    ) {
        // Borderless OEM Floating Pill
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF12141A))
                .clickable { onExpandDashboard() }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(telemetry.paintColor.primaryHex)
            )

            Text(
                text = "${telemetry.speedKmh} km/h",
                color = ObsidianColors.PureSilver,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "${telemetry.coolantTempC}°C",
                color = ObsidianColors.DimSilver,
                fontSize = 13.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (telemetry.hasAnyCriticalAlert) ObsidianColors.AlertSurface
                        else Color(0xFF1A1D24)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (telemetry.hasAnyCriticalAlert) "Warning · Expand" else "Expand",
                    color = ObsidianColors.PureSilver,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0C0E12))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Compact Overlay Mode",
                color = ObsidianColors.PureSilver,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Automatically expands when a door opens, lock warning triggers, or tire pressure drops.",
                color = ObsidianColors.DimSilver,
                fontSize = 12.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianColors.AlertSurface)
                        .clickable { onTriggerSimulatedDoorAlarm() }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "Simulate Door Alert",
                        color = ObsidianColors.PureSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181B22))
                        .clickable { onEnterNativePip() }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "Picture-in-Picture",
                        color = ObsidianColors.PureSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF12141A))
                        .clickable { onExpandDashboard() }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "Full View",
                        color = ObsidianColors.MutedSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
