package com.omnigraph.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF245C73),
    onPrimary = Color.White,
    secondary = Color(0xFF6B5E2E),
    tertiary = Color(0xFF7A3E4E),
    background = Color(0xFFF8FAF9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3E7E4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FCBE3),
    onPrimary = Color(0xFF003544),
    secondary = Color(0xFFD8C982),
    tertiary = Color(0xFFE7A2B5),
    background = Color(0xFF101413),
    surface = Color(0xFF171C1B),
    surfaceVariant = Color(0xFF3F4845),
)

@Composable
fun OmniGraphTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
