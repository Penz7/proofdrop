package com.penz7.proofdrop.core.data.repository

import com.penz7.proofdrop.core.data.LocationTracker
import com.penz7.proofdrop.core.data.sync.SyncScheduler
import com.penz7.proofdrop.core.database.EvidenceDao
import com.penz7.proofdrop.core.database.OrderDao
import com.penz7.proofdrop.core.database.toEntity
import com.penz7.proofdrop.core.database.toModel
import com.penz7.proofdrop.core.evidence.ChainAnchor
import com.penz7.proofdrop.core.evidence.ChainVerification
import com.penz7.proofdrop.core.evidence.EvidenceChain
import com.penz7.proofdrop.core.evidence.Sha256
import com.penz7.proofdrop.core.model.EvidenceDraft
import com.penz7.proofdrop.core.model.EvidenceRecord
import com.penz7.proofdrop.core.model.OrderStatus
import com.penz7.proofdrop.core.network.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class UploadStatus { PENDING, UPLOADED, REJECTED }

data class LedgerEntry(
    val record: EvidenceRecord,
    /** Human order code (e.g. PD-1001) when the order is still on this phone. */
    val orderCode: String?,
    val upload: UploadStatus,
    val message: String?,
)

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
    private val orderDao: OrderDao,
    private val storage: EvidenceStorage,
    private val orders: OrderRepository,
    private val location: LocationTracker,
    private val session: SessionStore,
    private val syncScheduler: SyncScheduler,
) : EvidenceRepository {

    // Sealing reads the last record then appends; must not interleave.
    private val sealMutex = Mutex()

    /** The server-side chain head at login: local records continue from it. */
    private fun loginAnchor() = session.chainHead.let { ChainAnchor(it.sequence, it.recordHash) }

    override fun observeLedger(): Flow<List<LedgerEntry>> =
        combine(dao.observeAll(), orderDao.observeAll()) { evidence, orders ->
            val codes = orders.associate { it.id to it.code }
            evidence.map {
                LedgerEntry(it.toModel(), codes[it.orderId], UploadStatus.valueOf(it.uploadState.name), it.uploadMessage)
            }
        }

    override fun newCaptureFile(): File = storage.newFile("${UUID.randomUUID()}.jpg")

    override suspend fun sealDelivery(orderId: String, photo: File, bleVerified: Boolean): EvidenceRecord {
        // Hash the photo and get a fresh GPS fix in parallel; both take a moment.
        val (fileHash, fix) = coroutineScope {
            val hash = async(Dispatchers.IO) { Sha256.of(photo.inputStream()) }
            val gps = async { location.freshFix() }
            hash.await() to gps.await()
        }
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
            val anchor = dao.last()?.let { ChainAnchor(it.sequence, it.recordHash) } ?: loginAnchor()
            EvidenceChain.seal(draft, anchor).also { dao.insert(it.toEntity()) }
        }
        orders.updateStatus(orderId, OrderStatus.DELIVERED)
        syncScheduler.requestSync()
        return record
    }

    override suspend fun verifyLedger(): ChainVerification = withContext(Dispatchers.IO) {
        EvidenceChain.verify(dao.all().map { it.toModel() }, loginAnchor()) { record ->
            storage.file(record.fileName).takeIf { it.exists() }?.let { Sha256.of(it.inputStream()) }
        }
    }

    override suspend fun tamperWithLatestForDemo(): Boolean {
        // In real mode, only edit a record the server already has, so the demo never blocks uploads.
        val last = (if (session.session.value?.demo == true) dao.last() else dao.lastUploaded()) ?: return false
        dao.overwriteLatitude(last.id, (last.latitude ?: 0.0) + 0.01)
        return true
    }
}
