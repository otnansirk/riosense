package id.bangsadigital.riosense.data

import android.content.Context
import android.content.SharedPreferences
import id.bangsadigital.riosense.launcher.InstalledAppsRepository
import id.bangsadigital.riosense.model.AppDrawerBackground
import id.bangsadigital.riosense.model.AppIconSize
import id.bangsadigital.riosense.model.BuiltInWidgetType
import id.bangsadigital.riosense.model.DockDisplayMode
import id.bangsadigital.riosense.model.DockPosition
import id.bangsadigital.riosense.model.KiaRioPaintColor
import id.bangsadigital.riosense.model.LauncherSettings
import id.bangsadigital.riosense.model.MAX_WIDGET_PAGES
import id.bangsadigital.riosense.model.MaintenanceItem
import id.bangsadigital.riosense.model.MaintenanceState
import id.bangsadigital.riosense.model.PlacedWidgetItem
import id.bangsadigital.riosense.model.ServiceRecord
import id.bangsadigital.riosense.model.TopBarBrandLogo
import id.bangsadigital.riosense.model.TripRecord
import id.bangsadigital.riosense.model.TripRoutePoint
import id.bangsadigital.riosense.model.TripSampleSeeder
import id.bangsadigital.riosense.model.VehicleTelemetry
import id.bangsadigital.riosense.model.VoiceButtonStyle
import id.bangsadigital.riosense.model.VoiceSttMode
import id.bangsadigital.riosense.model.WelcomeSoundMode
import id.bangsadigital.riosense.model.WidgetItemKind
import id.bangsadigital.riosense.model.WidgetWorkspaceState
import id.bangsadigital.riosense.ui.i18n.AppLanguage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
        private const val KEY_ENGINE_OFF_VOICE_REMINDER = "engine_off_voice_reminder_enabled"
        private const val KEY_WELCOME_SOUND_MODE = "welcome_sound_mode"
        private const val KEY_VOICE_STT_MODE = "voice_stt_mode"
        private const val KEY_VOICE_BUTTON_STYLE = "voice_button_style"
        private const val KEY_TRIP_RECORDING_ENABLED = "trip_recording_enabled"
        private const val KEY_TOP_BAR_BRAND_LOGO = "top_bar_brand_logo"
        private const val KEY_ROAD_EFFECT_ENABLED = "road_effect_enabled"
        private const val KEY_TRIP_SEEDED_V2 = "trip_showcase_seeded_v2"
        private const val KEY_APP_LANGUAGE = "app_language"

        private const val KEY_ODOMETER_KM = "maintenance_odometer_km"
        private const val KEY_ODOMETER_FRACTION_KM = "maintenance_odometer_fraction_km"
        private const val KEY_REMIND_DAYS = "maintenance_remind_days"
        private const val KEY_REMIND_KM = "maintenance_remind_km"
        private const val KEY_MAINTENANCE_ITEMS = "maintenance_items_json"
        private const val KEY_MAINTENANCE_STD_V2 = "maintenance_std_presets_v4"

        private const val KEY_LAUNCHER_DOCK_ENABLED = "launcher_dock_enabled"
        private const val KEY_LAUNCHER_DOCK_MODE = "launcher_dock_mode"
        private const val KEY_LAUNCHER_DOCK_POSITION = "launcher_dock_position"
        private const val KEY_LAUNCHER_DOCK_PINNED_JSON = "launcher_dock_pinned_json"
        private const val KEY_LAUNCHER_DOCK_PINNED_INIT = "launcher_dock_pinned_init_v1"
        private const val KEY_LAUNCHER_APP_ICON_SIZE = "launcher_app_icon_size"
        private const val KEY_LAUNCHER_APP_DRAWER_BG = "launcher_app_drawer_bg"
        private const val KEY_WIDGET_WORKSPACE_JSON = "widget_workspace_json"
    }

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val tripHistoryFile: File
        get() = File(appContext.filesDir, "riosense_trip_history.json")

    fun loadPersistedTelemetryOverrides(base: VehicleTelemetry): VehicleTelemetry {
        val paintName = prefs.getString(KEY_PAINT_COLOR, base.paintColor.name)
        val paintColor = runCatching {
            KiaRioPaintColor.valueOf(paintName ?: base.paintColor.name)
        }.getOrDefault(base.paintColor)

        val welcomeModeName = prefs.getString(KEY_WELCOME_SOUND_MODE, base.welcomeSoundMode.name)
        val welcomeMode = runCatching {
            WelcomeSoundMode.valueOf(welcomeModeName ?: base.welcomeSoundMode.name)
        }.getOrDefault(base.welcomeSoundMode)

        val sttModeName = prefs.getString(KEY_VOICE_STT_MODE, base.voiceSttMode.name)
        val sttMode = runCatching {
            VoiceSttMode.valueOf(sttModeName ?: base.voiceSttMode.name)
        }.getOrDefault(base.voiceSttMode)

        val btnStyleName = prefs.getString(KEY_VOICE_BUTTON_STYLE, base.voiceButtonStyle.name)
        val btnStyle = runCatching {
            VoiceButtonStyle.valueOf(btnStyleName ?: base.voiceButtonStyle.name)
        }.getOrDefault(base.voiceButtonStyle)

        val brandLogoName = prefs.getString(KEY_TOP_BAR_BRAND_LOGO, base.topBarBrandLogo.name)
        val brandLogo = runCatching {
            TopBarBrandLogo.valueOf(brandLogoName ?: base.topBarBrandLogo.name)
        }.getOrDefault(base.topBarBrandLogo)

        return base.copy(
            paintColor = paintColor,
            audioAlarmMuted = prefs.getBoolean(KEY_AUDIO_MUTED, base.audioAlarmMuted),
            engineOffVoiceReminderEnabled = prefs.getBoolean(
                KEY_ENGINE_OFF_VOICE_REMINDER,
                base.engineOffVoiceReminderEnabled
            ),
            welcomeSoundMode = welcomeMode,
            voiceSttMode = sttMode,
            voiceButtonStyle = btnStyle,
            tripRecordingEnabled = prefs.getBoolean(
                KEY_TRIP_RECORDING_ENABLED,
                base.tripRecordingEnabled
            ),
            topBarBrandLogo = brandLogo,
            roadEffectEnabled = prefs.getBoolean(KEY_ROAD_EFFECT_ENABLED, base.roadEffectEnabled),
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
            .putBoolean(KEY_ENGINE_OFF_VOICE_REMINDER, telemetry.engineOffVoiceReminderEnabled)
            .putString(KEY_WELCOME_SOUND_MODE, telemetry.welcomeSoundMode.name)
            .putString(KEY_VOICE_STT_MODE, telemetry.voiceSttMode.name)
            .putString(KEY_VOICE_BUTTON_STYLE, telemetry.voiceButtonStyle.name)
            .putBoolean(KEY_TRIP_RECORDING_ENABLED, telemetry.tripRecordingEnabled)
            .putString(KEY_TOP_BAR_BRAND_LOGO, telemetry.topBarBrandLogo.name)
            .putBoolean(KEY_ROAD_EFFECT_ENABLED, telemetry.roadEffectEnabled)
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

    fun loadOdometerFractionKm(): Double =
        prefs.getFloat(KEY_ODOMETER_FRACTION_KM, 0f).toDouble().coerceIn(0.0, 0.999)

    fun saveOdometerFractionKm(fractionKm: Double) {
        prefs.edit().putFloat(KEY_ODOMETER_FRACTION_KM, fractionKm.toFloat().coerceIn(0f, 0.999f)).apply()
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
            val parsed = parseItemsJson(rawJson).ifEmpty {
                MaintenanceState.defaultKiaRioItems(baseOdometerKm = odometer)
            }
            val withRotation = if (parsed.none { it.id == "tire_rotation" }) {
                val defaultRotation = MaintenanceState.defaultKiaRioItems(baseOdometerKm = odometer)
                    .firstOrNull { it.id == "tire_rotation" }
                if (defaultRotation != null) parsed + defaultRotation else parsed
            } else {
                parsed
            }
            if (!prefs.getBoolean(KEY_MAINTENANCE_STD_V2, false)) {
                val upgraded = withRotation.map { item ->
                    if (item.isCustom) item
                    else {
                        val (stdM, stdKm) = MaintenanceState.standardScheduleFor(item.id)
                        item.copy(intervalMonths = stdM, intervalKm = stdKm)
                    }
                }
                prefs.edit().putBoolean(KEY_MAINTENANCE_STD_V2, true).apply()
                saveMaintenanceState(
                    MaintenanceState(
                        currentOdometerKm = odometer,
                        remindDaysBefore = remindDays,
                        remindKmBefore = remindKm,
                        items = upgraded
                    )
                )
                upgraded
            } else {
                withRotation
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

    fun loadTripHistory(): List<TripRecord> {
        val file = tripHistoryFile
        val alreadySeeded = prefs.getBoolean(KEY_TRIP_SEEDED_V2, false)
        if (!file.exists()) {
            val defaults = TripSampleSeeder.defaultShowcaseTrips()
            saveTripHistory(defaults)
            prefs.edit().putBoolean(KEY_TRIP_SEEDED_V2, true).apply()
            return defaults
        }
        val parsed = runCatching {
            val raw = file.readText(Charsets.UTF_8)
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val ptsArr = obj.optJSONArray("routePoints")
                    val points = buildList {
                        if (ptsArr != null) {
                            for (j in 0 until ptsArr.length()) {
                                val p = ptsArr.getJSONObject(j)
                                add(
                                    TripRoutePoint(
                                        lat = p.getDouble("lat"),
                                        lng = p.getDouble("lng"),
                                        speedKmh = p.optInt("s", 0)
                                    )
                                )
                            }
                        }
                    }
                    add(
                        TripRecord(
                            id = obj.getString("id"),
                            startTimeMs = obj.getLong("startTimeMs"),
                            endTimeMs = obj.getLong("endTimeMs"),
                            distanceKm = obj.getDouble("distanceKm").toFloat(),
                            durationSec = obj.getLong("durationSec"),
                            idleSec = obj.optLong("idleSec", 0L),
                            avgSpeedKmh = obj.getDouble("avgSpeedKmh").toFloat(),
                            maxSpeedKmh = obj.getInt("maxSpeedKmh"),
                            maxRpm = obj.optInt("maxRpm", 0),
                            maxCoolantTempC = obj.optInt("maxCoolantTempC", 88),
                            minBatteryV = obj.optDouble("minBatteryV", 13.8).toFloat(),
                            estimatedFuelLiters = obj.optDouble("estimatedFuelLiters", 0.0).toFloat(),
                            startLabel = obj.optString("startLabel", ""),
                            endLabel = obj.optString("endLabel", ""),
                            routePoints = points
                        )
                    )
                }
            }.sortedByDescending { it.startTimeMs }
        }.getOrDefault(emptyList())

        if (parsed.isEmpty() && !alreadySeeded) {
            val defaults = TripSampleSeeder.defaultShowcaseTrips()
            saveTripHistory(defaults)
            prefs.edit().putBoolean(KEY_TRIP_SEEDED_V2, true).apply()
            return defaults
        }
        return parsed
    }

    fun saveTripHistory(trips: List<TripRecord>) {
        runCatching {
            val arr = JSONArray()
            trips.take(500).forEach { trip ->
                val obj = JSONObject()
                obj.put("id", trip.id)
                obj.put("startTimeMs", trip.startTimeMs)
                obj.put("endTimeMs", trip.endTimeMs)
                obj.put("distanceKm", trip.distanceKm.toDouble())
                obj.put("durationSec", trip.durationSec)
                obj.put("idleSec", trip.idleSec)
                obj.put("avgSpeedKmh", trip.avgSpeedKmh.toDouble())
                obj.put("maxSpeedKmh", trip.maxSpeedKmh)
                obj.put("maxRpm", trip.maxRpm)
                obj.put("maxCoolantTempC", trip.maxCoolantTempC)
                obj.put("minBatteryV", trip.minBatteryV.toDouble())
                obj.put("estimatedFuelLiters", trip.estimatedFuelLiters.toDouble())
                obj.put("startLabel", trip.startLabel)
                obj.put("endLabel", trip.endLabel)

                val ptsArr = JSONArray()
                trip.routePoints.forEach { pt ->
                    val pObj = JSONObject()
                    pObj.put("lat", pt.lat)
                    pObj.put("lng", pt.lng)
                    pObj.put("s", pt.speedKmh)
                    ptsArr.put(pObj)
                }
                obj.put("routePoints", ptsArr)
                arr.put(obj)
            }
            tripHistoryFile.writeText(arr.toString(), Charsets.UTF_8)
        }
    }

    fun loadLauncherSettings(): LauncherSettings {
        val enabled = prefs.getBoolean(KEY_LAUNCHER_DOCK_ENABLED, true)
        val mode = runCatching {
            DockDisplayMode.valueOf(
                prefs.getString(KEY_LAUNCHER_DOCK_MODE, DockDisplayMode.ALWAYS_VISIBLE.name)
                    ?: DockDisplayMode.ALWAYS_VISIBLE.name
            )
        }.getOrDefault(DockDisplayMode.ALWAYS_VISIBLE)

        val position = runCatching {
            DockPosition.valueOf(
                prefs.getString(KEY_LAUNCHER_DOCK_POSITION, DockPosition.BOTTOM.name)
                    ?: DockPosition.BOTTOM.name
            )
        }.getOrDefault(DockPosition.BOTTOM)

        val iconSize = runCatching {
            AppIconSize.valueOf(
                prefs.getString(KEY_LAUNCHER_APP_ICON_SIZE, AppIconSize.MEDIUM.name)
                    ?: AppIconSize.MEDIUM.name
            )
        }.getOrDefault(AppIconSize.MEDIUM)

        val drawerBg = runCatching {
            AppDrawerBackground.valueOf(
                prefs.getString(KEY_LAUNCHER_APP_DRAWER_BG, AppDrawerBackground.OBSIDIAN_PURE.name)
                    ?: AppDrawerBackground.OBSIDIAN_PURE.name
            )
        }.getOrDefault(AppDrawerBackground.OBSIDIAN_PURE)

        val initialized = prefs.getBoolean(KEY_LAUNCHER_DOCK_PINNED_INIT, false)
        val pinned = if (!initialized) {
            val defaults = InstalledAppsRepository.loadDefaultPinnedPackages(appContext)
            val initial = LauncherSettings(
                dockEnabled = enabled,
                dockDisplayMode = mode,
                dockPosition = position,
                pinnedPackages = defaults,
                appIconSize = iconSize,
                appDrawerBackground = drawerBg
            )
            saveLauncherSettings(initial)
            prefs.edit().putBoolean(KEY_LAUNCHER_DOCK_PINNED_INIT, true).apply()
            defaults
        } else {
            val raw = prefs.getString(KEY_LAUNCHER_DOCK_PINNED_JSON, "[]") ?: "[]"
            runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val pkg = arr.optString(i, "").trim()
                        if (pkg.isNotEmpty() && !contains(pkg)) add(pkg)
                    }
                }.take(LauncherSettings.MAX_PINNED_APPS)
            }.getOrDefault(emptyList())
        }

        return LauncherSettings(
            dockEnabled = enabled,
            dockDisplayMode = mode,
            dockPosition = position,
            pinnedPackages = pinned,
            appIconSize = iconSize,
            appDrawerBackground = drawerBg
        )
    }

    fun saveLauncherSettings(settings: LauncherSettings) {
        val arr = JSONArray()
        settings.pinnedPackages.take(LauncherSettings.MAX_PINNED_APPS).forEach { arr.put(it) }
        prefs.edit()
            .putBoolean(KEY_LAUNCHER_DOCK_ENABLED, settings.dockEnabled)
            .putString(KEY_LAUNCHER_DOCK_MODE, settings.dockDisplayMode.name)
            .putString(KEY_LAUNCHER_DOCK_POSITION, settings.dockPosition.name)
            .putString(KEY_LAUNCHER_DOCK_PINNED_JSON, arr.toString())
            .putString(KEY_LAUNCHER_APP_ICON_SIZE, settings.appIconSize.name)
            .putString(KEY_LAUNCHER_APP_DRAWER_BG, settings.appDrawerBackground.name)
            .putBoolean(KEY_LAUNCHER_DOCK_PINNED_INIT, true)
            .apply()
    }

    fun loadWidgetWorkspace(): WidgetWorkspaceState? {
        val raw = prefs.getString(KEY_WIDGET_WORKSPACE_JSON, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val pageCount = root.optInt("pageCount", 1).coerceIn(1, MAX_WIDGET_PAGES)
            val arr = root.optJSONArray("items") ?: JSONArray()
            val items = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "").trim()
                    if (id.isEmpty()) continue
                    val kind = runCatching {
                        WidgetItemKind.valueOf(obj.optString("kind", WidgetItemKind.BUILT_IN.name))
                    }.getOrDefault(WidgetItemKind.BUILT_IN)
                    val builtIn = obj.optString("builtInType", "").takeIf { it.isNotBlank() }?.let { name ->
                        runCatching { BuiltInWidgetType.valueOf(name) }.getOrNull()
                    }
                    if (kind == WidgetItemKind.BUILT_IN && builtIn == null) continue
                    val appWidgetId = obj.optInt("appWidgetId", -1)
                    if (kind == WidgetItemKind.APP_WIDGET && appWidgetId <= 0) continue

                    add(
                        PlacedWidgetItem(
                            id = id,
                            pageIndex = obj.optInt("pageIndex", 0).coerceIn(0, pageCount - 1),
                            kind = kind,
                            builtInType = builtIn,
                            appWidgetId = appWidgetId,
                            providerComponent = obj.optString("providerComponent", "").takeIf { it.isNotBlank() },
                            col = obj.optInt("col", 0).coerceAtLeast(0),
                            row = obj.optInt("row", 0).coerceAtLeast(0),
                            colSpan = obj.optInt("colSpan", 2).coerceAtLeast(1),
                            rowSpan = obj.optInt("rowSpan", 2).coerceAtLeast(1)
                        )
                    )
                }
            }
            WidgetWorkspaceState(pageCount = pageCount, items = items)
        }.getOrNull()
    }

    fun saveWidgetWorkspace(state: WidgetWorkspaceState) {
        val root = JSONObject().apply {
            put("pageCount", state.pageCount.coerceIn(1, MAX_WIDGET_PAGES))
            val arr = JSONArray()
            state.items.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("pageIndex", item.pageIndex)
                        put("kind", item.kind.name)
                        if (item.builtInType != null) put("builtInType", item.builtInType.name)
                        put("appWidgetId", item.appWidgetId)
                        if (item.providerComponent != null) put("providerComponent", item.providerComponent)
                        put("col", item.col)
                        put("row", item.row)
                        put("colSpan", item.colSpan)
                        put("rowSpan", item.rowSpan)
                    }
                )
            }
            put("items", arr)
        }
        prefs.edit().putString(KEY_WIDGET_WORKSPACE_JSON, root.toString()).apply()
    }
}
