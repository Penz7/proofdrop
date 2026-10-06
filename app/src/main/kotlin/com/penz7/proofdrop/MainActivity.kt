package com.penz7.proofdrop

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.penz7.proofdrop.core.data.shift.ShiftService
import com.penz7.proofdrop.core.designsystem.theme.ProofDropTheme
import com.penz7.proofdrop.navigation.ProofDropApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on first creation: after a rotation the order was already opened.
        if (savedInstanceState == null) openOrderId = intent.getStringExtra(ShiftService.EXTRA_ORDER_ID)
        setContent {
            ProofDropTheme {
                ProofDropApp(openOrderId = openOrderId, onOrderOpened = { openOrderId = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(ShiftService.EXTRA_ORDER_ID)?.let { openOrderId = it }
    }

    private var openOrderId by mutableStateOf<String?>(null)
}
