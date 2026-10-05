package com.penz7.proofdrop.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.penz7.proofdrop.core.data.auth.AuthRepository
import com.penz7.proofdrop.core.data.auth.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val serverUrl: String,
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState(serverUrl = auth.serverUrl.value))
    val state: StateFlow<LoginUiState> = _state

    fun onServerUrl(value: String) = _state.update { it.copy(serverUrl = value, error = null) }
    fun onEmail(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun login() {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "Enter your email and password") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val error = when (val result = auth.login(s.serverUrl, s.email, s.password)) {
                LoginResult.Success -> null
                LoginResult.InvalidServerUrl -> "Server URL is not valid (e.g. http://10.0.2.2:3000)"
                LoginResult.InvalidCredentials -> "Wrong email or password"
                LoginResult.NotACourier -> "This is a dispatcher account. Use the web dashboard instead."
                is LoginResult.Unreachable -> "Cannot reach the server: ${result.reason}"
            }
            _state.update { it.copy(loading = false, error = error) }
        }
    }

    fun startDemo() {
        viewModelScope.launch { auth.startDemo() }
    }
}
