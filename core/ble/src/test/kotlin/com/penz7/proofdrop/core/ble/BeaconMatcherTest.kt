package com.penz7.proofdrop.core.ble

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BeaconMatcherTest {
    private fun device(name: String?, rssi: Int, address: String = "AA:BB:CC:DD:EE:FF") =
        BleDevice(address, name, rssi, lastSeen = 0)

    @Test
    fun `matches beacon by name when close enough`() {
        assertTrue(BeaconMatcher.isNear("PD-BEACON-01", listOf(device("pd-beacon-01", -60))))
    }

    @Test
    fun `matches beacon by MAC address`() {
        assertTrue(BeaconMatcher.isNear("AA:BB:CC:DD:EE:FF", listOf(device(null, -70))))
    }

    @Test
    fun `ignores beacon that is too far away`() {
        assertFalse(BeaconMatcher.isNear("PD-BEACON-01", listOf(device("PD-BEACON-01", -95))))
    }

    @Test
    fun `orders without a beacon are never verified`() {
        assertFalse(BeaconMatcher.isNear(null, listOf(device("PD-BEACON-01", -40))))
    }
}
