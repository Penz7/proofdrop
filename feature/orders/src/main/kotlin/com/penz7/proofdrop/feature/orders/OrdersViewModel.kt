package com.penz7.proofdrop.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.penz7.proofdrop.core.data.auth.AuthRepository
import com.penz7.proofdrop.core.data.repository.OrderRepository
import com.penz7.proofdrop.core.data.shift.ShiftManager
import com.penz7.proofdrop.core.model.Order
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrdersUiState(
    val orders: List<Order> = emptyList(),
    val isRefreshing: Boolean = false,
    /** null until the first refresh finishes. */
    val online: Boolean? = null,
    /** True until the first database read, so the empty state never flashes on launch. */
    val isLoading: Boolean = false,
    val userName: String? = null,
    val demo: Boolean = false,
    val onShift: Boolean = false,
    /** Non-null while asking to confirm a logout that would drop unsynced evidence. */
    val unsyncedOnLogout: Int? = null,
)

private data class Status(val refreshing: Boolean, val online: Boolean?, val unsyncedOnLogout: Int?)

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val repository: OrderRepository,
    private val auth: AuthRepository,
    private val shift: ShiftManager,
) : ViewModel() {

    private val status = MutableStateFlow(Status(refreshing = false, online = null, unsyncedOnLogout = null))

    val uiState: StateFlow<OrdersUiState> =
        combine(repository.observeOrders(), status, auth.currentUser, shift.onShift) { orders, status, user, onShift ->
            OrdersUiState(
                orders = orders,
                isRefreshing = status.refreshing,
                online = if (user?.demo == true) false else status.online,
                userName = user?.user?.name,
                demo = user?.demo == true,
                onShift = onShift,
                unsyncedOnLogout = status.unsyncedOnLogout,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrdersUiState(isLoading = true))

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off messages for a snackbar, e.g. a newly pushed assignment. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        refresh()
        viewModelScope.launch {
            repository.liveAssignments().collect { order ->
                _messages.send("New order ${order.code} · ${order.address}")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            status.value = status.value.copy(refreshing = true)
            val online = repository.refresh()
            status.value = status.value.copy(refreshing = false, online = online)
        }
    }

    fun startShift() = shift.start()

    fun endShift() = shift.stop()

    fun onLocationDenied() {
        _messages.trySend("Location permission is required to start a shift")
    }

    fun requestLogout() {
        viewModelScope.launch {
            val unsynced = auth.unsyncedEvidenceCount()
            if (unsynced > 0) status.value = status.value.copy(unsyncedOnLogout = unsynced) else auth.logout()
        }
    }

    fun confirmLogout() {
        status.value = status.value.copy(unsyncedOnLogout = null)
        viewModelScope.launch { auth.logout() }
    }

    fun dismissLogout() {
        status.value = status.value.copy(unsyncedOnLogout = null)
    }
}
