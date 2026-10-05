package com.penz7.proofdrop.core.data.di

import com.penz7.proofdrop.core.data.auth.AuthRepository
import com.penz7.proofdrop.core.data.auth.DefaultAuthRepository
import com.penz7.proofdrop.core.data.repository.ChainedEvidenceRepository
import com.penz7.proofdrop.core.data.repository.DeviceRepository
import com.penz7.proofdrop.core.data.repository.EvidenceRepository
import com.penz7.proofdrop.core.data.repository.FleetRepository
import com.penz7.proofdrop.core.data.repository.LiveFleetRepository
import com.penz7.proofdrop.core.data.repository.OfflineFirstDeviceRepository
import com.penz7.proofdrop.core.data.repository.OfflineFirstOrderRepository
import com.penz7.proofdrop.core.data.repository.OrderRepository
import com.penz7.proofdrop.core.data.shift.ShiftController
import com.penz7.proofdrop.core.data.shift.ShiftManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun auth(impl: DefaultAuthRepository): AuthRepository
    @Binds abstract fun orders(impl: OfflineFirstOrderRepository): OrderRepository
    @Binds abstract fun evidence(impl: ChainedEvidenceRepository): EvidenceRepository
    @Binds abstract fun devices(impl: OfflineFirstDeviceRepository): DeviceRepository
    @Binds abstract fun fleet(impl: LiveFleetRepository): FleetRepository
    @Binds abstract fun shift(impl: ShiftController): ShiftManager
}
