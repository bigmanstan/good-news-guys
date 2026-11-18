package com.goodnewsguys.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** Great-circle distance in kilometres. */
fun distanceKm(a: LatLon, lat: Double, lon: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat - a.lat)
    val dLon = Math.toRadians(lon - a.lon)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(lat)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}

/** The covered city closest to a point, if one is within reach. */
fun nearestCity(here: LatLon, maxKm: Double = 200.0): City? =
    CITIES.minByOrNull { distanceKm(here, it.lat, it.lon) }?.takeIf { distanceKm(here, it.lat, it.lon) <= maxKm }

/** The place a story mentions that is closest to the reader, with the distance. */
fun Story.nearest(here: LatLon): Pair<Place, Double>? =
    places.map { it to distanceKm(here, it.lat, it.lon) }.minByOrNull { it.second }

fun formatKm(km: Double): String = when {
    km < 1 -> "${(km * 1000 / 100).roundToInt() * 100} m"
    km < 10 -> "%.1f km".format(km)
    else -> "${km.roundToInt()} km"
}

fun hasLocationPermission(context: Context) =
    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * One approximate fix from the phone, or null. Uses Android's own location service,
 * so no Google Play services are needed. The result never leaves the phone.
 */
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): LatLon? {
    if (!hasLocationPermission(context)) return null
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = buildList {
        if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

    fun lastKnown(maxAgeMs: Long): Location? = providers
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .filter { System.currentTimeMillis() - it.time <= maxAgeMs }
        .maxByOrNull { it.time }

    // A fix from the last half hour is plenty for "which neighbourhood am I in".
    lastKnown(30 * 60_000L)?.let { return LatLon(it.latitude, it.longitude) }

    for (provider in providers) {
        val fix = withTimeoutOrNull(12_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                runCatching {
                    if (Build.VERSION.SDK_INT >= 30) {
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        lm.getCurrentLocation(provider, signal, context.mainExecutor) { if (cont.isActive) cont.resume(it) }
                    } else {
                        val listener = object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                if (cont.isActive) cont.resume(location)
                            }
                            @Deprecated("Required on old Android versions")
                            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                            override fun onProviderEnabled(provider: String) {}
                            override fun onProviderDisabled(provider: String) {}
                        }
                        cont.invokeOnCancellation { lm.removeUpdates(listener) }
                        @Suppress("DEPRECATION")
                        lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
        if (fix != null) return LatLon(fix.latitude, fix.longitude)
    }
    return lastKnown(Long.MAX_VALUE)?.let { LatLon(it.latitude, it.longitude) }
}
