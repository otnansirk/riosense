package id.bangsadigital.riosense.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.bangsadigital.riosense.model.MaintenanceItem
import id.bangsadigital.riosense.model.MaintenanceState
import id.bangsadigital.riosense.model.MaintenanceStatus
import id.bangsadigital.riosense.model.ServiceRecord
import id.bangsadigital.riosense.model.formatIdr
import id.bangsadigital.riosense.ui.i18n.AppLanguage
import id.bangsadigital.riosense.ui.i18n.LocalAppLanguage
import id.bangsadigital.riosense.ui.i18n.appStrings
import id.bangsadigital.riosense.ui.theme.ObsidianColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimalist Obsidian Maintenance & Service section.
 * Matches the unified Controls -> Settings & Trip History design language:
 * flat hairline-separated rows for lists, and low-contrast Obsidian border
 * containers strictly for grouped detail/form sections.
 */
@Composable
fun MaintenanceServiceTabContent(
    maintenance: MaintenanceState,
    selectedItemId: String?,
    showAddForm: Boolean,
    showRecordForm: Boolean,
    onShowAddFormChange: (Boolean) -> Unit,
    onShowRecordFormChange: (Boolean) -> Unit,
    onSelectItem: (String?) -> Unit,
    onUpdateOdometer: (Int) -> Unit,
    onRemindDaysChange: (Int) -> Unit,
    onRemindKmChange: (Int) -> Unit,
    onLogServiceRecord: (itemId: String, dateMs: Long, odometerKm: Int, costIdr: Long, note: String) -> Unit,
    onDeleteHistoryRecord: (itemId: String, recordId: String) -> Unit,
    onUpdateSchedule: (String, Int, Int) -> Unit,
    onAddCustomItem: (name: String, months: Int, km: Int, dateMs: Long, odoKm: Int, costIdr: Long, note: String) -> Unit,
    onDeleteCustomItem: (String) -> Unit,
    onResetStandard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedItem = selectedItemId?.let { id -> maintenance.items.find { it.id == id } }

    when {
        showAddForm -> {
            AddCustomMaintenanceForm(
                currentOdometerKm = maintenance.currentOdometerKm,
                onSave = { name, months, km, dateMs, odoKm, costIdr, note ->
                    onAddCustomItem(name, months, km, dateMs, odoKm, costIdr, note)
                    onShowAddFormChange(false)
                },
                modifier = modifier
            )
        }

        selectedItem != null && showRecordForm -> {
            RecordServiceForm(
                item = selectedItem,
                currentOdometerKm = maintenance.currentOdometerKm,
                onSave = { dateMs, odoKm, costIdr, note ->
                    onLogServiceRecord(selectedItem.id, dateMs, odoKm, costIdr, note)
                    onShowRecordFormChange(false)
                },
                modifier = modifier
            )
        }

        selectedItem != null -> {
            MaintenanceItemDetailView(
                item = selectedItem,
                maintenance = maintenance,
                onOpenRecordForm = { onShowRecordFormChange(true) },
                onDeleteHistoryRecord = { recordId -> onDeleteHistoryRecord(selectedItem.id, recordId) },
                onUpdateSchedule = { months, km -> onUpdateSchedule(selectedItem.id, months, km) },
                onDelete = if (selectedItem.isCustom) {
                    {
                        onDeleteCustomItem(selectedItem.id)
                        onSelectItem(null)
                    }
                } else null,
                modifier = modifier
            )
        }

        else -> {
            MaintenanceListView(
                maintenance = maintenance,
                onSelectItem = { onSelectItem(it.id) },
                onUpdateOdometer = onUpdateOdometer,
                onRemindDaysChange = onRemindDaysChange,
                onRemindKmChange = onRemindKmChange,
                onOpenAddForm = { onShowAddFormChange(true) },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun MaintenanceListView(
    maintenance: MaintenanceState,
    onSelectItem: (MaintenanceItem) -> Unit,
    onUpdateOdometer: (Int) -> Unit,
    onRemindDaysChange: (Int) -> Unit,
    onRemindKmChange: (Int) -> Unit,
    onOpenAddForm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = appStrings()
    val nowMs = System.currentTimeMillis()
    var editingOdometer by remember { mutableStateOf(false) }
    var odometerInput by remember(maintenance.currentOdometerKm) {
        mutableStateOf(maintenance.currentOdometerKm.toString())
    }

    val sortedItems = remember(maintenance) {
        maintenance.items.sortedBy { item ->
            when (item.evaluateStatus(nowMs, maintenance.currentOdometerKm, maintenance.remindDaysBefore, maintenance.remindKmBefore)) {
                MaintenanceStatus.OVERDUE -> 0
                MaintenanceStatus.DUE_SOON -> 1
                MaintenanceStatus.OK -> 2
            }
        }
    }

    val compactLabelStyle = TextStyle(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeight = 10.sp
    )
    val compactValueStyle = TextStyle(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeight = 13.sp
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. Flat Overview Row: Odometer (2-line stack) & Total Spent (2-line stack)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editingOdometer) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("ODO", color = ObsidianColors.DimSilver, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, style = compactLabelStyle)
                    Box(
                        modifier = Modifier
                            .width(92.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ObsidianColors.ObsidianSubPanel)
                            .border(1.dp, ObsidianColors.ObsidianSubBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        BasicTextField(
                            value = odometerInput,
                            onValueChange = { text ->
                                odometerInput = text.filter { it.isDigit() }.take(6)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(
                                color = ObsidianColors.PureSilver,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(ObsidianColors.PureSilver),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    CompactActionChip(label = s.save, accent = ObsidianColors.CobaltAccent, bg = ObsidianColors.CobaltSurface) {
                        odometerInput.toIntOrNull()?.let { onUpdateOdometer(it) }
                        editingOdometer = false
                    }
                    CompactActionChip(label = s.cancel) {
                        odometerInput = maintenance.currentOdometerKm.toString()
                        editingOdometer = false
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.clickable {
                        odometerInput = maintenance.currentOdometerKm.toString()
                        editingOdometer = true
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "ODO",
                            color = ObsidianColors.DimSilver,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                            style = compactLabelStyle
                        )
                        Text(
                            text = s.edit,
                            color = ObsidianColors.CobaltAccent,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Medium,
                            style = compactLabelStyle
                        )
                    }
                    Text(
                        text = "${String.format("%,d", maintenance.currentOdometerKm)} km",
                        color = ObsidianColors.PureSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        style = compactValueStyle
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = s.totalSpent,
                        color = ObsidianColors.DimSilver,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        style = compactLabelStyle
                    )
                    Text(
                        text = formatIdr(maintenance.totalMaintenanceCostIdr),
                        color = ObsidianColors.PureSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        style = compactValueStyle
                    )
                }
            }
        }

        // 2. Flat Reminder Lead-Time & Actions Row (tight vertical gap directly under ODO/Total Spent)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = s.remind,
                    color = ObsidianColors.MutedSilver,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    style = compactLabelStyle
                )
                SegmentedPillRow(
                    options = listOf(3 to "3d", 7 to "7d", 14 to "14d"),
                    selectedValue = maintenance.remindDaysBefore,
                    onSelect = onRemindDaysChange
                )
                SegmentedPillRow(
                    options = listOf(500 to "500km", 1000 to "1000km"),
                    selectedValue = maintenance.remindKmBefore,
                    onSelect = onRemindKmChange
                )
            }

            CompactActionChip(
                label = s.addBtn,
                accent = ObsidianColors.CobaltAccent,
                bg = ObsidianColors.CobaltSurface,
                onClick = onOpenAddForm
            )
        }
        ServiceHairlineDivider()

        // 3. Flat Hairline-Separated Parts List (dotless, matching Trip History & Settings)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            itemsIndexed(sortedItems, key = { _, item -> item.id }) { index, item ->
                val status = item.evaluateStatus(
                    nowMs = nowMs,
                    currentOdometerKm = maintenance.currentOdometerKm,
                    remindDaysBefore = maintenance.remindDaysBefore,
                    remindKmBefore = maintenance.remindKmBefore
                )
                val statusColor = maintenanceStatusColor(status)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectItem(item) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = ObsidianColors.PureSilver,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.quickSummary(nowMs, maintenance.currentOdometerKm),
                            color = if (status == MaintenanceStatus.OK) ObsidianColors.DimSilver else statusColor,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (item.totalCostIdr > 0L) {
                            Text(
                                text = formatIdr(item.totalCostIdr),
                                color = ObsidianColors.DimSilver,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "›",
                            color = ObsidianColors.DimSilver,
                            fontSize = 16.sp
                        )
                    }
                }
                if (index < sortedItems.lastIndex) {
                    ServiceHairlineDivider()
                }
            }
        }
    }
}

