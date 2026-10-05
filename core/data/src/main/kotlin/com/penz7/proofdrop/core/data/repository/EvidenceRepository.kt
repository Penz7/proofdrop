package com.penz7.proofdrop.core.data.repository

import com.penz7.proofdrop.core.data.LocationProvider
import com.penz7.proofdrop.core.data.sync.SyncScheduler
import com.penz7.proofdrop.core.database.EvidenceDao
import com.penz7.proofdrop.core.database.toEntity
import com.penz7.proofdrop.core.database.toModel
import com.penz7.proofdrop.core.evidence.ChainVerification
import com.penz7.proofdrop.core.evidence.EvidenceChain
import com.penz7.proofdrop.core.evidence.Sha256
import com.penz7.proofdrop.core.model.EvidenceDraft
import com.penz7.proofdrop.core.model.EvidenceRecord
import com.penz7.proofdrop.core.model.OrderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class UploadStatus { PENDING, UPLOADED, REJECTED }

data class LedgerEntry(val record: EvidenceRecord, val upload: UploadStatus, val message: String?)

interface EvidenceRepository {
    fun observeLedger(): Flow<List<LedgerEntry>>

    /** A fresh file in private storage for the camera to write into. */
    fun newCaptureFile(): File

    /** Hashes the photo, seals it into the chain, marks the order delivered and queues upload. */
    suspend fun sealDelivery(orderId: String, photo: File, bleVerified: Boolean): EvidenceRecord

    /** Re-checks every link and re-hashes every stored photo. */
    suspend fun verifyLedger(): ChainVerification

    /** Debug-only: silently edits the latest record so the ledger check can be demoed. */
    suspend fun tamperWithLatestForDemo(): Boolean
}

@Singleton
class ChainedEvidenceRepository @Inject constructor(
    private val dao: EvidenceDao,
    private val storage: EvidenceStorage,
    private val orders: OrderRepository,
    private val location: LocationProvider,
    private val syncScheduler: SyncScheduler,
) : EvidenceRepository {

    // Sealing reads the last record then appends; must not interleave.
    private val sealMutex = Mutex()

    override fun observeLedger(): Flow<List<LedgerEntry>> = dao.observeAll().map { list ->
        list.map { LedgerEntry(it.toModel(), UploadStatus.valueOf(it.uploadState.name), it.uploadMessage) }
    }

    override fun newCaptureFile(): File = storage.newFile("${UUID.randomUUID()}.jpg")

    override suspend fun sealDelivery(orderId: String, photo: File, bleVerified: Boolean): EvidenceRecord {
        val fileHash = withContext(Dispatchers.IO) { Sha256.of(photo.inputStream()) }
        val fix = location.lastKnown()
        val record = sealMutex.withLock {
            val draft = EvidenceDraft(
                id = photo.nameWithoutExtension,
                orderId = orderId,
                fileName = photo.name,
                fileSha256 = fileHash,
                capturedAt = System.currentTimeMillis(),
                latitude = fix?.latitude,
                longitude = fix?.longitude,
                bleVerified = bleVerified,
            )
            EvidenceChain.seal(draft, dao.last()?.toModel()).also { dao.insert(it.toEntity()) }
        }
        orders.updateStatus(orderId, OrderStatus.DELIVERED)
        syncScheduler.requestSync()
        return record
    }

    override suspend fun verifyLedger(): ChainVerification = withContext(Dispatchers.IO) {
        EvidenceChain.verify(dao.all().map { it.toModel() }) { record ->
            storage.file(record.fileName).takeIf { it.exists() }?.let { Sha256.of(it.inputStream()) }
        }
    }

    override suspend fun tamperWithLatestForDemo(): Boolean {
        val last = dao.last() ?: return false
        dao.overwriteLatitude(last.id, (last.latitude ?: 0.0) + 0.01)
        return true
    }
}
