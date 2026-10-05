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

/** The courier using this phone. A real build would get this from auth. */
object CurrentCourier {
    const val ID = "courier-07"
    const val NAME = "Courier #07"
}
