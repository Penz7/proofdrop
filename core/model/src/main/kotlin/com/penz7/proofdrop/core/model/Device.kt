package com.penz7.proofdrop.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: String,
    val name: String,
    val type: DeviceType,
    val serial: String,
    val batteryPct: Int,
    val holderId: String? = null,
    val holderName: String? = null,
    val checkedOutAt: Long? = null,
) {
    val isAvailable: Boolean get() = holderId == null
}

@Serializable
enum class DeviceType { SCANNER, BODY_CAM, PRINTER, VEHICLE }

@Serializable
data class CheckoutRequest(val courierId: String, val courierName: String, val at: Long)
