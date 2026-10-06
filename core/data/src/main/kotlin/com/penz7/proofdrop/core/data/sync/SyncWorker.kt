package com.penz7.proofdrop.core.data.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.penz7.proofdrop.core.data.R
import com.penz7.proofdrop.core.data.repository.EvidenceStorage
import com.penz7.proofdrop.core.database.DeviceDao
import com.penz7.proofdrop.core.database.EvidenceDao
import com.penz7.proofdrop.core.database.OrderDao
import com.penz7.proofdrop.core.database.PendingDeviceAction
import com.penz7.proofdrop.core.database.UploadState
import com.penz7.proofdrop.core.database.toEntity
import com.penz7.proofdrop.core.database.toModel
import com.penz7.proofdrop.core.model.CheckoutRequest
import com.penz7.proofdrop.core.model.EvidenceUploadResult
import com.penz7.proofdrop.core.model.OrderStatus
import com.penz7.proofdrop.core.model.OrderStatusUpdate
import com.penz7.proofdrop.core.network.ProofDropApi
import com.penz7.proofdrop.core.network.session.SessionStore
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
    private val session: SessionStore,
    private val api: ProofDropApi,
    private val json: Json,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val current = session.session.value
        if (current == null || current.demo) return Result.success()
        return try {
            syncOrders()
            syncDevices(current.user.id, current.user.name)
            uploadEvidence()
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: HttpException) {
            // 401: the session is gone and the UI is returning to login; nothing to retry.
            if (e.code() == 401) Result.failure() else Result.retry()
        }
    }

    private suspend fun syncOrders() {
        for (order in orderDao.pending()) {
            // DELIVERED is set by the server when it accepts the evidence; never claim it without proof.
            if (order.status == OrderStatus.DELIVERED) continue
            try {
                val confirmed = api.updateStatus(order.id, OrderStatusUpdate(order.status, System.currentTimeMillis()))
                orderDao.markSynced(order.id)
                orderDao.upsert(listOf(confirmed.toEntity()))
            } catch (e: HttpException) {
                if (e.code() == 401) throw e
                // 404/409: reassigned or an invalid transition. Drop the local change; the next refresh corrects it.
                orderDao.markSynced(order.id)
            }
        }
    }

    private suspend fun syncDevices(myId: String, myName: String) {
        for (device in deviceDao.pending()) {
            val confirmed = try {
                when (device.pendingAction) {
                    PendingDeviceAction.CHECKOUT -> api.checkout(
                        device.id,
                        CheckoutRequest(myId, myName, device.checkedOutAt ?: System.currentTimeMillis()),
                    )
                    PendingDeviceAction.RETURN -> api.returnDevice(device.id)
                    null -> continue
                }
            } catch (e: HttpException) {
                if (e.code() == 401) throw e
                device.toModel()
            }
            deviceDao.upsert(listOf(confirmed.toEntity()))
            if (device.pendingAction == PendingDeviceAction.CHECKOUT && confirmed.holderId != myId) {
                // The offline checkout lost: someone else took the device first.
                notifyCheckoutLost(confirmed.name, confirmed.holderName ?: "another courier")
            }
        }
    }

    private fun notifyCheckoutLost(deviceName: String, holder: String) {
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!canNotify) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_SYNC, "Sync problems", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_stat_proofdrop)
            .setContentTitle("Device not checked out")
            .setContentText("$deviceName was already checked out by $holder while you were offline.")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(deviceName.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_SYNC = "sync"
    }

    /** In sequence order: the server needs record n-1 before it accepts record n. */
    private suspend fun uploadEvidence() {
        for (entity in evidenceDao.pendingUpload()) {
            val rejected = evidenceDao.firstRejected()
            if (rejected != null && rejected.sequence < entity.sequence) {
                // Retrying can't help: the server will never have the record this one links to.
                evidenceDao.setUploadState(entity.id, UploadState.REJECTED, "Blocked: record #${rejected.sequence} was rejected")
                continue
            }
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
                response.isSuccessful -> {
                    evidenceDao.setUploadState(entity.id, UploadState.UPLOADED, response.body()?.message)
                    // The server marked the order delivered as part of accepting the proof.
                    orderDao.markSynced(entity.orderId)
                }
                response.code() == 422 -> {
                    val reason = response.errorBody()?.string()
                        ?.let { runCatching { json.decodeFromString<EvidenceUploadResult>(it).message }.getOrNull() }
                    evidenceDao.setUploadState(entity.id, UploadState.REJECTED, reason ?: "Rejected by server")
                }
                response.code() == 401 -> throw HttpException(response)
                // 409 (previous record not there yet) or a server error: try again later, keep the order.
                else -> throw IOException("Upload of #${entity.sequence} failed with HTTP ${response.code()}")
            }
        }
    }
}
