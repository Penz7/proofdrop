package com.penz7.proofdrop.core.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

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

    private val listener = LocationListener { update(it) }

    fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Best available fix: the live one during a shift, otherwise the platform's last known. */
    fun current(): GeoPoint? = _location.value ?: lastKnown()

    @SuppressLint("MissingPermission") // checked by hasPermission()
    fun start(): Boolean {
        if (!hasPermission() || manager == null) return false
        lastKnown()?.let { _location.value = it }
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
            .maxByOrNull { it.time }
            ?.toGeoPoint()
    }

    private fun update(location: Location) {
        _location.value = location.toGeoPoint()
    }

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)

    private companion object {
        const val UPDATE_INTERVAL_MS = 5_000L
        const val MIN_DISTANCE_M = 5f
    }
}
