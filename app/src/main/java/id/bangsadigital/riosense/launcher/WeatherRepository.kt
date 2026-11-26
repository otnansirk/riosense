package id.bangsadigital.riosense.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

enum class WeatherCondition {
    SUNNY,
    PARTLY_CLOUDY,
    CLOUDY,
    RAIN,
    STORM,
    FOG
}

data class DailyForecast(
    val dayLabel: String,
    val condition: WeatherCondition,
    val highC: Int,
    val lowC: Int
)

data class WeatherSnapshot(
    val tempC: Int,
    val highC: Int,
    val lowC: Int,
    val condition: WeatherCondition,
    val conditionLabelEn: String,
    val conditionLabelId: String,
    val locationName: String,
    val daily: List<DailyForecast>,
    val updatedAtMs: Long,
    val isLiveFetched: Boolean = true
)

/**
 * Zero-API-key lightweight weather cache backed by Open-Meteo (~1.5 KB JSON, 30-min refresh).
 * Works completely offline by immediately serving the last cached forecast or clean local defaults.
 */
object WeatherRepository {

    private const val PREFS_NAME = "riosense_weather_cache"
    private const val KEY_WEATHER_JSON = "cached_weather_v1"
    private const val STALE_MS = 30L * 60_000L

    private val _weather = MutableStateFlow<WeatherSnapshot?>(null)
    val weather: StateFlow<WeatherSnapshot?> = _weather.asStateFlow()

