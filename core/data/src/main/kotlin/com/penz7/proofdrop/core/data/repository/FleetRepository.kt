package com.penz7.proofdrop.core.data.repository

import com.penz7.proofdrop.core.data.LocationTracker
import com.penz7.proofdrop.core.model.CourierPosition
import com.penz7.proofdrop.core.model.CourierStatus
import com.penz7.proofdrop.core.model.DemoData
import com.penz7.proofdrop.core.model.PositionReport
import com.penz7.proofdrop.core.network.FleetSocket
import com.penz7.proofdrop.core.network.session.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class FleetSource { LIVE, RECONNECTING, DEMO }

data class FleetState(val couriers: List<CourierPosition>, val source: FleetSource, val selfId: String?)

interface FleetRepository {
    /**
     * Live positions over the fleet WebSocket (which also reports our own position).
     * In demo mode, a local simulation instead.
     */
    fun observeFleet(): Flow<FleetState>
}

@Singleton
class LiveFleetRepository @Inject constructor(
    private val socket: FleetSocket,
    private val location: LocationTracker,
    private val session: SessionStore,
) : FleetRepository {

    private fun selfReport(): PositionReport? =
        location.current()?.let { PositionReport(it.latitude, it.longitude, CourierStatus.EN_ROUTE) }

    override fun observeFleet(): Flow<FleetState> = channelFlow {
        val current = session.session.value ?: return@channelFlow
        val selfId = current.user.id
        if (current.demo) {
            var tick = 0
            while (isActive) {
                val now = System.currentTimeMillis()
                val simulated = DemoData.courierIds.map { DemoData.simulatedPosition(it, tick++, now) }
                val self = selfReport()?.let {
                    CourierPosition(selfId, current.user.name, it.latitude, it.longitude, it.status, now)
                }
                send(FleetState(simulated + listOfNotNull(self), FleetSource.DEMO, selfId))
                delay(1_000)
            }
            return@channelFlow
        }

        var last: List<CourierPosition> = emptyList()
        var attempt = 0
        while (isActive) {
            try {
                socket.positions(::selfReport).collect {
                    attempt = 0
                    last = it
                    send(FleetState(it, FleetSource.LIVE, selfId))
                }
            } catch (e: IOException) {
                send(FleetState(last, FleetSource.RECONNECTING, selfId))
            }
            delay(minOf(MAX_BACKOFF_MS, BASE_BACKOFF_MS * ++attempt))
        }
    }

    private companion object {
        const val BASE_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
