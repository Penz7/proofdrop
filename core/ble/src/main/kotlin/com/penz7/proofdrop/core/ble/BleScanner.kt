package com.penz7.proofdrop.core.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val lastSeen: Long,
)

sealed interface BleScanState {
    data object Unavailable : BleScanState
    data object MissingPermission : BleScanState
    data class Scanning(val devices: List<BleDevice>) : BleScanState
    data class Failed(val errorCode: Int) : BleScanState
}

interface BleScanner {
    /** Scans while collected; emits the de-duplicated set of nearby devices, strongest first. */
    fun scan(): Flow<BleScanState>
}

@Singleton
class AndroidBleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) : BleScanner {

    private fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission") // checked by hasPermission()
    override fun scan(): Flow<BleScanState> = callbackFlow {
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?.takeIf { it.isEnabled }
            ?.bluetoothLeScanner
        when {
            scanner == null -> {
                trySend(BleScanState.Unavailable)
                awaitClose()
                return@callbackFlow
            }
            !hasPermission() -> {
                trySend(BleScanState.MissingPermission)
                awaitClose()
                return@callbackFlow
            }
        }

        val seen = LinkedHashMap<String, BleDevice>()
        fun publish() {
            val now = System.currentTimeMillis()
            seen.values.removeAll { now - it.lastSeen > STALE_AFTER_MS }
            trySend(BleScanState.Scanning(seen.values.sortedByDescending { it.rssi }))
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull()
                synchronized(seen) {
                    seen[result.device.address] = BleDevice(
                        result.device.address, name, result.rssi, System.currentTimeMillis(),
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                trySend(BleScanState.Failed(errorCode))
            }
        }

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner!!.startScan(null, settings, callback)
        trySend(BleScanState.Scanning(emptyList()))
        // Throttle UI updates to once per second instead of once per advertisement.
        val ticker = launch {
            while (true) {
                delay(1_000)
                synchronized(seen) { publish() }
            }
        }
        awaitClose {
            ticker.cancel()
            runCatching { scanner.stopScan(callback) }
        }
    }

    private companion object {
        const val STALE_AFTER_MS = 10_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BleModule {
    @Binds
    abstract fun scanner(impl: AndroidBleScanner): BleScanner
}
