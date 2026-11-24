package id.bangsadigital.riosense.ui.components

import android.app.DatePickerDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
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
import id.bangsadigital.riosense.ui.i18n.appStrings
import id.bangsadigital.riosense.ui.theme.ObsidianColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

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
                onResetStandard = onResetStandard,
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
    onResetStandard: () -> Unit,
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

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. Flat Overview Row: Odometer (inline edit) & Total Spent
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editingOdometer) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("ODO", color = ObsidianColors.DimSilver, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                    Box(
                        modifier = Modifier
                            .width(92.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ObsidianColors.ObsidianSubPanel)
                            .border(1.dp, ObsidianColors.ObsidianSubBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable {
                        odometerInput = maintenance.currentOdometerKm.toString()
                        editingOdometer = true
                    }
                ) {
                    Text("ODO", color = ObsidianColors.DimSilver, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${String.format("%,d", maintenance.currentOdometerKm)} km",
                        color = ObsidianColors.PureSilver,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = s.edit,
                        color = ObsidianColors.CobaltAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = s.totalSpent,
                        color = ObsidianColors.DimSilver,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = formatIdr(maintenance.totalMaintenanceCostIdr),
                        color = ObsidianColors.PureSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        ServiceHairlineDivider()

        // 2. Flat Reminder Lead-Time & Actions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = s.remind,
                    color = ObsidianColors.DimSilver,
                    fontSize = 9.5.sp
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

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                CompactActionChip(
                    label = s.addBtn,
                    accent = ObsidianColors.CobaltAccent,
                    bg = ObsidianColors.CobaltSurface,
                    onClick = onOpenAddForm
                )
                CompactActionChip(label = s.resetBtn, onClick = onResetStandard)
            }
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

        // 2. Reminder Interval Presets Group
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianColors.ObsidianPanel)
                .border(1.dp, ObsidianColors.ObsidianBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = s.reminderIntervalTitle,
                color = ObsidianColors.DimSilver,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )

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
                    options = listOf(0 to s.offLabel, 5000 to "5k", 10000 to "10k", 20000 to "20k", 40000 to "40k"),
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
                    options = listOf(0 to s.offLabel, 6 to "6m", 12 to "12m", 24 to "24m"),
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
                    options = listOf(0 to s.offLabel, 5000 to "5k", 10000 to "10k", 20000 to "20k", 40000 to "40k"),
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
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF0E1015))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { (value, label) ->
            val selected = value == selectedValue
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (selected) Color(0xFF212836) else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Text(
                    text = label,
                    color = if (selected) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
                    fontSize = 9.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
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
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = accent,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold
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
