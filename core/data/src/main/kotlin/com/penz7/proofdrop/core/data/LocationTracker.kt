package com.penz7.proofdrop.core.data

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
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Device location from the platform LocationManager. Deliberately avoids Play Services
 * so the app also runs on devices without GMS (common for rugged handhelds).
 * Continuous updates run only while a shift is active.
 */
@Singleton
class LocationTracker @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = context.getSystemService(LocationManager::class.java)
    private val _location = MutableStateFlow<GeoPoint?>(null)
    val location: StateFlow<GeoPoint?> = _location
    private var liveFixAt = 0L // elapsedRealtimeNanos of the last live fix

    private val listener = LocationListener { update(it) }

    fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /**
     * A *recent* fix only: the live one during a shift, otherwise the platform's last known.
     * An old fix is worse than none, because it would be sealed into evidence as the drop-off point.
     */
    fun current(): GeoPoint? {
        val live = _location.value
        if (live != null && SystemClock.elapsedRealtimeNanos() - liveFixAt < MAX_AGE_NANOS) return live
        return lastKnown()
    }

    /**
     * Asks the platform for a *fresh* fix (used at capture time, when no shift is running and the
     * last known location may be missing or stale). Falls back to [current] after [timeoutMs].
     */
    @SuppressLint("MissingPermission") // checked by hasPermission()
    suspend fun freshFix(timeoutMs: Long = 6_000): GeoPoint? {
        if (!hasPermission() || manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return current()
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { manager.isProviderEnabled(it) } ?: return current()
        val fix = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                manager.getCurrentLocation(provider, cancel, context.mainExecutor) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            }
        }
        return fix?.let { update(it); it.toGeoPoint() } ?: current()
    }

    @SuppressLint("MissingPermission") // checked by hasPermission()
    fun start(): Boolean {
        if (!hasPermission() || manager == null) return false
        lastKnown()?.let {
            _location.value = it
            liveFixAt = SystemClock.elapsedRealtimeNanos()
        }
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (manager.isProviderEnabled(provider)) {
                manager.requestLocationUpdates(provider, UPDATE_INTERVAL_MS, MIN_DISTANCE_M, listener, Looper.getMainLooper())
            }
        }
        return true
    }

    fun stop() {
        manager?.removeUpdates(listener)
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): GeoPoint? {
        if (!hasPermission() || manager == null) return null
        return manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < MAX_AGE_NANOS }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.toGeoPoint()
    }

    private fun update(location: Location) {
        _location.value = location.toGeoPoint()
        liveFixAt = location.elapsedRealtimeNanos
    }

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)

    private companion object {
        const val UPDATE_INTERVAL_MS = 5_000L
        const val MIN_DISTANCE_M = 5f
        val MAX_AGE_NANOS = TimeUnit.MINUTES.toNanos(2)
    }
}
