package com.auroraplay.iptv.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * AuroraPlay always defaults to dark mode (per product spec) but the accent
 * color is fully configurable and threaded through the color scheme so every
 * component (buttons, focus rings, progress bars, active nav item) reacts to
 * the user's chosen color automatically.
 */
private fun auroraDarkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color.Black,
    secondary = AuroraColors.AccentDefaultVariant,
    onSecondary = Color.Black,
    background = AuroraColors.BackgroundBase,
    onBackground = AuroraColors.TextPrimary,
    surface = AuroraColors.SurfaceDark,
    onSurface = AuroraColors.TextPrimary,
    surfaceVariant = AuroraColors.SurfaceHigh,
    onSurfaceVariant = AuroraColors.TextSecondary,
    error = AuroraColors.Error,
    outline = AuroraColors.Divider,
)

private fun auroraLightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    background = Color(0xFFF7F7F9),
    surface = Color.White,
)

@Composable
fun AuroraPlayTheme(
    accentColor: Color = AuroraColors.AccentDefault,
    darkTheme: Boolean = true, // app defaults to dark regardless of system, configurable in Settings
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) auroraDarkScheme(accentColor) else auroraLightScheme(accentColor)

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuroraTypography,
        shapes = AuroraShapes,
        content = content
    )
}
