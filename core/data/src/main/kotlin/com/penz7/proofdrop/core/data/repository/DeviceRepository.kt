package com.penz7.proofdrop.core.data.repository

import com.penz7.proofdrop.core.data.DemoSeeder
import com.penz7.proofdrop.core.data.sync.SyncScheduler
import com.penz7.proofdrop.core.database.DeviceDao
import com.penz7.proofdrop.core.database.PendingDeviceAction
import com.penz7.proofdrop.core.database.toEntity
import com.penz7.proofdrop.core.database.toModel
import com.penz7.proofdrop.core.model.CheckoutRequest
import com.penz7.proofdrop.core.model.CurrentCourier
import com.penz7.proofdrop.core.model.Device
import com.penz7.proofdrop.core.network.ProofDropApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ToggleResult {
    data class CheckedOut(val device: Device, val offline: Boolean) : ToggleResult
    data class Returned(val device: Device, val offline: Boolean) : ToggleResult
    data class HeldByOther(val holderName: String) : ToggleResult
    data object UnknownDevice : ToggleResult
}

interface DeviceRepository {
    fun observeDevices(): Flow<List<Device>>
    suspend fun refresh(): Boolean

    /** Checks the device out to the current courier, or returns it if they already hold it. */
    suspend fun toggle(deviceId: String): ToggleResult
}

@Singleton
class OfflineFirstDeviceRepository @Inject constructor(
    private val dao: DeviceDao,
    private val api: ProofDropApi,
    private val seeder: DemoSeeder,
    private val syncScheduler: SyncScheduler,
) : DeviceRepository {

    override fun observeDevices(): Flow<List<Device>> =
        dao.observeAll()
            .onStart { seeder.seedIfEmpty() }
            .map { list -> list.map { it.toModel() } }

    override suspend fun refresh(): Boolean = try {
        dao.mergeFromServer(api.devices().map { it.toEntity() })
        true
    } catch (e: IOException) {
        false
    } catch (e: HttpException) {
        false
    }

    override suspend fun toggle(deviceId: String): ToggleResult {
        val current = dao.get(deviceId)?.toModel() ?: return ToggleResult.UnknownDevice
        if (!current.isAvailable && current.holderId != CurrentCourier.ID) {
            return ToggleResult.HeldByOther(current.holderName ?: "another courier")
        }
        val returning = current.holderId == CurrentCourier.ID
        val now = System.currentTimeMillis()
        val optimistic = if (returning) {
            current.copy(holderId = null, holderName = null, checkedOutAt = null)
        } else {
            current.copy(holderId = CurrentCourier.ID, holderName = CurrentCourier.NAME, checkedOutAt = now)
        }
        val action = if (returning) PendingDeviceAction.RETURN else PendingDeviceAction.CHECKOUT
        dao.upsert(listOf(optimistic.toEntity(pendingAction = action)))

        return try {
            val confirmed = if (returning) {
                api.returnDevice(deviceId)
            } else {
                api.checkout(deviceId, CheckoutRequest(CurrentCourier.ID, CurrentCourier.NAME, now))
            }
            dao.upsert(listOf(confirmed.toEntity()))
            when {
                !returning && confirmed.holderId != CurrentCourier.ID ->
                    ToggleResult.HeldByOther(confirmed.holderName ?: "another courier")
                returning -> ToggleResult.Returned(confirmed, offline = false)
                else -> ToggleResult.CheckedOut(confirmed, offline = false)
            }
        } catch (e: IOException) {
            syncScheduler.requestSync()
            if (returning) ToggleResult.Returned(optimistic, offline = true) else ToggleResult.CheckedOut(optimistic, offline = true)
        } catch (e: HttpException) {
            dao.upsert(listOf(current.toEntity()))
            ToggleResult.UnknownDevice
        }
    }
}