@Composable
private fun MaintenanceItemDetailView(
    item: MaintenanceItem,
    maintenance: MaintenanceState,
    onOpenRecordForm: () -> Unit,
    onDeleteHistoryRecord: (String) -> Unit,
    onUpdateSchedule: (Int, Int) -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val s = appStrings()
    val nowMs = System.currentTimeMillis()
    val status = item.evaluateStatus(
        nowMs = nowMs,
        currentOdometerKm = maintenance.currentOdometerKm,
        remindDaysBefore = maintenance.remindDaysBefore,
        remindKmBefore = maintenance.remindKmBefore
    )
    val accent = maintenanceStatusColor(status)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Schedule Overview Group (ObsidianPanel + 1dp ObsidianBorder)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianColors.ObsidianPanel)
                .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    color = ObsidianColors.PureSilver,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = status.label.uppercase(),
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Text(
                text = "${item.category} · ${item.quickSummary(nowMs, maintenance.currentOdometerKm)}",
                color = if (status == MaintenanceStatus.OK) ObsidianColors.DimSilver else accent,
                fontSize = 10.5.sp
            )

            Spacer(modifier = Modifier.height(2.dp))
            ServiceHairlineDivider()
            ServiceMetaRow(
                s.lastService,
                "${formatDateOnly(item.lastServiceDateMs)} · ${String.format("%,d", item.lastServiceOdometerKm)} km"
            )
            ServiceHairlineDivider()
            ServiceMetaRow(
                s.nextDue,
                buildString {
                    append(item.nextDueDateMs()?.let { formatDateOnly(it) } ?: "-")
                    item.nextDueOdometerKm()?.let { append(" · ${String.format("%,d", it)} km") }
                }
            )
            ServiceHairlineDivider()
            ServiceMetaRow(s.partTotalSpent, formatIdr(item.totalCostIdr))

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianColors.CobaltSurface)
                    .border(1.dp, ObsidianColors.CobaltAccent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .clickable { onOpenRecordForm() }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s.recordServiceBtn,
                    color = ObsidianColors.CobaltAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 1B. Obsidian X-Ray Component Locator Diagram (RideCheck-inspired schematic)
        KiaRioComponentXRayCard(
            item = item,
            accent = accent
        )

        // 1C. Interactive FWD Tire Rotation, Spooring & Balancing Diagram (for tire items)
        val isTireItem = item.id == "tire_rotation" || item.id == "tires" ||
            item.category.contains("Tire", ignoreCase = true) ||
            item.name.contains("Rotasi", ignoreCase = true) ||
            item.name.contains("Tire", ignoreCase = true)
        if (isTireItem) {
            TireRotationGuideCard()
        }

        // 2. Reminder Interval Presets Group (aligned with standard Kia Rio UB presets + 1-tap standard restore)
        val (stdMonths, stdKm) = remember(item.id) {
            MaintenanceState.standardScheduleFor(item.id)
        }
        val isAtStandard = item.intervalMonths == stdMonths && item.intervalKm == stdKm
        val stdSummary = remember(stdMonths, stdKm, s.offLabel) {
            val mText = if (stdMonths > 0) "${stdMonths}m" else s.offLabel
            val kText = if (stdKm > 0) "${stdKm / 1000}k km" else s.offLabel
            "$mText · $kText"
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianColors.ObsidianPanel)
                .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.reminderIntervalTitle,
                    color = ObsidianColors.DimSilver,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                )
                if (isAtStandard) {
                    Text(
                        text = "${s.stdPrefix}: $stdSummary",
                        color = ObsidianColors.DimSilver,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    CompactActionChip(
                        label = "${s.resetBtn} (${s.stdPrefix}: $stdSummary)",
                        accent = ObsidianColors.CobaltAccent,
                        bg = ObsidianColors.CobaltSurface,
                        onClick = { onUpdateSchedule(stdMonths, stdKm) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.timeLabel, color = ObsidianColors.MutedSilver, fontSize = 10.5.sp)
                SegmentedPillRow(
                    options = listOf(0 to s.offLabel, 6 to "6m", 12 to "12m", 24 to "24m", 48 to "48m"),
                    selectedValue = item.intervalMonths,
                    onSelect = { months -> onUpdateSchedule(months, item.intervalKm) }
                )
            }

            ServiceHairlineDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.distanceLabel, color = ObsidianColors.MutedSilver, fontSize = 10.5.sp)
                SegmentedPillRow(
                    options = listOf(
                        0 to s.offLabel,
                        5000 to "5k",
                        10000 to "10k",
                        20000 to "20k",
                        40000 to "40k",
                        50000 to "50k",
                        80000 to "80k"
                    ),
                    selectedValue = item.intervalKm,
                    onSelect = { km -> onUpdateSchedule(item.intervalMonths, km) }
                )
            }
        }

        // 3. Permanent Service History Group
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianColors.ObsidianPanel)
                .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = s.serviceHistoryTitle(item.history.size),
                color = ObsidianColors.DimSilver,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (item.history.isEmpty()) {
                Text(
                    text = s.noServiceRecords,
                    color = ObsidianColors.DimSilver,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                item.history.forEachIndexed { idx, record ->
                    ServiceHistoryRow(
                        record = record,
                        canDelete = item.history.size > 1,
                        onDelete = { onDeleteHistoryRecord(record.id) }
                    )
                    if (idx < item.history.lastIndex) {
                        ServiceHairlineDivider()
                    }
                }
            }
        }

        if (onDelete != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianColors.CrimsonSurface)
                    .clickable { onDelete() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s.deleteCustomPart,
                    color = ObsidianColors.CrimsonAlert,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ServiceHistoryRow(
    record: ServiceRecord,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatDateOnly(record.dateMs)}  ·  ${String.format("%,d", record.odometerKm)} km",
                color = ObsidianColors.PureSilver,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (record.costIdr > 0L) {
                    Text(
                        text = formatIdr(record.costIdr),
                        color = ObsidianColors.EmeraldNominal,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (canDelete) {
                    Text(
                        text = "×",
                        color = ObsidianColors.DimSilver,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable { onDelete() }
                            .padding(horizontal = 2.dp)
                    )
                }
            }
        }
        if (record.note.isNotBlank()) {
            Text(
                text = record.note,
                color = ObsidianColors.MutedSilver,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun RecordServiceForm(
    item: MaintenanceItem,
    currentOdometerKm: Int,
    onSave: (dateMs: Long, odoKm: Int, costIdr: Long, note: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = appStrings()
    val context = LocalContext.current
    var selectedDateMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var odoText by remember { mutableStateOf(currentOdometerKm.toString()) }
    var costText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    fun openDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMs }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 12)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                selectedDateMs = picked.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianColors.ObsidianPanel)
                .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = item.name,
                color = ObsidianColors.PureSilver,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Date Selector with Native Calendar Picker + Quick Today/Yesterday
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s.serviceDate, color = ObsidianColors.MutedSilver, fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CompactActionChip(s.today) { selectedDateMs = System.currentTimeMillis() }
                        CompactActionChip(s.yesterday) {
                            selectedDateMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianColors.ObsidianSubPanel)
                        .border(1.dp, ObsidianColors.ObsidianSubBorder, RoundedCornerShape(8.dp))
                        .clickable { openDatePicker() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDateOnly(selectedDateMs),
                        color = ObsidianColors.PureSilver,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = s.pickDate,
                        color = ObsidianColors.CobaltAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Odometer Input
            FormInputField(
                label = s.odometerAtService,
                value = odoText,
                placeholder = currentOdometerKm.toString(),
                keyboardType = KeyboardType.Number,
                onValueChange = { odoText = it.filter { ch -> ch.isDigit() }.take(6) }
            )

            // Optional Cost Input (IDR)
            val parsedCost = costText.toLongOrNull() ?: 0L
            FormInputField(
                label = if (parsedCost > 0L) s.totalCostWithPreview(formatIdr(parsedCost)) else s.totalCostOptional,
                value = costText,
                placeholder = "e.g. 550000",
                keyboardType = KeyboardType.Number,
                onValueChange = { costText = it.filter { ch -> ch.isDigit() }.take(9) }
            )

            // Optional Multiline Notes
            FormInputField(
                label = s.notesOptional,
                value = noteText,
                placeholder = "e.g.\n- Shell Helix HX7 10W-40\n- OEM Oil Filter",
                keyboardType = KeyboardType.Text,
                multiline = true,
                onValueChange = { noteText = it }
            )

            val parsedOdo = odoText.toIntOrNull()
            val canSave = parsedOdo != null
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (canSave) ObsidianColors.CobaltSurface else ObsidianColors.ObsidianSubPanel)
                    .then(
                        if (canSave) {
                            Modifier.border(1.dp, ObsidianColors.CobaltAccent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        } else {
                            Modifier.border(1.dp, ObsidianColors.ObsidianSubBorder, RoundedCornerShape(8.dp))
                        }
                    )
                    .clickable(enabled = canSave) {
                        onSave(selectedDateMs, parsedOdo ?: currentOdometerKm, parsedCost, noteText)
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s.saveServiceRecord,
                    color = if (canSave) ObsidianColors.CobaltAccent else ObsidianColors.DimSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AddCustomMaintenanceForm(
    currentOdometerKm: Int,
    onSave: (name: String, months: Int, km: Int, dateMs: Long, odoKm: Int, costIdr: Long, note: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = appStrings()
    val context = LocalContext.current
    var partName by remember { mutableStateOf("") }
    var intervalMonths by remember { mutableIntStateOf(12) }
    var intervalKm by remember { mutableIntStateOf(10000) }
    var selectedDateMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var odoText by remember { mutableStateOf(currentOdometerKm.toString()) }
    var costText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    fun openDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMs }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 12)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                selectedDateMs = picked.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianColors.ObsidianPanel)
                .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FormInputField(
                label = s.partNameLabel,
                value = partName,
                placeholder = "e.g. Wiper Blades, Fan Belt, Brake Fluid",
                keyboardType = KeyboardType.Text,
                onValueChange = { partName = it }
            )

            // Date picker
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.lastServiceDate, color = ObsidianColors.MutedSilver, fontSize = 10.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianColors.ObsidianSubPanel)
                        .border(1.dp, ObsidianColors.ObsidianSubBorder, RoundedCornerShape(8.dp))
                        .clickable { openDatePicker() }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDateOnly(selectedDateMs),
                        color = ObsidianColors.PureSilver,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = s.pickDate,
                        color = ObsidianColors.CobaltAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            FormInputField(
                label = s.odometerAtService,
                value = odoText,
                placeholder = currentOdometerKm.toString(),
                keyboardType = KeyboardType.Number,
                onValueChange = { odoText = it.filter { ch -> ch.isDigit() }.take(6) }
            )

            val parsedCost = costText.toLongOrNull() ?: 0L
            FormInputField(
                label = if (parsedCost > 0L) s.costWithPreview(formatIdr(parsedCost)) else s.costOptional,
                value = costText,
                placeholder = "e.g. 250000",
                keyboardType = KeyboardType.Number,
                onValueChange = { costText = it.filter { ch -> ch.isDigit() }.take(9) }
            )

            FormInputField(
                label = s.notesOptional,
                value = noteText,
                placeholder = "e.g.\n- Bosch Advantage 24/16 inch\n- Rear wiper blade",
                keyboardType = KeyboardType.Text,
                multiline = true,
                onValueChange = { noteText = it }
            )

            ServiceHairlineDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.timeLabel, color = ObsidianColors.MutedSilver, fontSize = 10.sp)
                SegmentedPillRow(
                    options = listOf(0 to s.offLabel, 6 to "6m", 12 to "12m", 24 to "24m", 48 to "48m"),
                    selectedValue = intervalMonths,
                    onSelect = { intervalMonths = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.distanceLabel, color = ObsidianColors.MutedSilver, fontSize = 10.sp)
                SegmentedPillRow(
                    options = listOf(
                        0 to s.offLabel,
                        5000 to "5k",
                        10000 to "10k",
                        20000 to "20k",
                        40000 to "40k",
                        50000 to "50k",
                        80000 to "80k"
                    ),
                    selectedValue = intervalKm,
                    onSelect = { intervalKm = it }
                )
            }

            val canSave = partName.trim().isNotEmpty()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (canSave) ObsidianColors.CobaltSurface else ObsidianColors.ObsidianSubPanel)
                    .then(
                        if (canSave) {
                            Modifier.border(1.dp, ObsidianColors.CobaltAccent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        } else {
                            Modifier.border(1.dp, ObsidianColors.ObsidianSubBorder, RoundedCornerShape(8.dp))
                        }
                    )
                    .clickable(enabled = canSave) {
                        onSave(
                            partName,
                            intervalMonths,
                            intervalKm,
                            selectedDateMs,
                            odoText.toIntOrNull() ?: currentOdometerKm,
                            parsedCost,
                            noteText
                        )
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s.saveMaintenancePart,
                    color = if (canSave) ObsidianColors.CobaltAccent else ObsidianColors.DimSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun FormInputField(
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType,
    multiline: Boolean = false,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, color = ObsidianColors.MutedSilver, fontSize = 10.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (multiline) Modifier.heightIn(min = 76.dp) else Modifier)
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianColors.ObsidianSubPanel)
                .border(1.dp, ObsidianColors.ObsidianSubBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            contentAlignment = Alignment.TopStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = ObsidianColors.DimSilver,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = !multiline,
                minLines = if (multiline) 3 else 1,
                maxLines = if (multiline) 6 else 1,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = TextStyle(
                    color = ObsidianColors.PureSilver,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = if (keyboardType == KeyboardType.Number) FontFamily.Monospace else FontFamily.Default
                ),
                cursorBrush = SolidColor(ObsidianColors.PureSilver),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun <T> SegmentedPillRow(
    options: List<Pair<T, String>>,
    selectedValue: T,
    onSelect: (T) -> Unit
) {
    val pillTextStyle = TextStyle(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeight = 12.sp
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0E1015))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { (value, label) ->
            val selected = value == selectedValue
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.5.dp))
                    .background(if (selected) Color(0xFF212836) else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    style = pillTextStyle
                )
            }
        }
    }
}

