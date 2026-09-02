package com.auroraplay.iptv.core.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp

/**
 * FrostGlass — Configurações › Interface toggle (default on).
 *
 * When on, the app's floating translucent panels — glass buttons, the bottom
 * navigation bar, the player's ⋮ pop-over — are painted as a frosted graphite
 * pane: a soft top-lit gradient at ~67% opacity instead of the flat wash they
 * use when off. It swaps **only the material**: shape, size, padding, text
 * colour and layout are untouched, so turning it off returns the exact
 * previous look.
 *
 * Deliberately no RenderEffect backdrop blur — that is API 31+ only and would
 * look different across devices; this reads as glass on every Android version
 * and can never interfere with the player's gesture / Cinema layers.
 */
val LocalFrostGlass = staticCompositionLocalOf { true }

/** Alpha 170/255 — the frost body opacity the design calls for. */
private const val FROST_ALPHA = 170f / 255f

/**
 * Paints [shape] as a surface that honours the FrostGlass setting.
 *
 * @param flat the exact colour used when the effect is OFF (today's look).
 * @param tint graphite the frost is tinted with when the effect is ON.
 */
@Composable
fun Modifier.frostSurface(
    shape: Shape,
    flat: Color,
    tint: Color = AuroraColors.BackgroundElevated,
): Modifier {
    val on = LocalFrostGlass.current
    val paint: Brush = if (!on) {
        SolidColor(flat)
    } else {
        Brush.verticalGradient(
            listOf(
                lerp(tint, Color.White, 0.06f).copy(alpha = (FROST_ALPHA + 0.12f).coerceAtMost(1f)),
                tint.copy(alpha = FROST_ALPHA),
            )
        )
    }
    return this.clip(shape).background(paint, shape)
}
