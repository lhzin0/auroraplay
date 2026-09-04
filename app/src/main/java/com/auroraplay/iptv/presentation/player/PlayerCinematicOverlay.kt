package com.auroraplay.iptv.presentation.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * The frame, heavily blurred and oversized, painted into the real letterbox
 * bars that FIT leaves — a soft, out-of-focus continuation of the scene, the
 * way YouTube's ambient mode works. Two layers: [prev] stays fully opaque
 * while [cur] fades in over it ([curAlpha]), so there's no opacity dip and
 * therefore no flicker. Bars that don't exist aren't drawn.
 */
@Composable
fun CinematicBarsOverlay(
    prev: android.graphics.Bitmap?,
    cur: android.graphics.Bitmap,
    curAlpha: Float,
    videoWidth: Int,
    videoHeight: Int,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val videoAspect = (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(0.2f, 5f)
        val renderedW = minOf(maxWidth, maxHeight * videoAspect)
        val renderedH = minOf(maxHeight, maxWidth / videoAspect)
        val barW = ((maxWidth - renderedW) / 2f).coerceAtLeast(0.dp)
        val barH = ((maxHeight - renderedH) / 2f).coerceAtLeast(0.dp)

        if (barH > 2.dp) {
            CinematicBarImage(prev, cur, curAlpha, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(barH), Alignment.TopCenter)
            CinematicBarImage(prev, cur, curAlpha, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(barH), Alignment.BottomCenter)
        }
        if (barW > 2.dp) {
            CinematicBarImage(prev, cur, curAlpha, Modifier.align(Alignment.CenterStart).fillMaxHeight().width(barW), Alignment.CenterStart)
            CinematicBarImage(prev, cur, curAlpha, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(barW), Alignment.CenterEnd)
        }
    }
}

@Composable
private fun CinematicBarImage(
    prev: android.graphics.Bitmap?,
    cur: android.graphics.Bitmap,
    curAlpha: Float,
    modifier: Modifier,
    align: Alignment,
) {
    Box(modifier.clipToBounds()) {
        prev?.let { CinematicLayer(it, align, alpha = 1f) }
        CinematicLayer(cur, align, alpha = curAlpha)
        // Keep the video the focus.
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.25f)))
    }
}

@Composable
private fun CinematicLayer(bitmap: android.graphics.Bitmap, align: Alignment, alpha: Float) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = align,
        // The settled layer is fully opaque and the incoming one fades 0->1
        // on top of it: a true linear crossfade with constant total
        // brightness. The old 0.9 multiplier left the settled layer
        // translucent, so the two stacked layers summed *brighter* mid-fade
        // and snapped back each cycle — that was the flicker. Darkening is
        // handled by the 0.25 black scrim in CinematicBarImage.
        alpha = alpha.coerceIn(0f, 1f),
        modifier = Modifier
            .fillMaxSize()
            .scale(1.35f)                       // hide the blurred edges
            .blur(44.dp, BlurredEdgeTreatment.Unbounded),
    )
}
