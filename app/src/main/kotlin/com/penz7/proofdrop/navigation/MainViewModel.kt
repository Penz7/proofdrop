package com.penz7.proofdrop.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.penz7.proofdrop.core.data.auth.AuthRepository
import com.penz7.proofdrop.core.data.auth.CurrentUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    val currentUser: StateFlow<CurrentUser?> = auth.currentUser

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            auth.sessionExpired.collect {
                auth.logout()
                _messages.send("Your session expired. Please sign in again.")
            }
        }
    }
}
