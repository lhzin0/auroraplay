package com.auroraplay.iptv.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * D-pad focus treatment shared by every interactive surface.
 *
 * On a TV the pointer doesn't exist, so focus *is* the cursor: it has to be
 * unmistakable without being loud. This combines a small scale-up with a
 * 2dp accent ring, which reads clearly from across a room while staying
 * subtle enough that the same modifier can be used on touch devices (where
 * focus simply never triggers).
 *
 * Applying this via one modifier rather than per-screen styling is what keeps
 * focus from silently disappearing on some elements — the usual cause of a
 * remote getting "stuck" with nothing highlighted.
 *
 * Pass an existing [interactionSource] (and its [pressed] state) when the
 * caller already tracks one for its own press/ripple handling — this draws
 * the ring off that same source and calls the one `.focusable()` for it,
 * instead of every card/button hand-rolling an identical copy of this logic
 * with its own second interaction source.
 */
/**
 * True only on Android TV / leanback. On a touch device focus never triggers,
 * so every card's scale + ring `animateFloatAsState` pair would sit idle — on a
 * list screen that's dozens of live Animatables for nothing. Gate on this to
 * skip that machinery entirely off-TV.
 */
val LocalIsTvDevice = staticCompositionLocalOf { false }

@Composable
fun Modifier.tvFocusable(
    shape: Shape,
    accent: Color,
    interactionSource: MutableInteractionSource? = null,
    pressed: Boolean = false,
    pressedScale: Float = 0.96f,
    focusedScale: Float = 1.04f,
    ringWidth: androidx.compose.ui.unit.Dp = 2.dp,
    enabled: Boolean = true,
): Modifier {
    // Off-TV: no focus, no ring, no animation — just keep it focusable for a11y.
    if (!LocalIsTvDevice.current) {
        val src = interactionSource ?: remember { MutableInteractionSource() }
        return this.focusable(enabled = enabled, interactionSource = src)
    }

    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()

    val targetScale = when {
        pressed -> pressedScale
        focused -> focusedScale
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(150),
        label = "tvFocusScale",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(150),
        label = "tvFocusRing",
    )

    return this
        .scale(scale)
        .border(width = ringWidth, color = accent.copy(alpha = ringAlpha), shape = shape)
        .focusable(enabled = enabled, interactionSource = source)
}

/**
 * Just the animated numbers behind [tvFocusable] (scale + ring opacity),
 * without bundling them into one fixed modifier position.
 *
 * Buttons and cards need an opaque `.background()` sitting *between* where
 * the scale has to go (outermost, so the whole shape scales as one piece)
 * and where the border has to go (drawn last, or the background paints over
 * and hides it) — so their two effects can't share a single insertion point
 * in the modifier chain the way [tvFocusable] assumes. This gives every one
 * of those call sites the same shared animation spec without duplicating six
 * copies of the same two `animateFloatAsState` calls.
 */
@Composable
fun rememberTvFocusVisuals(
    interactionSource: MutableInteractionSource,
    pressed: Boolean = false,
    pressedScale: Float = 0.96f,
    focusedScale: Float = 1.04f,
): TvFocusVisuals {
    // Off-TV the ring never shows and the scale never leaves 1f, so skip the
    // two animateFloatAsState this would otherwise spin up per call site.
    if (!LocalIsTvDevice.current) return TvFocusVisuals(1f, 0f)

    val focused by interactionSource.collectIsFocusedAsState()
    val targetScale = when {
        pressed -> pressedScale
        focused -> focusedScale
        else -> 1f
    }
    val scale by animateFloatAsState(targetScale, tween(150), label = "tvFocusScale")
    val ringAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(150), label = "tvFocusRing")
    return TvFocusVisuals(scale, ringAlpha)
}

data class TvFocusVisuals(val scale: Float, val ringAlpha: Float)
