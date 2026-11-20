package com.example.smarttrafficlight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SmartTrafficLightColorScheme = lightColorScheme(

    // Primary
    primary = PrimaryRed,
    onPrimary = SurfaceWhite,

    primaryContainer = PrimaryRedLight,
    onPrimaryContainer = PrimaryRedDark,

    // Secondary
    secondary = TextSecondary,
    onSecondary = SurfaceWhite,

    // Background
    background = BackgroundLight,
    onBackground = TextPrimary,

    // Surface
    surface = SurfaceWhite,
    onSurface = TextPrimary,

    surfaceVariant = SurfaceSoft,
    onSurfaceVariant = TextSecondary,

    // Status
    error = ErrorRed,
    onError = SurfaceWhite,

    // Border
    outline = BorderLight,
    outlineVariant = BorderLight
)

@Composable
fun SmartTrafficLightTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SmartTrafficLightColorScheme,
        typography = AppTypography,
        content = content
    )
}