package com.penz7.proofdrop.feature.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.penz7.proofdrop.core.data.auth.AuthRepository
import com.penz7.proofdrop.core.data.repository.DeviceRepository
import com.penz7.proofdrop.core.data.repository.ToggleResult
import com.penz7.proofdrop.core.model.Device
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val repository: DeviceRepository,
    auth: AuthRepository,
) : ViewModel() {

    /** Id of the signed-in courier, to tell "with you" apart from "with someone else". */
    val myId: String? = auth.currentUser.value?.user?.id

    val devices: StateFlow<List<Device>> = repository.observeDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch { repository.refresh() }
    }

    fun startScan() {
        _scanning.value = true
    }

    fun stopScan() {
        _scanning.value = false
    }

    /** QR codes on devices simply contain the device id, e.g. "DEV-001". */
    fun onCodeScanned(code: String) {
        if (!_scanning.value) return
        _scanning.value = false
        toggle(code.trim())
    }

    fun toggle(deviceId: String) {
        viewModelScope.launch {
            val message = when (val result = repository.toggle(deviceId)) {
                is ToggleResult.CheckedOut -> "Checked out ${result.device.name}" + offlineSuffix(result.offline)
                is ToggleResult.Returned -> "Returned ${result.device.name}" + offlineSuffix(result.offline)
                is ToggleResult.HeldByOther -> "$deviceId is held by ${result.holderName}"
                ToggleResult.UnknownDevice -> "Unknown device code: $deviceId"
            }
            _messages.send(message)
        }
    }

    private fun offlineSuffix(offline: Boolean) = if (offline) " (offline, will sync)" else ""
}
