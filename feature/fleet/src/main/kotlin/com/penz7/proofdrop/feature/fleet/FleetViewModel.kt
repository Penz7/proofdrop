package com.penz7.proofdrop.feature.fleet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.penz7.proofdrop.core.data.ServerSettings
import com.penz7.proofdrop.core.data.repository.FleetRepository
import com.penz7.proofdrop.core.data.repository.FleetState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FleetViewModel @Inject constructor(
    repository: FleetRepository,
    private val settings: ServerSettings,
) : ViewModel() {

    val serverUrl: StateFlow<String> = settings.baseUrl

    /** Reconnects automatically when the server URL changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val fleet: StateFlow<FleetState?> = settings.baseUrl
        .flatMapLatest { repository.observeFleet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateServerUrl(url: String): Boolean = settings.update(url)
}
