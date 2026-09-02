package com.auroraplay.iptv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.theme.frostSurface

/** Primary call-to-action, filled with the accent color. Used for "Assistir". */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.PlayArrow,
    fullWidth: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // White reads clearly against the button's own accent-colored fill,
    // where a same-color ring would disappear — unlike GlassButton, which
    // sits on a dark/translucent surface and wants the accent ring instead.
    val visuals = rememberTvFocusVisuals(interactionSource, pressed = pressed)

    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .scale(visuals.scale)
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            // Frosted accent glass when FrostGlass is on; flat accent fill when off.
            .frostSurface(shape, flat = MaterialTheme.colorScheme.primary, tint = MaterialTheme.colorScheme.primary)
            .border(2.dp, Color.White.copy(alpha = visuals.ringAlpha), shape)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = Color.Black,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/** Secondary "glass" button — translucent surface, used for "Favoritar" and similar actions. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    /** When non-null (0f..1f), replaces [icon] with an animated circular
     * progress ring — the same "pie fills up" affordance every streaming
     * app uses for an in-progress download, instead of a static icon that
     * gives no sense that anything is actually happening. */
    downloadProgress: Float? = null,
    /** True when downloading but no percentage can be computed (the source
     * never sent a Content-Length — common on Xtream/IPTV VOD streams) —
     * shows a spinning, indeterminate ring instead of [downloadProgress],
     * which would otherwise have to fake a permanent, stuck "0%". Takes
     * priority over [downloadProgress] when both are set. */
    downloadIndeterminate: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val visuals = rememberTvFocusVisuals(interactionSource, pressed = pressed)

    val contentColor = if (selected) MaterialTheme.colorScheme.primary else AuroraColors.TextPrimary

    // "selected" keeps its flat accent wash; the neutral state is the glass
    // surface that follows the FrostGlass setting.
    val surface = if (selected) {
        Modifier.clip(shape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
    } else {
        Modifier.frostSurface(shape, flat = AuroraColors.SurfaceGlass)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .scale(visuals.scale)
            .then(surface)
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = visuals.ringAlpha), shape)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = if (text.isBlank()) 13.dp else 16.dp, vertical = 13.dp)
    ) {
        if (downloadIndeterminate) {
            androidx.compose.material3.CircularProgressIndicator(
                color = contentColor,
                trackColor = contentColor.copy(alpha = 0.25f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(iconSize),
            )
            if (text.isNotBlank()) Spacer(Modifier.width(8.dp))
        } else if (downloadProgress != null) {
            val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                targetValue = downloadProgress.coerceIn(0f, 1f),
                label = "downloadProgressRing",
            )
            androidx.compose.material3.CircularProgressIndicator(
                progress = { animatedProgress },
                color = contentColor,
                trackColor = contentColor.copy(alpha = 0.25f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(iconSize),
            )
            if (text.isNotBlank()) Spacer(Modifier.width(8.dp))
        } else {
            icon?.let {
                Icon(it, contentDescription = text.ifBlank { null }, tint = contentColor, modifier = Modifier.size(iconSize))
                if (text.isNotBlank()) Spacer(Modifier.width(8.dp))
            }
        }
        if (text.isNotBlank()) {
            Text(
                text,
                color = contentColor,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/** Small pill-shaped filter chip used for category selection ("CategoryChip"). */
@Composable
fun CategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 18.dp,
) {
    val chipInteraction = remember { MutableInteractionSource() }
    val chipFocused by chipInteraction.collectIsFocusedAsState()
    val chipShape = RoundedCornerShape(100.dp)
    // Selected keeps its flat accent fill; the neutral pill is a glass surface
    // that follows the FrostGlass setting.
    val chipSurface = if (selected) {
        Modifier.clip(chipShape).background(MaterialTheme.colorScheme.primary)
    } else {
        Modifier.frostSurface(chipShape, flat = AuroraColors.SurfaceHigh)
    }

    // Box with a fixed min height and centred content: relying on the Text's
    // own padding left the label sitting slightly high inside the pill,
    // because font ascent/descent metrics aren't symmetric.
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 36.dp)
            .then(chipSurface)
            .border(
                width = if (chipFocused) 2.dp else 0.dp,
                color = if (chipFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(100.dp),
            )
            .focusable(interactionSource = chipInteraction)
            .clickable(interactionSource = chipInteraction, indication = null) { onClick() }
            .padding(horizontal = horizontalPadding),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.Black else AuroraColors.TextSecondary,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
