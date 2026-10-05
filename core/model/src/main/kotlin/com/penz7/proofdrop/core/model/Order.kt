package com.penz7.proofdrop.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Order(
    val id: String,
    val customerName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val items: String,
    val status: OrderStatus,
    /** Name of the BLE beacon installed at the drop-off point, if any. */
    val beaconId: String? = null,
    val assignedAt: Long,
)

@Serializable
enum class OrderStatus { ASSIGNED, PICKED_UP, DELIVERED, FAILED }

@Serializable
data class OrderStatusUpdate(val status: OrderStatus, val at: Long)
