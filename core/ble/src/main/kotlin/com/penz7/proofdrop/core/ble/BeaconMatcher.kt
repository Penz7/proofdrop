package com.penz7.proofdrop.core.ble

/** Decides whether the drop-off beacon for an order is physically in range. */
object BeaconMatcher {
    /** Roughly "within a few metres" for a typical ESP32 / iBeacon at default TX power. */
    const val NEAR_RSSI = -80

    fun isNear(beaconId: String?, devices: List<BleDevice>, minRssi: Int = NEAR_RSSI): Boolean {
        if (beaconId.isNullOrBlank()) return false
        return devices.any { device ->
            device.rssi >= minRssi &&
                (device.name.equals(beaconId, ignoreCase = true) || device.address.equals(beaconId, ignoreCase = true))
        }
    }
}
