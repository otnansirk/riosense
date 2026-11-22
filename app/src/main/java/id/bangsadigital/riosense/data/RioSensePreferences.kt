package id.bangsadigital.riosense.data

import android.content.Context
import android.content.SharedPreferences
import id.bangsadigital.riosense.model.KiaRioPaintColor
import id.bangsadigital.riosense.model.MaintenanceItem
import id.bangsadigital.riosense.model.MaintenanceState
import id.bangsadigital.riosense.model.ServiceRecord
import id.bangsadigital.riosense.model.VehicleTelemetry
import id.bangsadigital.riosense.ui.i18n.AppLanguage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight local persistence layer backed by Android SharedPreferences.
 * Ensures custom alarm thresholds, vehicle finish, mute state, odometer, and
 * fast-moving part maintenance schedules survive Head Unit cold boots.
 */
class RioSensePreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "riosense_persistent_state"

        private const val KEY_SPEED_LOCK_KMH = "speed_lock_threshold_kmh"
        private const val KEY_TPMS_LOW_PSI = "tpms_low_threshold_psi"
        private const val KEY_TPMS_HIGH_PSI = "tpms_high_threshold_psi"
        private const val KEY_TPMS_HIGH_TEMP_C = "tpms_high_temp_c"
        private const val KEY_COOLANT_OVERHEAT_C = "coolant_overheat_threshold_c"
        private const val KEY_BATTERY_LOW_V = "battery_low_threshold_v"
        private const val KEY_BATTERY_HIGH_V = "battery_high_threshold_v"

        private const val KEY_PAINT_COLOR = "paint_color"
        private const val KEY_AUDIO_MUTED = "audio_alarm_muted"
        private const val KEY_APP_LANGUAGE = "app_language"

        private const val KEY_ODOMETER_KM = "maintenance_odometer_km"
        private const val KEY_REMIND_DAYS = "maintenance_remind_days"
        private const val KEY_REMIND_KM = "maintenance_remind_km"
        private const val KEY_MAINTENANCE_ITEMS = "maintenance_items_json"
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadPersistedTelemetryOverrides(base: VehicleTelemetry): VehicleTelemetry {
        val paintName = prefs.getString(KEY_PAINT_COLOR, base.paintColor.name)
        val paintColor = runCatching {
            KiaRioPaintColor.valueOf(paintName ?: base.paintColor.name)
        }.getOrDefault(base.paintColor)

        return base.copy(
            paintColor = paintColor,
            audioAlarmMuted = prefs.getBoolean(KEY_AUDIO_MUTED, base.audioAlarmMuted),
            speedLockAlarmThresholdKmh = prefs.getInt(
                KEY_SPEED_LOCK_KMH,
                VehicleTelemetry.DEFAULT_SPEED_LOCK_THRESHOLD_KMH
            ),
            tpmsLowThresholdPsi = prefs.getFloat(
                KEY_TPMS_LOW_PSI,
                VehicleTelemetry.DEFAULT_TPMS_LOW_PSI
            ),
            tpmsHighThresholdPsi = prefs.getFloat(
                KEY_TPMS_HIGH_PSI,
                VehicleTelemetry.DEFAULT_TPMS_HIGH_PSI
            ),
            tpmsHighTempC = prefs.getInt(
                KEY_TPMS_HIGH_TEMP_C,
                VehicleTelemetry.DEFAULT_TPMS_HIGH_TEMP_C
            ),
            coolantOverheatThresholdC = prefs.getInt(
                KEY_COOLANT_OVERHEAT_C,
                VehicleTelemetry.DEFAULT_COOLANT_OVERHEAT_C
            ),
            batteryLowThresholdV = prefs.getFloat(
                KEY_BATTERY_LOW_V,
                VehicleTelemetry.DEFAULT_BATTERY_LOW_V
            ),
            batteryHighThresholdV = prefs.getFloat(
                KEY_BATTERY_HIGH_V,
                VehicleTelemetry.DEFAULT_BATTERY_HIGH_V
            )
        )
    }

    fun saveTelemetrySettings(telemetry: VehicleTelemetry) {
        prefs.edit()
            .putString(KEY_PAINT_COLOR, telemetry.paintColor.name)
            .putBoolean(KEY_AUDIO_MUTED, telemetry.audioAlarmMuted)
            .putInt(KEY_SPEED_LOCK_KMH, telemetry.speedLockAlarmThresholdKmh)
            .putFloat(KEY_TPMS_LOW_PSI, telemetry.tpmsLowThresholdPsi)
            .putFloat(KEY_TPMS_HIGH_PSI, telemetry.tpmsHighThresholdPsi)
            .putInt(KEY_TPMS_HIGH_TEMP_C, telemetry.tpmsHighTempC)
            .putInt(KEY_COOLANT_OVERHEAT_C, telemetry.coolantOverheatThresholdC)
            .putFloat(KEY_BATTERY_LOW_V, telemetry.batteryLowThresholdV)
            .putFloat(KEY_BATTERY_HIGH_V, telemetry.batteryHighThresholdV)
            .apply()
    }

    fun loadAppLanguage(): AppLanguage {
        val saved = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.EN.name)
        return runCatching { AppLanguage.valueOf(saved ?: AppLanguage.EN.name) }
            .getOrDefault(AppLanguage.EN)
    }

    fun saveAppLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.name).apply()
    }

    fun loadMaintenanceState(): MaintenanceState {
        val odometer = prefs.getInt(KEY_ODOMETER_KM, MaintenanceState.DEFAULT_ODOMETER_KM)
        val remindDays = prefs.getInt(KEY_REMIND_DAYS, MaintenanceState.DEFAULT_REMIND_DAYS_BEFORE)
        val remindKm = prefs.getInt(KEY_REMIND_KM, MaintenanceState.DEFAULT_REMIND_KM_BEFORE)
        val rawJson = prefs.getString(KEY_MAINTENANCE_ITEMS, null)

        val items = if (rawJson.isNullOrBlank()) {
            val defaults = MaintenanceState.defaultKiaRioItems(baseOdometerKm = odometer)
            val initialState = MaintenanceState(
                currentOdometerKm = odometer,
                remindDaysBefore = remindDays,
                remindKmBefore = remindKm,
                items = defaults
            )
            saveMaintenanceState(initialState)
            defaults
        } else {
            parseItemsJson(rawJson).ifEmpty {
                MaintenanceState.defaultKiaRioItems(baseOdometerKm = odometer)
            }
        }

        return MaintenanceState(
            currentOdometerKm = odometer,
            remindDaysBefore = remindDays,
            remindKmBefore = remindKm,
            items = items
        )
    }

    fun saveMaintenanceState(state: MaintenanceState) {
        val arr = JSONArray()
        state.items.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("category", item.category)
            obj.put("lastServiceDateMs", item.lastServiceDateMs)
            obj.put("lastServiceOdometerKm", item.lastServiceOdometerKm)
            obj.put("intervalMonths", item.intervalMonths)
            obj.put("intervalKm", item.intervalKm)
            obj.put("isCustom", item.isCustom)

            val histArr = JSONArray()
            item.history.forEach { rec ->
                val hObj = JSONObject()
                hObj.put("id", rec.id)
                hObj.put("dateMs", rec.dateMs)
                hObj.put("odometerKm", rec.odometerKm)
                hObj.put("costIdr", rec.costIdr)
                hObj.put("note", rec.note)
                histArr.put(hObj)
            }
            obj.put("history", histArr)
            arr.put(obj)
        }
        prefs.edit()
            .putInt(KEY_ODOMETER_KM, state.currentOdometerKm)
            .putInt(KEY_REMIND_DAYS, state.remindDaysBefore)
            .putInt(KEY_REMIND_KM, state.remindKmBefore)
            .putString(KEY_MAINTENANCE_ITEMS, arr.toString())
            .apply()
    }

    private fun parseItemsJson(raw: String): List<MaintenanceItem> {
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val histList = buildList {
                        val histArr = obj.optJSONArray("history")
                        if (histArr != null) {
                            for (j in 0 until histArr.length()) {
                                val hObj = histArr.getJSONObject(j)
                                add(
                                    ServiceRecord(
                                        id = hObj.optString("id", "rec_$j"),
                                        dateMs = hObj.getLong("dateMs"),
                                        odometerKm = hObj.getInt("odometerKm"),
                                        costIdr = hObj.optLong("costIdr", 0L),
                                        note = hObj.optString("note", "")
                                    )
                                )
                            }
                        }
                    }.sortedByDescending { it.dateMs }

                    val lastDate = histList.firstOrNull()?.dateMs ?: obj.getLong("lastServiceDateMs")
                    val lastOdo = histList.firstOrNull()?.odometerKm ?: obj.getInt("lastServiceOdometerKm")

                    add(
                        MaintenanceItem(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            category = obj.optString("category", "General"),
                            lastServiceDateMs = lastDate,
                            lastServiceOdometerKm = lastOdo,
                            intervalMonths = obj.getInt("intervalMonths"),
                            intervalKm = obj.getInt("intervalKm"),
                            isCustom = obj.optBoolean("isCustom", false),
                            history = histList
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
