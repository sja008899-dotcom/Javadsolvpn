package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JavadDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF001E2E),
    primaryContainer = Color(0xFF004D68),
    onPrimaryContainer = Color(0xFFBCE9FF),
    secondary = CyberBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0A306E),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = CyberViolet,
    onTertiary = Color.White,
    background = CyberDarkBackground,
    onBackground = TextPrimary,
    surface = CyberDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberDarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CyberBorder,
    error = CyberErrorRed,
    onError = Color.White
)

private val JavadLightColorScheme = lightColorScheme(
    primary = Color(0xFF006884),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8EAFF),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = Color(0xFF1E5EC7),
    onSecondary = Color.White,
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF131B29),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF131B29),
    surfaceVariant = Color(0xFFE2E8F4),
    onSurfaceVariant = Color(0xFF455A64),
    outline = Color(0xFFB0BEC5),
    error = CyberErrorRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to stunning cyber dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) JavadDarkColorScheme else JavadLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
