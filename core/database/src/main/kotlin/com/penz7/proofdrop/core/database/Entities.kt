package com.penz7.proofdrop.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.penz7.proofdrop.core.model.Device
import com.penz7.proofdrop.core.model.DeviceType
import com.penz7.proofdrop.core.model.EvidenceRecord
import com.penz7.proofdrop.core.model.Order
import com.penz7.proofdrop.core.model.OrderStatus

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: String,
    val code: String,
    val customerName: String,
    val customerPhone: String?,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val items: String,
    val status: OrderStatus,
    val beaconId: String?,
    val courierId: String?,
    val courierName: String?,
    val assignedAt: Long,
    val deliveredAt: Long?,
    /** Local status change not yet acknowledged by the server. */
    val pendingSync: Boolean = false,
)

enum class UploadState { PENDING, UPLOADED, REJECTED }

@Entity(tableName = "evidence", indices = [Index(value = ["sequence"], unique = true)])
data class EvidenceEntity(
    @PrimaryKey val id: String,
    val sequence: Long,
    val orderId: String,
    val fileName: String,
    val fileSha256: String,
    val capturedAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val bleVerified: Boolean,
    val previousHash: String,
    val recordHash: String,
    val uploadState: UploadState = UploadState.PENDING,
    val uploadMessage: String? = null,
)

enum class PendingDeviceAction { CHECKOUT, RETURN }

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: DeviceType,
    val serial: String,
    val batteryPct: Int,
    val holderId: String?,
    val holderName: String?,
    val checkedOutAt: Long?,
    val pendingAction: PendingDeviceAction? = null,
)

fun OrderEntity.toModel() = Order(
    id, code, customerName, customerPhone, address, latitude, longitude, items, status,
    beaconId, courierId, courierName, assignedAt, deliveredAt,
)
fun Order.toEntity(pendingSync: Boolean = false) = OrderEntity(
    id, code, customerName, customerPhone, address, latitude, longitude, items, status,
    beaconId, courierId, courierName, assignedAt, deliveredAt, pendingSync,
)

fun EvidenceEntity.toModel() = EvidenceRecord(
    id, sequence, orderId, fileName, fileSha256, capturedAt, latitude, longitude, bleVerified, previousHash, recordHash,
)
fun EvidenceRecord.toEntity() = EvidenceEntity(
    id, sequence, orderId, fileName, fileSha256, capturedAt, latitude, longitude, bleVerified, previousHash, recordHash,
)

fun DeviceEntity.toModel() = Device(id, name, type, serial, batteryPct, holderId, holderName, checkedOutAt)
fun Device.toEntity(pendingAction: PendingDeviceAction? = null) =
    DeviceEntity(id, name, type, serial, batteryPct, holderId, holderName, checkedOutAt, pendingAction)
