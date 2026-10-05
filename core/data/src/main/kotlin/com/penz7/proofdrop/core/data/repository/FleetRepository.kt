package com.penz7.proofdrop.core.data.repository

import com.penz7.proofdrop.core.data.LocationProvider
import com.penz7.proofdrop.core.model.CourierPosition
import com.penz7.proofdrop.core.model.CourierStatus
import com.penz7.proofdrop.core.model.CurrentCourier
import com.penz7.proofdrop.core.model.DemoData
import com.penz7.proofdrop.core.network.FleetSocket
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class FleetSource { LIVE, SIMULATED }

data class FleetState(val couriers: List<CourierPosition>, val source: FleetSource)

interface FleetRepository {
    /** Live positions over WebSocket; falls back to a local simulation while the server is unreachable. */
    fun observeFleet(): Flow<FleetState>
}

@Singleton
class LiveFleetRepository @Inject constructor(
    private val socket: FleetSocket,
    private val location: LocationProvider,
) : FleetRepository {

    private fun self(): CourierPosition? = location.lastKnown()?.let {
        CourierPosition(
            CurrentCourier.ID, CurrentCourier.NAME, it.latitude, it.longitude,
            CourierStatus.EN_ROUTE, System.currentTimeMillis(),
        )
    }

    override fun observeFleet(): Flow<FleetState> = channelFlow {
        var tick = 0
        while (isActive) {
            try {
                socket.positions(::self).collect { send(FleetState(it, FleetSource.LIVE)) }
            } catch (e: IOException) {
                // Server unreachable: fall through to the simulator, then try again.
            }
            repeat(RETRY_AFTER_TICKS) {
                tick++
                val now = System.currentTimeMillis()
                val simulated = DemoData.courierIds.map { DemoData.simulatedPosition(it, tick, now) }
                send(FleetState(simulated + listOfNotNull(self()), FleetSource.SIMULATED))
                delay(1_000)
            }
        }
    }

    private companion object {
        const val RETRY_AFTER_TICKS = 10
    }
}
