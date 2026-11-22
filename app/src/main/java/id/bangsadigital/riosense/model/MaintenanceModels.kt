package id.bangsadigital.riosense.model

import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Health status of a scheduled vehicle maintenance / fast-moving part item.
 */
enum class MaintenanceStatus(val label: String) {
    OK("OK"),
    DUE_SOON("Due Soon"),
    OVERDUE("Overdue")
}

/**
 * A single permanent service history entry for a fast-moving part.
 */
data class ServiceRecord(
    val id: String,
    val dateMs: Long,
    val odometerKm: Int,
    val costIdr: Long = 0L,
    val note: String = ""
)

/**
 * Represents a single scheduled maintenance or fast-moving part with full permanent service history.
 */
data class MaintenanceItem(
    val id: String,
    val name: String,
    val category: String,
    val lastServiceDateMs: Long,
    val lastServiceOdometerKm: Int,
    val intervalMonths: Int,
    val intervalKm: Int,
    val isCustom: Boolean = false,
    val history: List<ServiceRecord> = emptyList()
) {
    val totalCostIdr: Long
        get() = history.sumOf { it.costIdr }

    val latestNote: String
        get() = history.firstOrNull()?.note.orEmpty()

    fun nextDueDateMs(): Long? {
        if (intervalMonths <= 0) return null
        val cal = Calendar.getInstance()
        cal.timeInMillis = lastServiceDateMs
        cal.add(Calendar.MONTH, intervalMonths)
        return cal.timeInMillis
    }

    fun daysRemaining(nowMs: Long = System.currentTimeMillis()): Int? {
        val due = nextDueDateMs() ?: return null
        val diffMs = due - nowMs
        return if (diffMs >= 0L) {
            ((diffMs + TimeUnit.DAYS.toMillis(1) - 1L) / TimeUnit.DAYS.toMillis(1)).toInt()
        } else {
            (diffMs / TimeUnit.DAYS.toMillis(1)).toInt()
        }
    }

    fun nextDueOdometerKm(): Int? =
        if (intervalKm > 0) lastServiceOdometerKm + intervalKm else null

    fun kmRemaining(currentOdometerKm: Int): Int? {
        val dueKm = nextDueOdometerKm() ?: return null
        return dueKm - currentOdometerKm
    }

    fun evaluateStatus(
        nowMs: Long,
        currentOdometerKm: Int,
        remindDaysBefore: Int,
        remindKmBefore: Int
    ): MaintenanceStatus {
        val daysLeft = daysRemaining(nowMs)
        val kmLeft = kmRemaining(currentOdometerKm)

        val overdueByDate = daysLeft != null && daysLeft <= 0
        val overdueByKm = kmLeft != null && kmLeft <= 0
        if (overdueByDate || overdueByKm) return MaintenanceStatus.OVERDUE

        val soonByDate = daysLeft != null && daysLeft <= remindDaysBefore
        val soonByKm = kmLeft != null && kmLeft <= remindKmBefore
        if (soonByDate || soonByKm) return MaintenanceStatus.DUE_SOON

        return MaintenanceStatus.OK
    }

    fun quickSummary(nowMs: Long, currentOdometerKm: Int): String {
        val parts = buildList {
            daysRemaining(nowMs)?.let { d ->
                when {
                    d < 0 -> add("Overdue ${-d}d")
                    d == 0 -> add("Due today")
                    else -> add("In ${d}d")
                }
            }
            kmRemaining(currentOdometerKm)?.let { km ->
                when {
                    km < 0 -> add("Over ${-km} km")
                    km == 0 -> add("0 km left")
                    else -> add("${String.format("%,d", km)} km left")
                }
            }
        }
        return if (parts.isEmpty()) "No schedule set" else parts.joinToString(" · ")
    }
}

/**
 * Aggregated maintenance reminder & spending state persisted locally on the Head Unit.
 */
