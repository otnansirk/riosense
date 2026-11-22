package id.bangsadigital.riosense.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import id.bangsadigital.riosense.ui.theme.ObsidianColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Overview Header: Odometer (direct input), Total Spent & Reminder Lead-Time
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF14171E))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                                .background(Color(0xFF0E1015))
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
                        CompactChipButton(label = "Save", highlighted = true) {
                            odometerInput.toIntOrNull()?.let { onUpdateOdometer(it) }
                            editingOdometer = false
                        }
                        CompactChipButton(label = "Cancel") {
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
                        Text("ODO", color = ObsidianColors.DimSilver, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${String.format("%,d", maintenance.currentOdometerKm)} km",
                            color = ObsidianColors.PureSilver,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text("Edit", color = Color(0xFF63B3ED), fontSize = 9.5.sp, fontWeight = FontWeight.Medium)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "TOTAL SPENT",
                            color = ObsidianColors.DimSilver,
                            fontSize = 8.sp,
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Remind:",
                        color = ObsidianColors.DimSilver,
                        fontSize = 9.5.sp
                    )
                    listOf(3, 7, 14).forEach { days ->
                        CompactChipButton(
                            label = "${days}d",
                            highlighted = maintenance.remindDaysBefore == days,
                            onClick = { onRemindDaysChange(days) }
                        )
                    }
                    listOf(500, 1000).forEach { km ->
                        CompactChipButton(
                            label = "${km}km",
                            highlighted = maintenance.remindKmBefore == km,
                            onClick = { onRemindKmChange(km) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CompactChipButton(label = "+ Add", highlighted = true, onClick = onOpenAddForm)
                    CompactChipButton(label = "Reset", onClick = onResetStandard)
                }
            }
        }

        // Fast-Moving Parts List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(sortedItems, key = { it.id }) { item ->
                val status = item.evaluateStatus(
                    nowMs = nowMs,
                    currentOdometerKm = maintenance.currentOdometerKm,
                    remindDaysBefore = maintenance.remindDaysBefore,
                    remindKmBefore = maintenance.remindKmBefore
                )
                val dotColor = maintenanceStatusColor(status)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF14171E))
                        .clickable { onSelectItem(item) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
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
                            Text(
                                text = item.name,
                                color = ObsidianColors.PureSilver,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.quickSummary(nowMs, maintenance.currentOdometerKm),
                                color = if (status == MaintenanceStatus.OK) ObsidianColors.DimSilver else dotColor,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (item.totalCostIdr > 0L) {
                            Text(
                                text = formatIdr(item.totalCostIdr),
                                color = ObsidianColors.DimSilver,
                                fontSize = 9.5.sp,
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
        // 1. Schedule Overview Card + Primary Record Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF14171E))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
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
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "${item.category} · ${item.quickSummary(nowMs, maintenance.currentOdometerKm)}",
                color = if (status == MaintenanceStatus.OK) ObsidianColors.DimSilver else accent,
                fontSize = 10.5.sp
            )

            ServiceMetaRow("Last Service", "${formatDateOnly(item.lastServiceDateMs)} · ${String.format("%,d", item.lastServiceOdometerKm)} km")
            ServiceMetaRow(
                "Next Due",
                buildString {
                    append(item.nextDueDateMs()?.let { formatDateOnly(it) } ?: "-")
                    item.nextDueOdometerKm()?.let { append(" · ${String.format("%,d", it)} km") }
                }
            )
            ServiceMetaRow("Part Total Spent", formatIdr(item.totalCostIdr))

            Spacer(modifier = Modifier.height(3.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF1D2C24))
                    .clickable { onOpenRecordForm() }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ Record Service (Today or Past Date)",
                    color = Color(0xFF68D391),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 2. Permanent Service History List
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF14171E))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = "SERVICE HISTORY (${item.history.size})",
                color = ObsidianColors.DimSilver,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            )

            if (item.history.isEmpty()) {
                Text(
                    text = "No service records logged yet.",
                    color = ObsidianColors.DimSilver,
                    fontSize = 10.5.sp
                )
            } else {
                item.history.forEach { record ->
                    ServiceHistoryRow(
                        record = record,
                        canDelete = item.history.size > 1,
                        onDelete = { onDeleteHistoryRecord(record.id) }
                    )
                }
            }
        }

        // 3. Reminder Schedule Presets (Clean & Fast on Car Touchscreen)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF14171E))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "REMINDER INTERVAL",
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
                Text("Time", color = ObsidianColors.MutedSilver, fontSize = 10.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0 to "Off", 6 to "6m", 12 to "12m", 24 to "24m", 48 to "48m").forEach { (months, label) ->
                        CompactChipButton(
                            label = label,
                            highlighted = item.intervalMonths == months,
                            onClick = { onUpdateSchedule(months, item.intervalKm) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Distance", color = ObsidianColors.MutedSilver, fontSize = 10.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0 to "Off", 5000 to "5k", 10000 to "10k", 20000 to "20k", 40000 to "40k").forEach { (km, label) ->
                        CompactChipButton(
                            label = label,
                            highlighted = item.intervalKm == km,
                            onClick = { onUpdateSchedule(item.intervalMonths, km) }
                        )
                    }
                }
            }

            if (onDelete != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF281A1D))
                        .clickable { onDelete() }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Delete Custom Part",
                        color = ObsidianColors.CrimsonAlert,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
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
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0E1015))
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
                        color = Color(0xFF68D391),
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
                .background(Color(0xFF14171E))
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
                    Text("Service Date", color = ObsidianColors.MutedSilver, fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CompactChipButton("Today") { selectedDateMs = System.currentTimeMillis() }
                        CompactChipButton("Yesterday") {
                            selectedDateMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0E1015))
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
                        text = "Pick Date",
                        color = Color(0xFF63B3ED),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Odometer Input
            FormInputField(
                label = "Odometer at Service (km)",
                value = odoText,
                placeholder = currentOdometerKm.toString(),
                keyboardType = KeyboardType.Number,
                onValueChange = { odoText = it.filter { ch -> ch.isDigit() }.take(6) }
            )

            // Optional Cost Input (IDR)
            val parsedCost = costText.toLongOrNull() ?: 0L
            FormInputField(
                label = if (parsedCost > 0L) "Total Cost (Optional · ${formatIdr(parsedCost)})" else "Total Cost in Rp (Optional)",
                value = costText,
                placeholder = "e.g. 550000",
                keyboardType = KeyboardType.Number,
                onValueChange = { costText = it.filter { ch -> ch.isDigit() }.take(9) }
            )

            // Optional Multiline Notes
            FormInputField(
                label = "Notes (Optional)",
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
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (canSave) Color(0xFF1D2C24) else Color(0xFF181B22))
                    .clickable(enabled = canSave) {
                        onSave(selectedDateMs, parsedOdo ?: currentOdometerKm, parsedCost, noteText)
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Save Service Record",
                    color = if (canSave) Color(0xFF68D391) else ObsidianColors.DimSilver,
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
                .background(Color(0xFF14171E))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FormInputField(
                label = "Part / Service Name",
                value = partName,
                placeholder = "e.g. Wiper Blades, Fan Belt, Brake Fluid",
                keyboardType = KeyboardType.Text,
                onValueChange = { partName = it }
            )

            // Date picker
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Last Service Date", color = ObsidianColors.MutedSilver, fontSize = 10.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0E1015))
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
                    Text("Pick Date", color = Color(0xFF63B3ED), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }

            FormInputField(
                label = "Odometer at Service (km)",
                value = odoText,
                placeholder = currentOdometerKm.toString(),
                keyboardType = KeyboardType.Number,
                onValueChange = { odoText = it.filter { ch -> ch.isDigit() }.take(6) }
            )

            val parsedCost = costText.toLongOrNull() ?: 0L
            FormInputField(
                label = if (parsedCost > 0L) "Cost (Optional · ${formatIdr(parsedCost)})" else "Cost in Rp (Optional)",
                value = costText,
                placeholder = "e.g. 250000",
                keyboardType = KeyboardType.Number,
                onValueChange = { costText = it.filter { ch -> ch.isDigit() }.take(9) }
            )

            FormInputField(
                label = "Notes (Optional)",
                value = noteText,
                placeholder = "e.g.\n- Bosch Advantage 24/16 inch\n- Rear wiper blade",
                keyboardType = KeyboardType.Text,
                multiline = true,
                onValueChange = { noteText = it }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Time interval", color = ObsidianColors.MutedSilver, fontSize = 10.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0 to "Off", 6 to "6m", 12 to "12m", 24 to "24m").forEach { (m, label) ->
                        CompactChipButton(label = label, highlighted = intervalMonths == m) {
                            intervalMonths = m
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Distance", color = ObsidianColors.MutedSilver, fontSize = 10.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0 to "Off", 5000 to "5k", 10000 to "10k", 20000 to "20k", 40000 to "40k").forEach { (k, label) ->
                        CompactChipButton(label = label, highlighted = intervalKm == k) {
                            intervalKm = k
                        }
                    }
                }
            }

            val canSave = partName.trim().isNotEmpty()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (canSave) Color(0xFF212A38) else Color(0xFF181B22))
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
                    text = "Save Maintenance Part",
                    color = if (canSave) ObsidianColors.PureSilver else ObsidianColors.DimSilver,
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
                .background(Color(0xFF0E1015))
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
private fun CompactChipButton(
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (highlighted) Color(0xFF212836) else Color(0xFF1B1F28))
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = if (highlighted) ObsidianColors.PureSilver else ObsidianColors.MutedSilver,
            fontSize = 9.5.sp,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun ServiceMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF0E1015))
            .padding(horizontal = 10.dp, vertical = 6.dp),
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

fun maintenanceStatusColor(status: MaintenanceStatus): Color = when (status) {
    MaintenanceStatus.OVERDUE -> ObsidianColors.CrimsonAlert
    MaintenanceStatus.DUE_SOON -> Color(0xFFE6A23C)
    MaintenanceStatus.OK -> Color(0xFF38A169)
}

private fun formatDateOnly(timestampMs: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return sdf.format(Date(timestampMs))
}
