package id.bangsadigital.riosense.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bangsadigital.riosense.model.ObdActivityEntry
import id.bangsadigital.riosense.model.ObdDirection
import id.bangsadigital.riosense.model.ObdErrorEntry
import id.bangsadigital.riosense.model.ObdErrorSeverity
import id.bangsadigital.riosense.ui.theme.ObsidianColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ControlsTab(val label: String) {
    ERROR_LOG("Error Log"),
    ACTIVITY_LOG("Activity Log")
}

@Composable
fun ControlsMenuSheet(
    errorLogs: List<ObdErrorEntry>,
    activityLogs: List<ObdActivityEntry>,
    isActivityPaused: Boolean,
    onToggleActivityPause: () -> Unit,
    onClearErrorLogs: () -> Unit,
    onClearActivityLogs: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(ControlsTab.ERROR_LOG) }
    var selectedError by remember { mutableStateOf<ObdErrorEntry?>(null) }
    var selectedActivity by remember { mutableStateOf<ObdActivityEntry?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth(0.58f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0E1015))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Controls",
                    color = ObsidianColors.PureSilver,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "OBD-II Diagnostics & Live Telemetry Logs",
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
                Text(
                    text = "Done",
                    color = ObsidianColors.PureSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 2-Menu Selector: Error Log | Activity Log
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF14171E))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ControlsMenuTabButton(
                label = ControlsTab.ERROR_LOG.label,
                badgeText = if (errorLogs.isNotEmpty()) errorLogs.size.toString() else null,
                badgeColor = ObsidianColors.CrimsonAlert,
                selected = activeTab == ControlsTab.ERROR_LOG,
                onClick = {
                    activeTab = ControlsTab.ERROR_LOG
                    selectedError = null
                    selectedActivity = null
                },
                modifier = Modifier.weight(1f)
            )
            ControlsMenuTabButton(
                label = ControlsTab.ACTIVITY_LOG.label,
                badgeText = if (activityLogs.isNotEmpty()) activityLogs.size.toString() else null,
                badgeColor = if (isActivityPaused) ObsidianColors.DimSilver else Color(0xFF38A169),
                selected = activeTab == ControlsTab.ACTIVITY_LOG,
                onClick = {
                    activeTab = ControlsTab.ACTIVITY_LOG
                    selectedError = null
                    selectedActivity = null
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Content Body
        when (activeTab) {
            ControlsTab.ERROR_LOG -> {
                val detail = selectedError
                if (detail != null) {
                    ErrorLogDetailView(
                        entry = detail,
                        onBack = { selectedError = null },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ErrorLogListView(
                        entries = errorLogs,
                        onSelect = { selectedError = it },
                        onClear = onClearErrorLogs,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            ControlsTab.ACTIVITY_LOG -> {
                val detail = selectedActivity
                if (detail != null) {
                    ActivityLogDetailView(
                        entry = detail,
                        onBack = { selectedActivity = null },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ActivityLogListView(
                        entries = activityLogs,
                        isPaused = isActivityPaused,
                        onTogglePause = onToggleActivityPause,
                        onSelect = { selectedActivity = it },
                        onClear = onClearActivityLogs,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlsMenuTabButton(
    label: String,
    badgeText: String?,
    badgeColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFF212632) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (selected) ObsidianColors.PureSilver else ObsidianColors.MutedSilver,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
        if (badgeText != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeColor.copy(alpha = 0.22f))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = badgeText,
                    color = badgeColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ErrorLogListView(
    entries: List<ObdErrorEntry>,
    onSelect: (ObdErrorEntry) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (entries.isEmpty()) "No OBD errors detected" else "${entries.size} detected · tap item for details",
                color = ObsidianColors.DimSilver,
                fontSize = 10.sp
            )
            if (entries.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF181B22))
                        .clickable { onClear() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Clear",
                        color = ObsidianColors.MutedSilver,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF12151B)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "All OBD-II modules report no stored faults.",
                    color = ObsidianColors.DimSilver,
                    fontSize = 11.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries, key = { it.id }) { item ->
                    val dotColor = severityColor(item.severity)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF14171E))
                            .clickable { onSelect(item) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = item.code,
                                        color = dotColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = item.title,
                                        color = ObsidianColors.PureSilver,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatDateTimeCompact(item.timestampMs),
                                    color = ObsidianColors.DimSilver,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Text(
                            text = "›",
                            color = ObsidianColors.DimSilver,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorLogDetailView(
    entry: ObdErrorEntry,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = severityColor(entry.severity)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181B22))
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Back",
                    color = ObsidianColors.PureSilver,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = entry.severity.label.uppercase(),
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF14171E))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = entry.code,
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = entry.title,
                    color = ObsidianColors.PureSilver,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            DetailMetaRow(label = "Timestamp", value = formatDateTimeFull(entry.timestampMs), mono = true)
            DetailMetaRow(label = "ECU / Module", value = entry.subsystem)
            DetailMetaRow(label = "Service / PID", value = entry.pid, mono = true)
            DetailMetaRow(label = "Raw Frame", value = entry.rawFrame, mono = true)
            DetailMetaRow(label = "Source", value = entry.source)

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "DESCRIPTION",
                color = ObsidianColors.DimSilver,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = entry.description,
                color = ObsidianColors.PureSilver,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "RECOMMENDED ACTION",
                color = ObsidianColors.DimSilver,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = entry.recommendedAction,
                color = ObsidianColors.MutedSilver,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun ActivityLogListView(
    entries: List<ObdActivityEntry>,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onSelect: (ObdActivityEntry) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isPaused) ObsidianColors.DimSilver else Color(0xFF38A169))
                )
                Text(
                    text = if (isPaused) "Stream paused · tap item for details" else "Live OBD stream · tap item for details",
                    color = ObsidianColors.DimSilver,
                    fontSize = 10.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF181B22))
                        .clickable { onTogglePause() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isPaused) "Resume" else "Pause",
                        color = ObsidianColors.PureSilver,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (entries.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF181B22))
                            .clickable { onClear() }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Clear",
                            color = ObsidianColors.MutedSilver,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF12151B)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Waiting for OBD-II TX/RX frames...",
                    color = ObsidianColors.DimSilver,
                    fontSize = 11.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(entries, key = { it.id }) { item ->
                    val isTx = item.direction == ObdDirection.TX
                    val dirColor = if (isTx) Color(0xFF63B3ED) else Color(0xFF68D391)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFF14171E))
                            .clickable { onSelect(item) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(dirColor.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.direction.tag,
                                    color = dirColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = item.pid,
                                        color = ObsidianColors.PureSilver,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = item.quickValue,
                                        color = ObsidianColors.MutedSilver,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatDateTimeCompact(item.timestampMs),
                                    color = ObsidianColors.DimSilver,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Text(
                            text = "›",
                            color = ObsidianColors.DimSilver,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityLogDetailView(
    entry: ObdActivityEntry,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTx = entry.direction == ObdDirection.TX
    val dirColor = if (isTx) Color(0xFF63B3ED) else Color(0xFF68D391)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181B22))
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Back",
                    color = ObsidianColors.PureSilver,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = if (isTx) "TX · SENT TO ECU" else "RX · RECEIVED FROM ECU",
                color = dirColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF14171E))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "PID ${entry.pid}",
                    color = dirColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = entry.parameterName,
                    color = ObsidianColors.PureSilver,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            DetailMetaRow(label = "Timestamp", value = formatDateTimeFull(entry.timestampMs), mono = true)
            DetailMetaRow(label = "Quick Value", value = entry.quickValue)
            DetailMetaRow(label = "ECU Header", value = entry.ecuHeader, mono = true)
            DetailMetaRow(label = "Raw Frame", value = entry.rawFrame, mono = true)
            DetailMetaRow(label = "Formula", value = entry.formula, mono = true)
            DetailMetaRow(label = "Source", value = entry.source)

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "DECODED DATA",
                color = ObsidianColors.DimSilver,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = entry.decodedDetail,
                color = ObsidianColors.PureSilver,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun DetailMetaRow(
    label: String,
    value: String,
    mono: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF0E1015))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = ObsidianColors.DimSilver,
            fontSize = 10.sp
        )
        Text(
            text = value,
            color = ObsidianColors.PureSilver,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        )
    }
}

private fun severityColor(severity: ObdErrorSeverity): Color = when (severity) {
    ObdErrorSeverity.CRITICAL -> ObsidianColors.CrimsonAlert
    ObdErrorSeverity.WARNING -> Color(0xFFE6A23C)
    ObdErrorSeverity.INFO -> ObsidianColors.MutedSilver
}

private fun formatDateTimeCompact(timestampMs: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return sdf.format(Date(timestampMs))
}

private fun formatDateTimeFull(timestampMs: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    return sdf.format(Date(timestampMs))
}
