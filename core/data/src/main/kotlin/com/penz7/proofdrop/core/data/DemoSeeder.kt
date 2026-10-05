package com.penz7.proofdrop.core.data

import com.penz7.proofdrop.core.database.DeviceDao
import com.penz7.proofdrop.core.database.OrderDao
import com.penz7.proofdrop.core.database.toEntity
import com.penz7.proofdrop.core.model.DemoData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Fills an empty database with demo data so the app works on first launch without a server. */
@Singleton
class DemoSeeder @Inject constructor(
    private val orderDao: OrderDao,
    private val deviceDao: DeviceDao,
) {
    private val mutex = Mutex()

    suspend fun seedIfEmpty() = mutex.withLock {
        if (orderDao.count() == 0) {
            orderDao.upsert(DemoData.orders(System.currentTimeMillis()).map { it.toEntity() })
        }
        if (deviceDao.count() == 0) {
            deviceDao.upsert(DemoData.devices().map { it.toEntity() })
        }
    }
}
