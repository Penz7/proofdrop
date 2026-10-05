package com.penz7.proofdrop.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFFFC72C)
private val AmberDark = Color(0xFF7A5900)
private val Navy = Color(0xFF0E1A2B)
private val NavySurface = Color(0xFF172538)
private val Slate = Color(0xFF5B6B7F)

val SuccessGreen = Color(0xFF2E9E5B)
val DangerRed = Color(0xFFD64545)
val InfoBlue = Color(0xFF3B82C4)

private val LightColors = lightColorScheme(
    primary = AmberDark,
    onPrimary = Color.White,
    primaryContainer = Amber,
    onPrimaryContainer = Navy,
    secondary = Slate,
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EDF2),
)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Navy,
    primaryContainer = AmberDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF9FB0C4),
    background = Navy,
    surface = NavySurface,
    surfaceVariant = Color(0xFF22344B),
)

@Composable
fun ProofDropTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
