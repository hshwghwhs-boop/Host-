package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = CyberNeonBlue,
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF004D63),
    onSecondaryContainer = Color(0xFFBEE9FF),
    tertiary = OnlineGreen,
    onTertiary = Color(0xFF003920),
    background = CyberDeepNavy,
    onBackground = TextPrimary,
    surface = CyberCardNavy,
    onSurface = TextPrimary,
    surfaceVariant = CyberCardElevated,
    onSurfaceVariant = TextSecondary,
    outline = CyberBorder,
    error = OfflineRed
)

private val LightColorScheme = DarkColorScheme // BotHost is built as an immersive dark operations center

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Preserve high-contrast server aesthetic
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
