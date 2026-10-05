package com.penz7.proofdrop.core.designsystem.component

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Shows [content] once all [required] permissions are granted. [optional] permissions are
 * requested at the same time but never block the screen (e.g. location for evidence tags).
 */
@Composable
fun PermissionGate(
    required: List<String>,
    rationale: String,
    optional: List<String> = emptyList(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    fun allGranted() = required.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(allGranted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = allGranted()
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch((required + optional).toTypedArray())
    }

    if (granted) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(rationale, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Button(onClick = { launcher.launch((required + optional).toTypedArray()) }) {
                Text("Grant permission")
            }
        }
    }
}
