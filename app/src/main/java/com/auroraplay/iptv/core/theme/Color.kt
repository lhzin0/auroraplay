package com.auroraplay.iptv.core.theme

import androidx.compose.ui.graphics.Color

/**
 * AuroraPlay design tokens.
 *
 * Own visual identity: near-black graphite surfaces, elevated contrast,
 * and a single configurable accent color (default: violet/electric-blue
 * blend, distinct from the red/blue accents of mainstream streaming apps).
 */
object AuroraColors {
    // Backgrounds
    val BackgroundBase = Color(0xFF07070A)
    val BackgroundElevated = Color(0xFF101014)
    val SurfaceDark = Color(0xFF17171C)
    val SurfaceHigh = Color(0xFF1E1E24)
    val SurfaceGlass = Color(0x33FFFFFF)

    // Text
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFA6A6AD)
    val TextTertiary = Color(0xFF6E6E76)

    // Default accent (user-configurable at runtime, see AccentColor palette below)
    val AccentDefault = Color(0xFF7C5CFF)
    val AccentDefaultVariant = Color(0xFF5CE1FF)

    val Success = Color(0xFF3DDC84)
    val Warning = Color(0xFFFFC24B)
    val Error = Color(0xFFFF5C5C)

    val Divider = Color(0xFF232329)

    /** Curated accent choices exposed in Settings > Interface > Cor de destaque */
    val AccentPalette = listOf(
        "Violeta" to Color(0xFF7C5CFF),
        "Ciano" to Color(0xFF32D8E0),
        "Esmeralda" to Color(0xFF2ED47A),
        "Âmbar" to Color(0xFFFFA53D),
        "Rosa" to Color(0xFFFF4FA3),
        "Vermelho" to Color(0xFFFF4B4B),
    )
}
