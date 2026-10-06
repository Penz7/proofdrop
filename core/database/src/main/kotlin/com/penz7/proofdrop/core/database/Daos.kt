package com.penz7.proofdrop.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.penz7.proofdrop.core.model.OrderStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY CASE status WHEN 'DELIVERED' THEN 1 WHEN 'FAILED' THEN 1 WHEN 'CANCELLED' THEN 1 ELSE 0 END, assignedAt DESC")
    fun observeAll(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :id")
    fun observe(id: String): Flow<OrderEntity?>

    @Query("SELECT COUNT(*) FROM orders")
    suspend fun count(): Int

    @Query("SELECT * FROM orders WHERE pendingSync = 1")
    suspend fun pending(): List<OrderEntity>

    @Query("UPDATE orders SET status = :status, pendingSync = 1 WHERE id = :id")
    suspend fun updateStatusLocally(id: String, status: OrderStatus)

    @Query("UPDATE orders SET pendingSync = 0 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Upsert
    suspend fun upsert(orders: List<OrderEntity>)

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM orders WHERE id NOT IN (:keep) AND pendingSync = 0")
    suspend fun deleteAllExcept(keep: List<String>)

    /**
     * The server's list is the truth (orders can be reassigned away from us),
     * except for rows with unsynced local changes.
     */
    @Transaction
    suspend fun mergeFromServer(remote: List<OrderEntity>) {
        val pendingIds = pending().mapTo(HashSet()) { it.id }
        upsert(remote.filterNot { it.id in pendingIds })
        deleteAllExcept(remote.map { it.id })
    }
}

@Dao
interface EvidenceDao {
    @Query("SELECT * FROM evidence ORDER BY sequence DESC")
    fun observeAll(): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM evidence ORDER BY sequence ASC")
    suspend fun all(): List<EvidenceEntity>

    @Query("SELECT * FROM evidence ORDER BY sequence DESC LIMIT 1")
    suspend fun last(): EvidenceEntity?

    @Query("SELECT * FROM evidence WHERE uploadState = 'PENDING' ORDER BY sequence ASC")
    suspend fun pendingUpload(): List<EvidenceEntity>

    /** The earliest rejected record, if any: the server can never accept anything after it. */
    @Query("SELECT * FROM evidence WHERE uploadState = 'REJECTED' ORDER BY sequence ASC LIMIT 1")
    suspend fun firstRejected(): EvidenceEntity?

    /** Latest record already accepted by the server (safe target for the tamper demo). */
    @Query("SELECT * FROM evidence WHERE uploadState = 'UPLOADED' ORDER BY sequence DESC LIMIT 1")
    suspend fun lastUploaded(): EvidenceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: EvidenceEntity)

    @Query("UPDATE evidence SET uploadState = :state, uploadMessage = :message WHERE id = :id")
    suspend fun setUploadState(id: String, state: UploadState, message: String?)

    @Query("UPDATE evidence SET latitude = :latitude WHERE id = :id")
    suspend fun overwriteLatitude(id: String, latitude: Double)
}

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY id")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun get(id: String): DeviceEntity?

    @Query("SELECT COUNT(*) FROM devices")
    suspend fun count(): Int

    @Query("SELECT * FROM devices WHERE pendingAction IS NOT NULL")
    suspend fun pending(): List<DeviceEntity>

    @Upsert
    suspend fun upsert(devices: List<DeviceEntity>)

    @Transaction
    suspend fun mergeFromServer(remote: List<DeviceEntity>) {
        val pendingIds = pending().mapTo(HashSet()) { it.id }
        upsert(remote.filterNot { it.id in pendingIds })
    }
}
