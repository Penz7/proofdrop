package com.penz7.proofdrop.feature.orders

import app.cash.turbine.test
import com.penz7.proofdrop.core.data.auth.AuthRepository
import com.penz7.proofdrop.core.data.auth.CurrentUser
import com.penz7.proofdrop.core.data.auth.LoginResult
import com.penz7.proofdrop.core.data.repository.OrderRepository
import com.penz7.proofdrop.core.data.shift.ShiftManager
import com.penz7.proofdrop.core.model.DemoData
import com.penz7.proofdrop.core.model.Order
import com.penz7.proofdrop.core.model.OrderStatus
import com.penz7.proofdrop.core.model.Role
import com.penz7.proofdrop.core.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private class FakeAuth : AuthRepository {
        override val currentUser = MutableStateFlow<CurrentUser?>(
            CurrentUser(User("c1", "courier1@proofdrop.dev", "Nguyễn Văn An", Role.COURIER), demo = false),
        )
        override val sessionExpired: Flow<Unit> = emptyFlow()
        override val serverUrl = MutableStateFlow("http://10.0.2.2:3000")
        var unsynced = 0
        var loggedOut = false

        override suspend fun login(serverUrl: String, email: String, password: String) = LoginResult.Success
        override suspend fun startDemo() = Unit
        override suspend fun unsyncedEvidenceCount() = unsynced
        override suspend fun logout() {
            loggedOut = true
        }
    }

    private class FakeShift : ShiftManager {
        override val onShift = MutableStateFlow(false)
        override fun start() {
            onShift.value = true
        }
        override fun stop() {
            onShift.value = false
        }
    }

    private val repository = FakeOrderRepository()
    private val auth = FakeAuth()
    private val shift = FakeShift()

    private fun viewModel() = OrdersViewModel(repository, auth, shift)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows local orders and offline badge when server is unreachable`() = runTest {
        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertEquals(5, state.orders.size)
            assertEquals(false, state.online)
            assertEquals("Nguyễn Văn An", state.userName)
            assertFalse(state.isRefreshing)
        }
    }

    @Test
    fun `pushed assignment is surfaced as a message`() = runTest {
        val viewModel = viewModel()
        viewModel.messages.test {
            repository.assignments.emit(DemoData.orders(now = 0).first().copy(code = "PD-2000"))
            assertEquals("New order PD-2000 · 12 Nguyễn Huệ, Q.1, TP.HCM", awaitItem())
        }
    }

    @Test
    fun `refresh reports online once the server answers`() = runTest {
        val viewModel = viewModel()
        repository.serverReachable = true
        viewModel.refresh()
        viewModel.uiState.test {
            assertEquals(true, expectMostRecentItem().online)
        }
    }

    @Test
    fun `starting a shift is reflected in state`() = runTest {
        val viewModel = viewModel()
        viewModel.startShift()
        viewModel.uiState.test {
            assertTrue(expectMostRecentItem().onShift)
        }
    }

    @Test
    fun `logout asks for confirmation when evidence is unsynced`() = runTest {
        auth.unsynced = 2
        val viewModel = viewModel()
        viewModel.uiState.test {
            viewModel.requestLogout()
            assertEquals(2, expectMostRecentItem().unsyncedOnLogout)
            assertFalse(auth.loggedOut)

            viewModel.confirmLogout()
            assertNull(expectMostRecentItem().unsyncedOnLogout)
            assertTrue(auth.loggedOut)
        }
    }
}
