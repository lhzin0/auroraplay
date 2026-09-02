package com.auroraplay.iptv.core.theme

import android.os.Build
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
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * FrostGlass — Configurações › Interface toggle (default on).
 *
 * When on, the app's floating translucent panels — glass buttons, the bottom
 * navigation bar, the player's ⋮ pop-over, list/section cards — are painted as
 * a frosted graphite pane instead of a flat wash. It swaps **only the
 * material**: shape, size, padding, text colour and layout are untouched, so
 * turning it off returns the exact previous look.
 *
 * Two render paths:
 *  - given a [HazeState] on API 31+, a real backdrop blur of whatever that
 *    state is capturing (the reference "glass" look — bottom nav over the
 *    scrolling grid, the ⋮ panel over the video), at the low alpha-170 body
 *    so the blurred content stays visible;
 *  - otherwise a top-lit translucent graphite gradient. This is used for
 *    inline surfaces (cards, chips, the search field) that sit on the near-
 *    black page with nothing behind them, so it's kept substantial enough to
 *    still read as a lifted card. It's also the API < 31 fallback.
 */
val LocalFrostGlass = staticCompositionLocalOf { true }

/** Alpha 170/255 — the frost body opacity for the backdrop-blur path. */
private const val FROST_ALPHA = 170f / 255f

private val canBackdropBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Paints [shape] as a surface that honours the FrostGlass setting.
 *
 * @param flat the exact colour used when the effect is OFF (today's look).
 * @param tint graphite the frost is tinted with when the effect is ON.
 * @param haze when non-null (and the device supports it), the surface becomes
 *   a real backdrop blur of the content marked `Modifier.hazeSource(haze)`.
 */
@Composable
fun Modifier.frostSurface(
    shape: Shape,
    flat: Color,
    tint: Color = AuroraColors.SurfaceHigh,
    haze: HazeState? = null,
): Modifier {
    val on = LocalFrostGlass.current
    if (!on) return this.clip(shape).background(flat, shape)

    if (haze != null && canBackdropBlur) {
        return this
            .clip(shape)
            .hazeEffect(state = haze) {
                backgroundColor = tint
                tints = listOf(HazeTint(tint.copy(alpha = FROST_ALPHA)))
                blurRadius = 24.dp
                noiseFactor = 0.04f
            }
    }

    // Inline (no backdrop): keep it substantial — a barely-there wash on a
    // near-black page just makes cards vanish. Top edge catches a little light.
    val gradient = Brush.verticalGradient(
        listOf(
            lerp(tint, Color.White, 0.10f).copy(alpha = 0.95f),
            tint.copy(alpha = 0.76f),
        )
    )
    return this.clip(shape).background(gradient, shape)
}
