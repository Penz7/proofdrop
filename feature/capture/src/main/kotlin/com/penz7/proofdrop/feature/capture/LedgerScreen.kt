package com.penz7.proofdrop.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.penz7.proofdrop.core.data.repository.EvidenceRepository
import com.penz7.proofdrop.core.data.repository.LedgerEntry
import com.penz7.proofdrop.core.data.repository.UploadStatus
import com.penz7.proofdrop.core.designsystem.component.EmptyState
import com.penz7.proofdrop.core.designsystem.component.StatusChip
import com.penz7.proofdrop.core.designsystem.theme.DangerRed
import com.penz7.proofdrop.core.designsystem.theme.SuccessGreen
import com.penz7.proofdrop.core.evidence.ChainVerification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val repository: EvidenceRepository,
) : ViewModel() {

    private val _verification = MutableStateFlow<ChainVerification?>(null)
    /** null while a check is running. */
    val verification: StateFlow<ChainVerification?> = _verification

    // Re-verify whenever a record is added.
    val entries: StateFlow<List<LedgerEntry>> = repository.observeLedger()
        .distinctUntilChangedBy { it.size }
        .onEach { verify() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun verify() {
        viewModelScope.launch {
            _verification.value = null
            _verification.value = repository.verifyLedger()
        }
    }

    fun tamper() {
        viewModelScope.launch {
            if (repository.tamperWithLatestForDemo()) verify()
        }
    }
}

@Composable
internal fun LedgerRoute(showDebugTools: Boolean, viewModel: LedgerViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val verification by viewModel.verification.collectAsStateWithLifecycle()
    LedgerScreen(entries, verification, showDebugTools, viewModel::verify, viewModel::tamper)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerScreen(
    entries: List<LedgerEntry>,
    verification: ChainVerification?,
    showDebugTools: Boolean,
    onVerify: () -> Unit,
    onTamper: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Evidence ledger") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { VerificationCard(verification, entries.isNotEmpty(), showDebugTools, onVerify, onTamper) }
            if (entries.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.ReceiptLong,
                        "No evidence yet",
                        "Capture proof of delivery on an order. Every photo is hashed and chained here.",
                    )
                }
            }
            items(entries, key = { it.record.id }) { EntryCard(it) }
        }
    }
}

@Composable
private fun VerificationCard(
    verification: ChainVerification?,
    hasEntries: Boolean,
    showDebugTools: Boolean,
    onVerify: () -> Unit,
    onTamper: () -> Unit,
) {
    val (color, icon, title, body) = when (verification) {
        null -> Quad(MaterialTheme.colorScheme.secondary, null, "Verifying…", "Re-hashing every photo and link")
        is ChainVerification.Valid -> Quad(
            SuccessGreen, Icons.Filled.GppGood, "Chain intact",
            "${verification.count} records · every link and photo hash matches",
        )
        is ChainVerification.Broken -> Quad(
            DangerRed, Icons.Filled.GppBad, "Tampering detected at #${verification.atSequence}", verification.reason,
        )
    }
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon == null) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
                    Text(body, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onVerify) { Text("Verify again") }
                if (showDebugTools && hasEntries) {
                    TextButton(onClick = onTamper) { Text("Simulate tampering", color = DangerRed) }
                }
            }
        }
    }
}

private data class Quad(val color: Color, val icon: androidx.compose.ui.graphics.vector.ImageVector?, val title: String, val body: String)

@Composable
private fun EntryCard(entry: LedgerEntry) {
    val r = entry.record
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${r.sequence} · ${r.orderId}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (r.latitude != null) Icon(Icons.Outlined.LocationOn, "GPS tagged", Modifier.size(16.dp))
                if (r.bleVerified) Icon(Icons.Outlined.Bluetooth, "Beacon verified", Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                when (entry.upload) {
                    UploadStatus.PENDING -> StatusChip("Queued", MaterialTheme.colorScheme.secondary)
                    UploadStatus.UPLOADED -> StatusChip("Synced", SuccessGreen)
                    UploadStatus.REJECTED -> StatusChip("Rejected", DangerRed)
                }
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(r.capturedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            HashLine("photo", r.fileSha256)
            HashLine("prev ", r.previousHash)
            HashLine("hash ", r.recordHash)
            val message = entry.message
            if (entry.upload == UploadStatus.REJECTED && message != null) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = DangerRed)
            }
        }
    }
}
