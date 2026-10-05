package com.penz7.proofdrop.core.data.shift

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface ShiftManager {
    val onShift: StateFlow<Boolean>

    /** Callers must hold a location permission; Android 14+ refuses location services without one. */
    fun start()
    fun stop()
}

/** Starts and stops the courier's shift (the [ShiftService] foreground service). */
@Singleton
class ShiftController @Inject constructor(@ApplicationContext private val context: Context) : ShiftManager {

    private val _onShift = MutableStateFlow(false)
    override val onShift: StateFlow<Boolean> = _onShift

    override fun start() {
        ContextCompat.startForegroundService(context, Intent(context, ShiftService::class.java))
    }

    override fun stop() {
        context.stopService(Intent(context, ShiftService::class.java))
    }

    internal fun setRunning(running: Boolean) {
        _onShift.value = running
    }
}