@Composable
private fun CompactActionChip(
    label: String,
    accent: Color = ObsidianColors.MutedSilver,
    bg: Color = ObsidianColors.ObsidianSubPanel,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeight = 12.sp
            )
        )
    }
}

@Composable
private fun ServiceMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = ObsidianColors.DimSilver, fontSize = 10.sp)
        Text(
            text = value,
            color = ObsidianColors.PureSilver,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ServiceHairlineDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ObsidianColors.ObsidianHairline)
    )
}

fun maintenanceStatusColor(status: MaintenanceStatus): Color = when (status) {
    MaintenanceStatus.OVERDUE -> ObsidianColors.CrimsonAlert
    MaintenanceStatus.DUE_SOON -> ObsidianColors.AmberCaution
    MaintenanceStatus.OK -> ObsidianColors.EmeraldNominal
}

private fun formatDateOnly(timestampMs: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return sdf.format(Date(timestampMs))
}

/**
 * Technical Obsidian X-Ray schematic of the Kia Rio UB (2015) chassis that highlights
 * the exact physical position of the selected maintenance component (0 KB asset overhead).
 */
@Composable
private fun KiaRioComponentXRayCard(
    item: MaintenanceItem,
    accent: Color
) {
    val isId = LocalAppLanguage.current == AppLanguage.ID
    val zoneLabel = remember(item.id, item.category, isId) {
        when (item.id) {
            "engine_oil" -> if (isId) "Blok Mesin · Depan Tengah" else "Engine Block · Front Center"
            "air_cabin_filter" -> if (isId) "Airbox Mesin & Kabin AC" else "Engine Airbox & Cabin Filter"
            "ac_service" -> if (isId) "Kompresor & Evaporator AC" else "AC Compressor & Evaporator"
            "coolant" -> if (isId) "Radiator Core · Hidung Depan" else "Radiator Core · Front Nose"
            "battery" -> if (isId) "Ruang Mesin · Kiri Depan (NS60L)" else "Engine Bay · Front-Left (NS60L)"
            "tire_rotation", "tires" -> if (isId) "4 Roda · Penggerak Depan (FWD)" else "4-Wheel Chassis (FWD)"
            "brake_pads" -> if (isId) "4 Kaliper Cakram & Master Rem" else "4 Brake Calipers & Master Cylinder"
            "trans_oil_plugs" -> if (isId) "Gearbox 6MT & 4 Koil Busi" else "6MT Gearbox & 4 Ignition Coils"
            else -> if (isId) "Sasis Kia Rio UB · ${item.category}" else "Kia Rio UB Chassis · ${item.category}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianColors.ObsidianPanel)
            .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isId) "LOKASI KOMPONEN (X-RAY)" else "COMPONENT LOCATION (X-RAY)",
                color = ObsidianColors.DimSilver,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = zoneLabel,
                color = ObsidianColors.CobaltAccent,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF090B0F))
                .border(0.8.dp, ObsidianColors.ObsidianSubBorder, RoundedCornerShape(9.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Subtle technical blueprint grid
                val gridColor = Color(0xFF131720)
                for (i in 1..7) {
                    val gx = (w / 8f) * i
                    drawLine(gridColor, Offset(gx, 0f), Offset(gx, h), 0.7.dp.toPx())
                }
                for (j in 1..3) {
                    val gy = (h / 4f) * j
                    drawLine(gridColor, Offset(0f, gy), Offset(w, gy), 0.7.dp.toPx())
                }

                // Horizontal top-down Kia Rio UB X-Ray chassis (nose on LEFT, tailgate on RIGHT)
                val carW = w * 0.62f
                val carH = h * 0.56f
                val left = (w - carW) * 0.5f
                val top = (h - carH) * 0.5f
                val bottom = top + carH
                val cy = h * 0.5f

                val wireColor = Color(0xFF2B3344)
                val innerWire = Color(0xFF1E2533)
                val stroke = 1.2.dp.toPx()

                // 4 X-Ray tires (FL = top-left? Since nose is LEFT, front-right is top-left, front-left is bottom-left)
                val wheelW = carW * 0.14f
                val wheelH = carH * 0.16f
                val frontAxleX = left + carW * 0.18f
                val rearAxleX = left + carW * 0.79f
                val wheelOffsets = listOf(
                    Offset(frontAxleX, top - wheelH * 0.45f),
                    Offset(frontAxleX, bottom - wheelH * 0.55f),
                    Offset(rearAxleX, top - wheelH * 0.45f),
                    Offset(rearAxleX, bottom - wheelH * 0.55f)
                )
                wheelOffsets.forEach { wo ->
                    drawRoundRect(
                        color = Color(0xFF161B24),
                        topLeft = wo,
                        size = Size(wheelW, wheelH),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                    drawRoundRect(
                        color = wireColor,
                        topLeft = wo,
                        size = Size(wheelW, wheelH),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Outer translucent Kia Rio body shell
                drawRoundRect(
                    color = Color(0xFF0E121A),
                    topLeft = Offset(left, top),
                    size = Size(carW, carH),
                    cornerRadius = CornerRadius(carH * 0.36f, carH * 0.36f)
                )
                drawRoundRect(
                    color = wireColor,
                    topLeft = Offset(left, top),
                    size = Size(carW, carH),
                    cornerRadius = CornerRadius(carH * 0.36f, carH * 0.36f),
                    style = Stroke(width = stroke)
                )

                // Internal X-ray drivetrain & cabin structure
                // Front radiator core
                drawRoundRect(
                    color = innerWire,
                    topLeft = Offset(left + carW * 0.04f, top + carH * 0.22f),
                    size = Size(carW * 0.035f, carH * 0.56f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                // Gamma 1.4L transverse engine block + transaxle
                drawRoundRect(
                    color = innerWire,
                    topLeft = Offset(left + carW * 0.11f, top + carH * 0.24f),
                    size = Size(carW * 0.15f, carH * 0.52f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
                // Front & rear axle shafts
                drawLine(innerWire, Offset(frontAxleX + wheelW * 0.5f, top), Offset(frontAxleX + wheelW * 0.5f, bottom), 1.dp.toPx())
                drawLine(innerWire, Offset(rearAxleX + wheelW * 0.5f, top), Offset(rearAxleX + wheelW * 0.5f, bottom), 1.dp.toPx())
                // Cabin greenhouse frame
                drawRoundRect(
                    color = innerWire,
                    topLeft = Offset(left + carW * 0.32f, top + carH * 0.14f),
                    size = Size(carW * 0.52f, carH * 0.72f),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = Stroke(width = 0.9.dp.toPx())
                )

                // Compute glowing target coordinates for the selected maintenance item
                val hotspots: List<Offset> = when (item.id) {
                    "engine_oil" -> listOf(Offset(left + carW * 0.18f, cy))
                    "air_cabin_filter" -> listOf(
                        Offset(left + carW * 0.15f, top + carH * 0.28f),
                        Offset(left + carW * 0.34f, cy)
                    )
                    "ac_service" -> listOf(
                        Offset(left + carW * 0.13f, bottom - carH * 0.26f),
                        Offset(left + carW * 0.33f, cy)
                    )
                    "coolant" -> listOf(Offset(left + carW * 0.055f, cy))
                    "battery" -> listOf(Offset(left + carW * 0.14f, bottom - carH * 0.22f))
                    "trans_oil_plugs" -> listOf(
                        Offset(left + carW * 0.19f, cy),
                        Offset(left + carW * 0.22f, bottom - carH * 0.30f)
                    )
                    "brake_pads" -> listOf(
                        Offset(frontAxleX + wheelW * 0.5f, top + 2.dp.toPx()),
                        Offset(frontAxleX + wheelW * 0.5f, bottom - 2.dp.toPx()),
                        Offset(rearAxleX + wheelW * 0.5f, top + 2.dp.toPx()),
                        Offset(rearAxleX + wheelW * 0.5f, bottom - 2.dp.toPx())
                    )
                    "tire_rotation", "tires" -> listOf(
                        Offset(frontAxleX + wheelW * 0.5f, top - wheelH * 0.1f),
                        Offset(frontAxleX + wheelW * 0.5f, bottom + wheelH * 0.1f),
                        Offset(rearAxleX + wheelW * 0.5f, top - wheelH * 0.1f),
                        Offset(rearAxleX + wheelW * 0.5f, bottom + wheelH * 0.1f)
                    )
                    else -> listOf(Offset(left + carW * 0.20f, cy))
                }

                val targetColor = if (accent == ObsidianColors.EmeraldNominal) ObsidianColors.CobaltAccent else accent
                hotspots.forEach { pt ->
                    drawCircle(color = targetColor.copy(alpha = 0.20f), radius = 12.dp.toPx(), center = pt)
                    drawCircle(
                        color = targetColor.copy(alpha = 0.70f),
                        radius = 7.dp.toPx(),
                        center = pt,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    drawCircle(color = targetColor, radius = 3.2.dp.toPx(), center = pt)
                }
            }
        }
    }
}

private enum class TireRotationPattern {
    FWD_CROSS,
    DIRECTIONAL
}

/**
 * Interactive Kia Rio FWD Tire Rotation, Spooring & Balancing visual guide
 * inspired by the Obrolan Bengkel reel: supports Standard FWD Cross pattern
 * (Front straight to Rear, Rear crossed to Front) and Directional pattern (Front <-> Rear same side).
 */
@Composable
private fun TireRotationGuideCard() {
    val isId = LocalAppLanguage.current == AppLanguage.ID
    var pattern by remember { mutableStateOf(TireRotationPattern.FWD_CROSS) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianColors.ObsidianPanel)
            .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isId) "POLA ROTASI BAN (FWD)" else "TIRE ROTATION PATTERN (FWD)",
                color = ObsidianColors.DimSilver,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )
            SegmentedPillRow(
                options = listOf(
                    TireRotationPattern.FWD_CROSS to (if (isId) "FWD Silang" else "FWD Cross"),
                    TireRotationPattern.DIRECTIONAL to (if (isId) "Ban Searah" else "Directional")
                ),
                selectedValue = pattern,
                onSelect = { pattern = it }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF090B0F))
                .border(0.8.dp, ObsidianColors.ObsidianSubBorder, RoundedCornerShape(9.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Horizontal Kia Rio chassis (FRONT on LEFT, REAR on RIGHT)
                val carW = w * 0.56f
                val carH = h * 0.48f
                val left = (w - carW) * 0.5f
                val top = (h - carH) * 0.5f
                val bottom = top + carH

                drawRoundRect(
                    color = Color(0xFF0E121A),
                    topLeft = Offset(left, top),
                    size = Size(carW, carH),
                    cornerRadius = CornerRadius(carH * 0.36f, carH * 0.36f)
                )
                drawRoundRect(
                    color = Color(0xFF242B3A),
                    topLeft = Offset(left, top),
                    size = Size(carW, carH),
                    cornerRadius = CornerRadius(carH * 0.36f, carH * 0.36f),
                    style = Stroke(width = 1.1.dp.toPx())
                )

                // 4 Wheel centers (Front axle on left, Rear axle on right)
                val frontX = left + carW * 0.20f
                val rearX = left + carW * 0.80f
                val topWheelY = top - 6.dp.toPx()
                val botWheelY = bottom + 6.dp.toPx()

                val fl = Offset(frontX, botWheelY)
                val fr = Offset(frontX, topWheelY)
                val rl = Offset(rearX, botWheelY)
                val rr = Offset(rearX, topWheelY)

                val wheelW = 24.dp.toPx()
                val wheelH = 11.dp.toPx()
                listOf(fr to ObsidianColors.AmberCaution, fl to ObsidianColors.AmberCaution, rr to ObsidianColors.EmeraldNominal, rl to ObsidianColors.EmeraldNominal).forEach { (c, rimColor) ->
                    drawRoundRect(
                        color = Color(0xFF141922),
                        topLeft = Offset(c.x - wheelW * 0.5f, c.y - wheelH * 0.5f),
                        size = Size(wheelW, wheelH),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                    drawRoundRect(
                        color = rimColor.copy(alpha = 0.75f),
                        topLeft = Offset(c.x - wheelW * 0.5f, c.y - wheelH * 0.5f),
                        size = Size(wheelW, wheelH),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }

                fun drawArrow(start: Offset, end: Offset, color: Color, bidirectional: Boolean = false) {
                    val strokeW = 1.8.dp.toPx()
                    drawLine(color, start, end, strokeWidth = strokeW, cap = StrokeCap.Round)
                    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
                    val headLen = 5.5.dp.toPx()
                    val a1 = angle - Math.toRadians(150.0)
                    val a2 = angle + Math.toRadians(150.0)
                    drawLine(
                        color,
                        end,
                        Offset(end.x + (cos(a1) * headLen).toFloat(), end.y + (sin(a1) * headLen).toFloat()),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color,
                        end,
                        Offset(end.x + (cos(a2) * headLen).toFloat(), end.y + (sin(a2) * headLen).toFloat()),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    if (bidirectional) {
                        val b1 = angle - Math.toRadians(30.0)
                        val b2 = angle + Math.toRadians(30.0)
                        drawLine(
                            color,
                            start,
                            Offset(start.x + (cos(b1) * headLen).toFloat(), start.y + (sin(b1) * headLen).toFloat()),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color,
                            start,
                            Offset(start.x + (cos(b2) * headLen).toFloat(), start.y + (sin(b2) * headLen).toFloat()),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                    }
                }

                val padX = wheelW * 0.65f
                val padY = wheelH * 0.70f
                if (pattern == TireRotationPattern.FWD_CROSS) {
                    // Front moves straight back (FR -> RR on top, FL -> RL on bottom) in Emerald
                    drawArrow(
                        start = Offset(fr.x + padX, fr.y),
                        end = Offset(rr.x - padX, rr.y),
                        color = ObsidianColors.EmeraldNominal
                    )
                    drawArrow(
                        start = Offset(fl.x + padX, fl.y),
                        end = Offset(rl.x - padX, rl.y),
                        color = ObsidianColors.EmeraldNominal
                    )
                    // Rear crosses diagonally forward (RR -> FL, RL -> FR) in Cobalt
                    drawArrow(
                        start = Offset(rr.x - padX * 0.5f, rr.y + padY),
                        end = Offset(fl.x + padX * 0.5f, fl.y - padY),
                        color = ObsidianColors.CobaltAccent
                    )
                    drawArrow(
                        start = Offset(rl.x - padX * 0.5f, rl.y - padY),
                        end = Offset(fr.x + padX * 0.5f, fr.y + padY),
                        color = ObsidianColors.CobaltAccent
                    )
                } else {
                    // Directional tires: Front <-> Rear on the same side
                    drawArrow(
                        start = Offset(fr.x + padX, fr.y),
                        end = Offset(rr.x - padX, rr.y),
                        color = ObsidianColors.CobaltAccent,
                        bidirectional = true
                    )
                    drawArrow(
                        start = Offset(fl.x + padX, fl.y),
                        end = Offset(rl.x - padX, rl.y),
                        color = ObsidianColors.CobaltAccent,
                        bidirectional = true
                    )
                }
            }

            // Corner orientation labels
            Text(
                text = if (isId) "← DEPAN (FWD)" else "← FRONT (FWD)",
                color = ObsidianColors.AmberCaution,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
            )
            Text(
                text = if (isId) "BELAKANG →" else "REAR →",
                color = ObsidianColors.EmeraldNominal,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
            )
        }

        Text(
            text = when (pattern) {
                TireRotationPattern.FWD_CROSS ->
                    if (isId) "Ban depan pindah lurus ke belakang (hijau) · Ban belakang pindah silang ke depan (biru). Standar buku servis Kia 10.000 km (atau 5.000 km jika pemakaian berat)."
                    else "Front tires move straight to rear (green) · Rear tires cross diagonally to front (blue). Kia manual standard: 10,000 km (or 5,000 km severe use)."
                TireRotationPattern.DIRECTIONAL ->
                    if (isId) "Ban dengan tanda panah arah putar: tukar ban depan ↔ belakang di sisi yang sama tanpa menyilang + cek Spooring & Balancing."
                    else "Directional tread tires: swap front ↔ rear on the same side without crossing + check Spooring & Balancing."
            },
            color = ObsidianColors.MutedSilver,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}
