package com.penz7.proofdrop.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.penz7.proofdrop.core.designsystem.theme.DangerRed
import kotlinx.serialization.Serializable

@Serializable data object LoginDestination

/** Navigation away from login is driven by the session state in the app module. */
fun NavGraphBuilder.loginScreen(showDevHints: Boolean) {
    composable<LoginDestination> { LoginRoute(showDevHints) }
}

@Composable
internal fun LoginRoute(showDevHints: Boolean, viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginScreen(
        state = state,
        showDevHints = showDevHints,
        onServerUrl = viewModel::onServerUrl,
        onEmail = viewModel::onEmail,
        onPassword = viewModel::onPassword,
        onLogin = viewModel::login,
        onDemo = viewModel::startDemo,
    )
}

@Composable
private fun LoginScreen(
    state: LoginUiState,
    showDevHints: Boolean,
    onServerUrl: (String) -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onLogin: () -> Unit,
    onDemo: () -> Unit,
) {
    // Hide the keyboard on submit, otherwise it covers the error message below the form.
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val submit = {
        keyboard?.hide()
        focusManager.clearFocus()
        onLogin()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Filled.GppGood, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text("ProofDrop", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Tamper-evident proof of delivery",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmail,
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPassword,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = onServerUrl,
            label = { Text("Server URL") },
            singleLine = true,
            supportingText = { Text("Emulator: http://10.0.2.2:3000 · USB: http://127.0.0.1:3000 (adb reverse)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let { Text(it, color = DangerRed, textAlign = TextAlign.Center) }

        Button(onClick = submit, enabled = !state.loading, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Sign in")
        }

        if (showDevHints) {
            Text(
                "Local test account: courier1@proofdrop.dev / courier123",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        OutlinedButton(onClick = onDemo, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            Text("Try demo mode (no server)")
        }
        Text(
            "Demo mode uses sample orders and a simulated fleet on this phone only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
        )
    }
}
