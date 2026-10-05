package com.penz7.proofdrop.core.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Last known location from the platform LocationManager. Deliberately avoids Play Services
 * so the app also runs on devices without GMS (common for rugged handhelds).
 */
@Singleton
class LocationProvider @Inject constructor(@ApplicationContext private val context: Context) {

    @SuppressLint("MissingPermission") // checked below
    fun lastKnown(): GeoPoint? {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { GeoPoint(it.latitude, it.longitude) }
    }
}
