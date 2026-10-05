package com.penz7.proofdrop.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.penz7.proofdrop.core.data.repository.EvidenceStorage
import com.penz7.proofdrop.core.database.DeviceDao
import com.penz7.proofdrop.core.database.EvidenceDao
import com.penz7.proofdrop.core.database.OrderDao
import com.penz7.proofdrop.core.database.PendingDeviceAction
import com.penz7.proofdrop.core.database.UploadState
import com.penz7.proofdrop.core.database.toEntity
import com.penz7.proofdrop.core.database.toModel
import com.penz7.proofdrop.core.model.CheckoutRequest
import com.penz7.proofdrop.core.model.CurrentCourier
import com.penz7.proofdrop.core.model.EvidenceUploadResult
import com.penz7.proofdrop.core.model.OrderStatusUpdate
import com.penz7.proofdrop.core.network.ProofDropApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException

/** Pushes every queued local change to the server: order statuses, device checkouts, evidence. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val orderDao: OrderDao,
    private val deviceDao: DeviceDao,
    private val evidenceDao: EvidenceDao,
    private val storage: EvidenceStorage,
    private val api: ProofDropApi,
    private val json: Json,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        syncOrders()
        syncDevices()
        uploadEvidence()
        Result.success()
    } catch (e: IOException) {
        Result.retry()
    }

    private suspend fun syncOrders() {
        for (order in orderDao.pending()) {
            try {
                api.updateStatus(order.id, OrderStatusUpdate(order.status, System.currentTimeMillis()))
            } catch (e: HttpException) {
                // Server doesn't know this order (e.g. demo data); keep the local state.
            }
            orderDao.markSynced(order.id)
        }
    }

    private suspend fun syncDevices() {
        for (device in deviceDao.pending()) {
            val confirmed = try {
                when (device.pendingAction) {
                    PendingDeviceAction.CHECKOUT -> api.checkout(
                        device.id,
                        CheckoutRequest(CurrentCourier.ID, CurrentCourier.NAME, device.checkedOutAt ?: System.currentTimeMillis()),
                    )
                    PendingDeviceAction.RETURN -> api.returnDevice(device.id)
                    null -> continue
                }
            } catch (e: HttpException) {
                device.toModel()
            }
            deviceDao.upsert(listOf(confirmed.toEntity()))
        }
    }

    private suspend fun uploadEvidence() {
        for (entity in evidenceDao.pendingUpload()) {
            val file = storage.file(entity.fileName)
            if (!file.exists()) {
                evidenceDao.setUploadState(entity.id, UploadState.REJECTED, "Media file missing on device")
                continue
            }
            val response = api.uploadEvidence(
                record = json.encodeToString(entity.toModel()).toRequestBody("application/json".toMediaType()),
                file = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("image/jpeg".toMediaType())),
            )
            when {
                response.isSuccessful ->
                    evidenceDao.setUploadState(entity.id, UploadState.UPLOADED, response.body()?.message)
                response.code() == 422 -> {
                    val reason = response.errorBody()?.string()
                        ?.let { runCatching { json.decodeFromString<EvidenceUploadResult>(it).message }.getOrNull() }
                    evidenceDao.setUploadState(entity.id, UploadState.REJECTED, reason ?: "Rejected by server")
                }
                else -> throw IOException("Upload failed with HTTP ${response.code()}")
            }
        }
    }
}
