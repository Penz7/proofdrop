package com.penz7.proofdrop.feature.orders

import app.cash.turbine.test
import com.penz7.proofdrop.core.data.repository.OrderRepository
import com.penz7.proofdrop.core.model.DemoData
import com.penz7.proofdrop.core.model.Order
import com.penz7.proofdrop.core.model.OrderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelTest {

    private class FakeOrderRepository : OrderRepository {
        val orders = MutableStateFlow(DemoData.orders(now = 0))
        val assignments = MutableSharedFlow<Order>()
        var serverReachable = false

        override fun observeOrders(): Flow<List<Order>> = orders
        override fun observeOrder(id: String) = orders.map { list -> list.find { it.id == id } }
        override suspend fun refresh() = serverReachable
        override suspend fun updateStatus(id: String, status: OrderStatus) {
            orders.value = orders.value.map { if (it.id == id) it.copy(status = status) else it }
        }
        override fun liveAssignments(): Flow<Order> = assignments
    }

    private val repository = FakeOrderRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows local orders and offline badge when server is unreachable`() = runTest {
        val viewModel = OrdersViewModel(repository)
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(5, state.orders.size)
            assertEquals(false, state.online)
            assertFalse(state.isRefreshing)
        }
    }

    @Test
    fun `pushed assignment is surfaced as a message`() = runTest {
        val viewModel = OrdersViewModel(repository)
        viewModel.messages.test {
            repository.assignments.emit(DemoData.orders(now = 0).first().copy(id = "PD-2000"))
            assertEquals("New order PD-2000 · 12 Nguyễn Huệ, Q.1, TP.HCM", awaitItem())
        }
    }

    @Test
    fun `refresh reports online once the server answers`() = runTest {
        val viewModel = OrdersViewModel(repository)
        repository.serverReachable = true
        viewModel.refresh()
        viewModel.uiState.test {
            assertEquals(true, expectMostRecentItem().online)
        }
    }
}
