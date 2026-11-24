package id.bangsadigital.riosense.model

import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Single GPS breadcrumb along a recorded vehicle trip.
 */
data class TripRoutePoint(
    val lat: Double,
    val lng: Double,
    val speedKmh: Int = 0
)

/**
 * Completed vehicle trip persisted locally with OBD-II telemetry & Strava-style GPS route trace.
 */
data class TripRecord(
    val id: String = UUID.randomUUID().toString(),
    val startTimeMs: Long,
    val endTimeMs: Long,
    val distanceKm: Float,
    val durationSec: Long,
    val idleSec: Long,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Int,
    val maxRpm: Int,
    val maxCoolantTempC: Int,
    val minBatteryV: Float,
    val estimatedFuelLiters: Float,
    val startLabel: String,
    val endLabel: String,
    val routePoints: List<TripRoutePoint> = emptyList()
) {
    val fuelEconomyKmPerL: Float
        get() = if (estimatedFuelLiters > 0.01f && distanceKm > 0.05f) {
            (distanceKm / estimatedFuelLiters).coerceIn(3.0f, 35.0f)
        } else 0f

    val routeTitle: String
        get() = if (startLabel.isNotBlank() && endLabel.isNotBlank() && startLabel != endLabel) {
            "$startLabel → $endLabel"
        } else if (startLabel.isNotBlank()) {
            startLabel
        } else {
            "Kia Rio Trip"
        }
}

/**
 * Live in-progress trip accumulator updated during driving / engine running.
 */
data class ActiveTripState(
    val startTimeMs: Long,
    val distanceKm: Float = 0f,
    val durationSec: Long = 0L,
    val idleSec: Long = 0L,
    val maxSpeedKmh: Int = 0,
    val maxRpm: Int = 0,
    val maxCoolantTempC: Int = 0,
    val minBatteryV: Float = 15.0f,
    val estimatedFuelLiters: Float = 0f,
    val routePoints: List<TripRoutePoint> = emptyList()
)

object TripSampleSeeder {
    /**
     * Seeds realistic initial showcase trips around Yogyakarta so the Strava-style
     * route canvas and trip list can be previewed immediately before live driving.
     */
    fun defaultShowcaseTrips(nowMs: Long = System.currentTimeMillis()): List<TripRecord> {
        val dayMs = TimeUnit.DAYS.toMillis(1)
        val hourMs = TimeUnit.HOURS.toMillis(1)

        // Trip 1: Sleman -> Malioboro (city commute with a realistic Tugu traffic-light stop)
        val trip1Route = listOf(
            TripRoutePoint(-7.7421, 110.3695, 22),
            TripRoutePoint(-7.7468, 110.3688, 38),
            TripRoutePoint(-7.7524, 110.3679, 44),
            TripRoutePoint(-7.7579, 110.3672, 48),
            TripRoutePoint(-7.7638, 110.3669, 32),
            TripRoutePoint(-7.7695, 110.3668, 14),
            TripRoutePoint(-7.7752, 110.3666, 0),
            TripRoutePoint(-7.7810, 110.3664, 41),
            TripRoutePoint(-7.7865, 110.3659, 25),
            TripRoutePoint(-7.7926, 110.3658, 18)
        )

        // Trip 2: Ringroad Utara -> Bandara YIA (fast highway run)
        val trip2Route = listOf(
            TripRoutePoint(-7.7565, 110.3880, 42),
            TripRoutePoint(-7.7610, 110.3710, 62),
            TripRoutePoint(-7.7720, 110.3450, 74),
            TripRoutePoint(-7.7890, 110.3120, 82),
            TripRoutePoint(-7.8080, 110.2780, 88),
            TripRoutePoint(-7.8260, 110.2360, 86),
            TripRoutePoint(-7.8480, 110.1940, 92),
            TripRoutePoint(-7.8710, 110.1520, 84),
            TripRoutePoint(-7.8930, 110.1080, 72),
            TripRoutePoint(-7.9056, 110.0568, 34)
        )

        // Trip 3: Kaliurang Scenic Drive (winding mountain road)
        val trip3Route = listOf(
            TripRoutePoint(-7.7510, 110.3990, 36),
            TripRoutePoint(-7.7340, 110.4050, 48),
            TripRoutePoint(-7.7160, 110.4110, 52),
            TripRoutePoint(-7.6980, 110.4190, 46),
            TripRoutePoint(-7.6810, 110.4220, 40),
            TripRoutePoint(-7.6640, 110.4280, 35),
            TripRoutePoint(-7.6480, 110.4250, 38),
            TripRoutePoint(-7.6310, 110.4290, 32),
            TripRoutePoint(-7.6140, 110.4240, 28),
            TripRoutePoint(-7.5985, 110.4265, 22)
        )

        return listOf(
            TripRecord(
                id = "demo_trip_1",
                startTimeMs = nowMs - 3 * hourMs,
                endTimeMs = nowMs - 3 * hourMs + 28 * 60_000L,
                distanceKm = 11.4f,
                durationSec = 28 * 60L,
                idleSec = 6 * 60L,
                avgSpeedKmh = 24.4f,
                maxSpeedKmh = 54,
                maxRpm = 3150,
                maxCoolantTempC = 91,
                minBatteryV = 13.8f,
                estimatedFuelLiters = 0.94f,
                startLabel = "Sleman",
                endLabel = "Malioboro",
                routePoints = trip1Route
            ),
            TripRecord(
                id = "demo_trip_2",
                startTimeMs = nowMs - dayMs - 2 * hourMs,
                endTimeMs = nowMs - dayMs - 2 * hourMs + 56 * 60_000L,
                distanceKm = 46.8f,
                durationSec = 56 * 60L,
                idleSec = 5 * 60L,
                avgSpeedKmh = 50.1f,
                maxSpeedKmh = 94,
                maxRpm = 3420,
                maxCoolantTempC = 92,
                minBatteryV = 14.0f,
                estimatedFuelLiters = 3.05f,
                startLabel = "Ringroad Utara",
                endLabel = "Bandara YIA",
                routePoints = trip2Route
            ),
            TripRecord(
                id = "demo_trip_3",
                startTimeMs = nowMs - 3 * dayMs,
                endTimeMs = nowMs - 3 * dayMs + 42 * 60_000L,
                distanceKm = 24.6f,
                durationSec = 42 * 60L,
                idleSec = 4 * 60L,
                avgSpeedKmh = 35.1f,
                maxSpeedKmh = 64,
                maxRpm = 3680,
                maxCoolantTempC = 94,
                minBatteryV = 13.9f,
                estimatedFuelLiters = 1.98f,
                startLabel = "Condongcatur",
                endLabel = "Kaliurang",
                routePoints = trip3Route
            )
        )
    }
}
