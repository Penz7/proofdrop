package com.penz7.proofdrop.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Order(
    val id: String,
    /** Human-readable code shown in the UI, e.g. "PD-1001". */
    val code: String,
    val customerName: String,
    val customerPhone: String? = null,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val items: String,
    val status: OrderStatus,
    /** Name of the BLE beacon installed at the drop-off point, if any. */
    val beaconId: String? = null,
    val courierId: String? = null,
    val courierName: String? = null,
    val assignedAt: Long,
    val deliveredAt: Long? = null,
)

/** CREATED (not assigned yet) and CANCELLED orders are never sent to couriers, but are decodable. */
@Serializable
enum class OrderStatus { CREATED, ASSIGNED, PICKED_UP, DELIVERED, FAILED, CANCELLED }

@Serializable
data class OrderStatusUpdate(val status: OrderStatus, val at: Long)
