package com.penz7.proofdrop.feature.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.penz7.proofdrop.core.data.repository.OrderRepository
import com.penz7.proofdrop.core.designsystem.component.StatusChip
import com.penz7.proofdrop.core.designsystem.theme.SuccessGreen
import com.penz7.proofdrop.core.model.Order
import com.penz7.proofdrop.core.model.OrderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: OrderRepository,
) : ViewModel() {
    private val orderId = savedStateHandle.toRoute<OrderDetailDestination>().orderId

    val order: StateFlow<Order?> = repository.observeOrder(orderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setStatus(status: OrderStatus) {
        viewModelScope.launch { repository.updateStatus(orderId, status) }
    }
}

@Composable
internal fun OrderDetailRoute(
    onCaptureProof: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: OrderDetailViewModel = hiltViewModel(),
) {
    val order by viewModel.order.collectAsStateWithLifecycle()
    OrderDetailScreen(order, onBack, onCaptureProof, viewModel::setStatus)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderDetailScreen(
    order: Order?,
    onBack: () -> Unit,
    onCaptureProof: (String) -> Unit,
    onSetStatus: (OrderStatus) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(order?.id ?: "Order") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        if (order == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(order.customerName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        StatusChip(order.status.label, order.status.color)
                    }
                    Detail("Address", order.address)
                    Detail("Items", order.items)
                    Detail("Coordinates", "%.5f, %.5f".format(order.latitude, order.longitude))
                    Detail("Drop-off beacon", order.beaconId ?: "None. Photo + GPS proof only")
                    Detail("Assigned", DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(order.assignedAt)))
                }
            }

            when (order.status) {
                OrderStatus.ASSIGNED, OrderStatus.PICKED_UP -> {
                    if (order.status == OrderStatus.ASSIGNED) {
                        FilledTonalButton(onClick = { onSetStatus(OrderStatus.PICKED_UP) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Mark picked up")
                        }
                    }
                    Button(onClick = { onCaptureProof(order.id) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Capture proof of delivery")
                    }
                    OutlinedButton(onClick = { onSetStatus(OrderStatus.FAILED) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Report failed delivery")
                    }
                }
                OrderStatus.DELIVERED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Verified, null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delivered. Proof is sealed in the evidence ledger.")
                }
                OrderStatus.FAILED -> OutlinedButton(
                    onClick = { onSetStatus(OrderStatus.ASSIGNED) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Retry delivery") }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
