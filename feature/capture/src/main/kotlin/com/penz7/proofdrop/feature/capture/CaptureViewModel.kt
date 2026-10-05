package com.penz7.proofdrop.feature.capture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.penz7.proofdrop.core.ble.BeaconMatcher
import com.penz7.proofdrop.core.ble.BleScanState
import com.penz7.proofdrop.core.ble.BleScanner
import com.penz7.proofdrop.core.data.repository.EvidenceRepository
import com.penz7.proofdrop.core.data.repository.OrderRepository
import com.penz7.proofdrop.core.model.EvidenceRecord
import com.penz7.proofdrop.core.model.Order
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface BeaconUi {
    data object NotRequired : BeaconUi
    data object BluetoothOff : BeaconUi
    data object NoPermission : BeaconUi
    data class Searching(val nearbyDevices: Int) : BeaconUi
    data class Verified(val rssi: Int) : BeaconUi
}

sealed interface CapturePhase {
    data object Ready : CapturePhase
    data object Sealing : CapturePhase
    data class Sealed(val record: EvidenceRecord) : CapturePhase
    data class Failed(val message: String) : CapturePhase
}

data class CaptureUiState(
    val order: Order? = null,
    val beacon: BeaconUi = BeaconUi.NotRequired,
    val phase: CapturePhase = CapturePhase.Ready,
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    orders: OrderRepository,
    bleScanner: BleScanner,
    private val evidence: EvidenceRepository,
) : ViewModel() {

    private val orderId = savedStateHandle.toRoute<CaptureDestination>().orderId
    private val phase = MutableStateFlow<CapturePhase>(CapturePhase.Ready)

    val uiState: StateFlow<CaptureUiState> =
        combine(orders.observeOrder(orderId), bleScanner.scan(), phase) { order, scan, phase ->
            CaptureUiState(order, beaconUi(order, scan), phase)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptureUiState())

    fun newCaptureFile(): File = evidence.newCaptureFile()

    fun onPhotoSaved(file: File) {
        if (phase.value is CapturePhase.Sealing) return
        phase.value = CapturePhase.Sealing
        viewModelScope.launch {
            val bleVerified = uiState.value.beacon is BeaconUi.Verified
            phase.value = runCatching { evidence.sealDelivery(orderId, file, bleVerified) }
                .fold(
                    onSuccess = { CapturePhase.Sealed(it) },
                    onFailure = { CapturePhase.Failed(it.message ?: "Could not seal evidence") },
                )
        }
    }

    fun onCaptureError(message: String?) {
        phase.value = CapturePhase.Failed(message ?: "Camera error")
    }

    fun retry() {
        phase.value = CapturePhase.Ready
    }

    private fun beaconUi(order: Order?, scan: BleScanState): BeaconUi {
        val beaconId = order?.beaconId ?: return BeaconUi.NotRequired
        return when (scan) {
            BleScanState.Unavailable, is BleScanState.Failed -> BeaconUi.BluetoothOff
            BleScanState.MissingPermission -> BeaconUi.NoPermission
            is BleScanState.Scanning -> {
                val match = scan.devices.firstOrNull { BeaconMatcher.isNear(beaconId, listOf(it)) }
                if (match != null) BeaconUi.Verified(match.rssi) else BeaconUi.Searching(scan.devices.size)
            }
        }
    }
}
