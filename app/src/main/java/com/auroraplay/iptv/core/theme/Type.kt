package com.auroraplay.iptv.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

private val AuroraFont = FontFamily.Default

/**
 * Trims the extra leading Compose adds above/below a line box. Without this,
 * a single line of text sits slightly high inside a fixed-height container,
 * which is what made labels look vertically off-centre next to their icons
 * and avatars even when the container was correctly centred.
 */
private val TrimmedLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun aurora(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = AuroraFont,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeightStyle = TrimmedLineHeight,
)

/**
 * One explicit hierarchy, used everywhere:
 *
 *  displayLarge/Medium → hero titles only
 *  headlineLarge       → page title ("Filmes", "Quem está assistindo?")
 *  headlineMedium      → detail-screen content title
 *  titleLarge          → section/rail title ("Continuar assistindo")
 *  titleMedium         → content title on a card, list row, button
 *  bodyLarge/Medium    → synopsis and descriptive prose
 *  bodySmall           → secondary info under a title
 *  labelLarge/Medium   → interactive labels, chips
 *  labelSmall          → metadata, nav labels, eyebrows
 *
 * Sizes step deliberately (11/12/14/16/18/22/26/32/40) instead of drifting a
 * point at a time, and every step carries its own line height so text keeps
 * a consistent rhythm on both a phone and a TV viewed from across a room.
 */
val AuroraTypography = Typography(
    displayLarge = aurora(40, 46, FontWeight.Bold, -0.5),
    displayMedium = aurora(32, 38, FontWeight.Bold, -0.3),
    displaySmall = aurora(28, 34, FontWeight.Bold, -0.2),

    headlineLarge = aurora(26, 32, FontWeight.SemiBold, -0.2),
    headlineMedium = aurora(22, 28, FontWeight.SemiBold),
    headlineSmall = aurora(20, 26, FontWeight.SemiBold),

    titleLarge = aurora(18, 24, FontWeight.SemiBold),
    titleMedium = aurora(16, 22, FontWeight.Medium),
    titleSmall = aurora(14, 20, FontWeight.Medium),

    // Synopsis-grade prose: slightly looser leading so long paragraphs on the
    // detail screen stay readable instead of feeling compressed.
    bodyLarge = aurora(16, 24, FontWeight.Normal),
    bodyMedium = aurora(14, 21, FontWeight.Normal),
    bodySmall = aurora(12, 18, FontWeight.Normal),

    labelLarge = aurora(14, 18, FontWeight.Medium),
    labelMedium = aurora(12, 16, FontWeight.Medium),
    labelSmall = aurora(11, 14, FontWeight.Medium, 0.2),
)
