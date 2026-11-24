package id.bangsadigital.riosense.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import id.bangsadigital.riosense.model.TripRoutePoint
import kotlin.math.roundToInt

/**
 * 100% Offline GPS breadcrumb tracker backed by Android's native [LocationManager].
 * Zero Google Play Services dependency; active only while a trip is running (0% CPU when parked).
 */
class TripGpsTracker(
    private val context: Context,
    private val onPointCaptured: (TripRoutePoint) -> Unit
) {
    private val locationManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var isTracking = false
    private var lastLocation: Location? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.accuracy > 65f) return
            val prev = lastLocation
            if (prev != null && prev.distanceTo(location) < 18f) return

            lastLocation = location
            val speedKmh = if (location.hasSpeed()) {
                (location.speed * 3.6f).roundToInt().coerceIn(0, 220)
            } else 0
            onPointCaptured(
                TripRoutePoint(
                    lat = location.latitude,
                    lng = location.longitude,
                    speedKmh = speedKmh
                )
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (isTracking || !hasLocationPermission()) return
        val lm = locationManager ?: return
        isTracking = true
        lastLocation = null

        runCatching {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000L,
                    20f,
                    listener,
                    Looper.getMainLooper()
                )
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    6000L,
                    35f,
                    listener,
                    Looper.getMainLooper()
                )
            }
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        runCatching { locationManager?.removeUpdates(listener) }
        lastLocation = null
    }
}
