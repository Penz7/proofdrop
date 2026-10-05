package com.penz7.proofdrop.server

import com.penz7.proofdrop.core.model.CheckoutRequest
import com.penz7.proofdrop.core.model.CourierPosition
import com.penz7.proofdrop.core.model.DemoData
import com.penz7.proofdrop.core.model.Device
import com.penz7.proofdrop.core.model.EvidenceRecord
import com.penz7.proofdrop.core.model.Order
import com.penz7.proofdrop.core.model.OrderStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** In-memory dispatch backend. Good enough for a demo; swap for a database in production. */
class DispatchState(private val clock: () -> Long = System::currentTimeMillis) {

    val orders = ConcurrentHashMap<String, Order>()
    val devices = ConcurrentHashMap<String, Device>()
    val evidence = ConcurrentHashMap<String, EvidenceRecord>()

    private val _fleet = MutableStateFlow<Map<String, CourierPosition>>(emptyMap())
    val fleet: StateFlow<Map<String, CourierPosition>> = _fleet

    private val _assignments = MutableSharedFlow<Order>(extraBufferCapacity = 16)
    val assignments: SharedFlow<Order> = _assignments

    private var orderCounter = 0

    init {
        DemoData.orders(clock()).forEach { orders[it.id] = it }
        orderCounter = orders.size
        DemoData.devices().forEach { devices[it.id] = it }
        _fleet.value = DemoData.courierIds
            .map { DemoData.simulatedPosition(it, tick = 0, now = clock()) }
            .associateBy { it.courierId }
    }

    fun updateStatus(orderId: String, status: OrderStatus): Order? =
        orders.computeIfPresent(orderId) { _, order -> order.copy(status = status) }

    /** Returns the device unchanged if someone else already holds it. */
    fun checkout(deviceId: String, request: CheckoutRequest): Device? =
        devices.computeIfPresent(deviceId) { _, d ->
            if (!d.isAvailable && d.holderId != request.courierId) d
            else d.copy(holderId = request.courierId, holderName = request.courierName, checkedOutAt = request.at)
        }

    fun returnDevice(deviceId: String): Device? =
        devices.computeIfPresent(deviceId) { _, d -> d.copy(holderId = null, holderName = null, checkedOutAt = null) }

    fun report(position: CourierPosition) {
        _fleet.update { it + (position.courierId to position.copy(updatedAt = clock())) }
    }

    suspend fun runFleetSimulation() {
        var tick = 0
        while (true) {
            delay(1_000)
            tick++
            _fleet.update { current ->
                current + DemoData.courierIds.associateWith { DemoData.simulatedPosition(it, tick, clock()) }
            }
        }
    }

    /** Pushes a new order to connected couriers every 45 seconds (delivered over SSE). */
    suspend fun runAssignmentGenerator() {
        while (true) {
            delay(45_000)
            val order = newOrder()
            orders[order.id] = order
            _assignments.emit(order)
        }
    }

    private fun newOrder(): Order {
        orderCounter++
        return Order(
            id = "PD-%04d".format(1000 + orderCounter),
            customerName = DemoData.customers.random(),
            address = "${Random.nextInt(1, 300)} ${DemoData.streets.random()}, Q.1, TP.HCM",
            latitude = DemoData.HUB_LAT + Random.nextDouble(-0.01, 0.01),
            longitude = DemoData.HUB_LNG + Random.nextDouble(-0.01, 0.01),
            items = DemoData.items.random(),
            status = OrderStatus.ASSIGNED,
            beaconId = if (Random.nextBoolean()) "PD-BEACON-01" else null,
            assignedAt = clock(),
        )
    }
}