    fun loadCached(context: Context): WeatherSnapshot {
        _weather.value?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_WEATHER_JSON, null)
        val parsed = raw?.let { parseCacheJson(it) } ?: defaultOfflinePlaceholder()
        _weather.value = parsed
        return parsed
    }

    suspend fun refreshIfStale(context: Context, force: Boolean = false) {
        val appCtx = context.applicationContext
        val current = loadCached(appCtx)
        val now = System.currentTimeMillis()
        if (!force && current.isLiveFetched && (now - current.updatedAtMs) < STALE_MS) {
            return
        }

        withContext(Dispatchers.IO) {
            runCatching {
                val (lat, lon) = resolveBestCoordinates(appCtx)
                val endpoint =
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                        "&timezone=auto&forecast_days=6"

                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3500
                    readTimeout = 3500
                    requestMethod = "GET"
                }

                val body = try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }

                val root = JSONObject(body)
                val currentObj = root.getJSONObject("current")
                val tempNow = currentObj.getDouble("temperature_2m").roundToInt()
                val currentCode = currentObj.getInt("weather_code")
                val (condNow, labelEn, labelId) = mapWmoCode(currentCode)

                val dailyObj = root.getJSONObject("daily")
                val datesArr = dailyObj.getJSONArray("time")
                val codesArr = dailyObj.getJSONArray("weather_code")
                val highsArr = dailyObj.getJSONArray("temperature_2m_max")
                val lowsArr = dailyObj.getJSONArray("temperature_2m_min")

                val todayHigh = if (highsArr.length() > 0) highsArr.getDouble(0).roundToInt() else tempNow + 2
                val todayLow = if (lowsArr.length() > 0) lowsArr.getDouble(0).roundToInt() else tempNow - 4

                val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dayFmt = SimpleDateFormat("EEE", Locale.US)
                val forecastList = buildList {
                    val maxIdx = minOf(datesArr.length(), 6)
                    for (i in 1 until maxIdx) {
                        val dateObj = runCatching { inFmt.parse(datesArr.getString(i)) }.getOrNull()
                        val dayLabel = dateObj?.let { dayFmt.format(it).uppercase() } ?: "D+$i"
                        val (c, _, _) = mapWmoCode(codesArr.getInt(i))
                        add(
                            DailyForecast(
                                dayLabel = dayLabel,
                                condition = c,
                                highC = highsArr.getDouble(i).roundToInt(),
                                lowC = lowsArr.getDouble(i).roundToInt()
                            )
                        )
                    }
                }

                val city = resolveCityName(appCtx, lat, lon)
                val snapshot = WeatherSnapshot(
                    tempC = tempNow,
                    highC = todayHigh,
                    lowC = todayLow,
                    condition = condNow,
                    conditionLabelEn = labelEn,
                    conditionLabelId = labelId,
                    locationName = city,
                    daily = forecastList,
                    updatedAtMs = System.currentTimeMillis(),
                    isLiveFetched = true
                )
                saveCacheJson(appCtx, snapshot)
                _weather.value = snapshot
            }
        }
    }

    private fun resolveBestCoordinates(context: Context): Pair<Double, Double> {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return -6.2088 to 106.8456

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return -6.2088 to 106.8456

        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        var best: Location? = null
        for (p in providers) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (best == null || loc.time > best.time) {
                best = loc
            }
        }
        return if (best != null) best.latitude to best.longitude else (-6.2088 to 106.8456)
    }

    @Suppress("DEPRECATION")
    private fun resolveCityName(context: Context, lat: Double, lon: Double): String {
        val addr = runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
        }.getOrNull()
        return addr?.locality?.takeIf { it.isNotBlank() }
            ?: addr?.subAdminArea?.takeIf { it.isNotBlank() }
            ?: addr?.adminArea?.takeIf { it.isNotBlank() }
            ?: "Jakarta"
    }

    private fun mapWmoCode(code: Int): Triple<WeatherCondition, String, String> = when (code) {
        0 -> Triple(WeatherCondition.SUNNY, "Clear Sky", "Cerah")
        1, 2 -> Triple(WeatherCondition.PARTLY_CLOUDY, "Partly Cloudy", "Cerah Berawan")
        3 -> Triple(WeatherCondition.CLOUDY, "Overcast", "Berawan")
        45, 48 -> Triple(WeatherCondition.FOG, "Foggy", "Berkabut")
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 ->
            Triple(WeatherCondition.RAIN, "Rain Showers", "Hujan")
        95, 96, 99 -> Triple(WeatherCondition.STORM, "Thunderstorm", "Badai Petir")
        else -> Triple(WeatherCondition.CLOUDY, "Cloudy", "Berawan")
    }

    private fun saveCacheJson(context: Context, snapshot: WeatherSnapshot) {
        val obj = JSONObject().apply {
            put("tempC", snapshot.tempC)
            put("highC", snapshot.highC)
            put("lowC", snapshot.lowC)
            put("condition", snapshot.condition.name)
            put("labelEn", snapshot.conditionLabelEn)
            put("labelId", snapshot.conditionLabelId)
            put("locationName", snapshot.locationName)
            put("updatedAtMs", snapshot.updatedAtMs)
            val arr = JSONArray()
            snapshot.daily.forEach { d ->
                arr.put(
                    JSONObject().apply {
                        put("day", d.dayLabel)
                        put("cond", d.condition.name)
                        put("hi", d.highC)
                        put("lo", d.lowC)
                    }
                )
            }
            put("daily", arr)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WEATHER_JSON, obj.toString())
            .apply()
    }

    private fun parseCacheJson(raw: String): WeatherSnapshot? = runCatching {
        val obj = JSONObject(raw)
        val arr = obj.getJSONArray("daily")
        val daily = buildList {
            for (i in 0 until arr.length()) {
                val d = arr.getJSONObject(i)
                add(
                    DailyForecast(
                        dayLabel = d.getString("day"),
                        condition = WeatherCondition.valueOf(d.getString("cond")),
                        highC = d.getInt("hi"),
                        lowC = d.getInt("lo")
                    )
                )
            }
        }
        WeatherSnapshot(
            tempC = obj.getInt("tempC"),
            highC = obj.getInt("highC"),
            lowC = obj.getInt("lowC"),
            condition = WeatherCondition.valueOf(obj.getString("condition")),
            conditionLabelEn = obj.getString("labelEn"),
            conditionLabelId = obj.getString("labelId"),
            locationName = obj.getString("locationName"),
            daily = daily,
            updatedAtMs = obj.getLong("updatedAtMs"),
            isLiveFetched = true
        )
    }.getOrNull()

    private fun defaultOfflinePlaceholder(): WeatherSnapshot = WeatherSnapshot(
        tempC = 29,
        highC = 32,
        lowC = 25,
        condition = WeatherCondition.PARTLY_CLOUDY,
        conditionLabelEn = "Partly Cloudy",
        conditionLabelId = "Cerah Berawan",
        locationName = "Jakarta",
        daily = listOf(
            DailyForecast("THU", WeatherCondition.PARTLY_CLOUDY, 32, 25),
            DailyForecast("FRI", WeatherCondition.RAIN, 30, 24),
            DailyForecast("SAT", WeatherCondition.SUNNY, 33, 25),
            DailyForecast("SUN", WeatherCondition.CLOUDY, 31, 25),
            DailyForecast("MON", WeatherCondition.PARTLY_CLOUDY, 32, 25)
        ),
        updatedAtMs = 0L,
        isLiveFetched = false
    )
}
