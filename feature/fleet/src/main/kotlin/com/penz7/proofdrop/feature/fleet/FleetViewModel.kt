package com.penz7.proofdrop.feature.fleet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.penz7.proofdrop.core.data.repository.FleetRepository
import com.penz7.proofdrop.core.data.repository.FleetState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FleetViewModel @Inject constructor(
    repository: FleetRepository,
) : ViewModel() {
    val fleet: StateFlow<FleetState?> = repository.observeFleet()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
