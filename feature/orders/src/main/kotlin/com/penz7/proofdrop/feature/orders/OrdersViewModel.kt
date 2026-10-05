package com.penz7.proofdrop.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.penz7.proofdrop.core.data.repository.OrderRepository
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
)

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val repository: OrderRepository,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val online = MutableStateFlow<Boolean?>(null)

    val uiState: StateFlow<OrdersUiState> =
        combine(repository.observeOrders(), refreshing, online, ::OrdersUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrdersUiState())

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off messages for a snackbar, e.g. a newly pushed assignment. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        refresh()
        viewModelScope.launch {
            repository.liveAssignments().collect { order ->
                _messages.send("New order ${order.id} · ${order.address}")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            online.value = repository.refresh()
            refreshing.value = false
        }
    }
}
