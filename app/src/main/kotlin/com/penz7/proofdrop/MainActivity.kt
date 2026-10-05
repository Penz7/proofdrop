package com.penz7.proofdrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.penz7.proofdrop.core.designsystem.theme.ProofDropTheme
import com.penz7.proofdrop.navigation.ProofDropApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProofDropTheme {
                ProofDropApp()
            }
        }
    }
}