data class MaintenanceState(
    val currentOdometerKm: Int = DEFAULT_ODOMETER_KM,
    val remindDaysBefore: Int = DEFAULT_REMIND_DAYS_BEFORE,
    val remindKmBefore: Int = DEFAULT_REMIND_KM_BEFORE,
    val items: List<MaintenanceItem> = emptyList()
) {
    val totalMaintenanceCostIdr: Long
        get() = items.sumOf { it.totalCostIdr }

    companion object {
        const val DEFAULT_ODOMETER_KM = 85_000
        const val DEFAULT_REMIND_DAYS_BEFORE = 7
        const val DEFAULT_REMIND_KM_BEFORE = 500

        /**
         * Recommended Kia Rio UB (2012–2016) fast-moving maintenance schedule.
         */
        fun defaultKiaRioItems(
            nowMs: Long = System.currentTimeMillis(),
            baseOdometerKm: Int = DEFAULT_ODOMETER_KM
        ): List<MaintenanceItem> {
            fun monthsAgo(months: Int, extraDays: Int = 0): Long {
                val cal = Calendar.getInstance()
                cal.timeInMillis = nowMs
                cal.add(Calendar.MONTH, -months)
                cal.add(Calendar.DAY_OF_YEAR, -extraDays)
                return cal.timeInMillis
            }

            fun buildItem(
                id: String,
                name: String,
                category: String,
                dateMs: Long,
                odoKm: Int,
                intervalMonths: Int,
                intervalKm: Int,
                sampleCostIdr: Long = 0L,
                sampleNote: String = ""
            ): MaintenanceItem {
                val clampedOdo = odoKm.coerceAtLeast(0)
                return MaintenanceItem(
                    id = id,
                    name = name,
                    category = category,
                    lastServiceDateMs = dateMs,
                    lastServiceOdometerKm = clampedOdo,
                    intervalMonths = intervalMonths,
                    intervalKm = intervalKm,
                    history = listOf(
                        ServiceRecord(
                            id = "${id}_init",
                            dateMs = dateMs,
                            odometerKm = clampedOdo,
                            costIdr = sampleCostIdr,
                            note = sampleNote
                        )
                    )
                )
            }

            return listOf(
                buildItem(
                    id = "engine_oil",
                    name = "Engine Oil & Filter",
                    category = "Engine",
                    dateMs = monthsAgo(5, 25),
                    odoKm = baseOdometerKm - 4650,
                    intervalMonths = 6,
                    intervalKm = 5000,
                    sampleCostIdr = 550_000L,
                    sampleNote = "Shell Helix HX7 10W-40 + OEM Oil Filter"
                ),
                buildItem(
                    id = "air_cabin_filter",
                    name = "Air & Cabin Filter",
                    category = "Intake / AC",
                    dateMs = monthsAgo(6),
                    odoKm = baseOdometerKm - 5200,
                    intervalMonths = 12,
                    intervalKm = 15000,
                    sampleCostIdr = 220_000L,
                    sampleNote = "OEM Air Filter + Carbon Cabin Filter"
                ),
                buildItem(
                    id = "ac_service",
                    name = "AC Service & Flushing",
                    category = "AC",
                    dateMs = monthsAgo(8),
                    odoKm = baseOdometerKm - 7000,
                    intervalMonths = 12,
                    intervalKm = 20000,
                    sampleCostIdr = 450_000L,
                    sampleNote = "Evaporator cleaning & Freon R134a top-up"
                ),
                buildItem(
                    id = "coolant",
                    name = "Engine Coolant",
                    category = "Cooling",
                    dateMs = monthsAgo(12),
                    odoKm = baseOdometerKm - 12000,
                    intervalMonths = 24,
                    intervalKm = 40000,
                    sampleCostIdr = 180_000L,
                    sampleNote = "Prestone Long-Life Coolant"
                ),
                buildItem(
                    id = "battery",
                    name = "Battery / Accu",
                    category = "Electrical",
                    dateMs = monthsAgo(14),
                    odoKm = baseOdometerKm - 14000,
                    intervalMonths = 24,
                    intervalKm = 0,
                    sampleCostIdr = 950_000L,
                    sampleNote = "GS Astra MF NS60L"
                ),
                buildItem(
                    id = "tires",
                    name = "Tires (Replacement)",
                    category = "Tires",
                    dateMs = monthsAgo(18),
                    odoKm = baseOdometerKm - 18000,
                    intervalMonths = 48,
                    intervalKm = 45000,
                    sampleCostIdr = 3_200_000L,
                    sampleNote = "4x Bridgestone Turanza 195/55 R16"
                ),
                buildItem(
                    id = "brake_pads",
                    name = "Brake Pads & Fluid",
                    category = "Brakes",
                    dateMs = monthsAgo(10),
                    odoKm = baseOdometerKm - 10000,
                    intervalMonths = 24,
                    intervalKm = 35000,
                    sampleCostIdr = 650_000L,
                    sampleNote = "Bendix Front Pads + DOT 4 Brake Fluid"
                ),
                buildItem(
                    id = "trans_oil_plugs",
                    name = "6MT Gear Oil & Spark Plugs",
                    category = "Drivetrain",
                    dateMs = monthsAgo(12),
                    odoKm = baseOdometerKm - 12000,
                    intervalMonths = 24,
                    intervalKm = 40000,
                    sampleCostIdr = 520_000L,
                    sampleNote = "MTF 75W-85 + 4x NGK Iridium"
                )
            )
        }
    }

    fun dueOrOverdueItems(nowMs: Long = System.currentTimeMillis()): List<MaintenanceItem> =
        items.filter {
            it.evaluateStatus(nowMs, currentOdometerKm, remindDaysBefore, remindKmBefore) != MaintenanceStatus.OK
        }.sortedBy {
            when (it.evaluateStatus(nowMs, currentOdometerKm, remindDaysBefore, remindKmBefore)) {
                MaintenanceStatus.OVERDUE -> 0
                MaintenanceStatus.DUE_SOON -> 1
                MaintenanceStatus.OK -> 2
            }
        }
}

fun formatIdr(amount: Long): String {
    if (amount <= 0L) return "Rp 0"
    val nf = NumberFormat.getIntegerInstance(Locale("in", "ID"))
    return "Rp ${nf.format(amount)}"
}
