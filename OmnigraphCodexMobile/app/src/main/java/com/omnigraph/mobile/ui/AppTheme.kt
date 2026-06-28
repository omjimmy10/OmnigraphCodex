package com.omnigraph.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val OmniBackground = Color(0xFF020509)
internal val OmniPanel = Color(0xFF07101A)
internal val OmniPanelSoft = Color(0xFF0B1522)
internal val OmniPanelMuted = Color(0xFF101925)
internal val OmniGreen = Color(0xFF26FF7E)
internal val OmniBlue = Color(0xFF448FFF)
internal val OmniText = Color(0xFFF7FAFF)
internal val OmniMuted = Color(0xFF9CA8B8)
internal val OmniDivider = Color(0xFF253140)

private val OmniColors = darkColorScheme(
    primary = OmniGreen,
    onPrimary = Color(0xFF00150A),
    secondary = OmniBlue,
    onSecondary = Color(0xFF001329),
    tertiary = Color(0xFF7AC7FF),
    background = OmniBackground,
    onBackground = OmniText,
    surface = OmniPanel,
    onSurface = OmniText,
    surfaceVariant = OmniPanelMuted,
    onSurfaceVariant = OmniMuted,
    outline = OmniDivider,
)

@Composable
fun OmniGraphTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OmniColors,
        content = content,
    )
}
