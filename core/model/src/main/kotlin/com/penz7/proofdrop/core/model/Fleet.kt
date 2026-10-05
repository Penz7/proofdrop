package com.penz7.proofdrop.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CourierPosition(
    val courierId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val status: CourierStatus,
    val updatedAt: Long,
)

@Serializable
enum class CourierStatus { IDLE, EN_ROUTE, DELIVERING, OFFLINE }

/** What a courier reports over the fleet WebSocket; the server fills in identity and time. */
@Serializable
data class PositionReport(
    val latitude: Double,
    val longitude: Double,
    val status: CourierStatus = CourierStatus.EN_ROUTE,
)
