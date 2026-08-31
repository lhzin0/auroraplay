package com.auroraplay.iptv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.theme.AuroraRadius

/** Poster-style card used for Movies and Series (2:3 aspect). */
@Composable
fun MovieCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    width: androidx.compose.ui.unit.Dp? = 128.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val visuals = rememberTvFocusVisuals(interactionSource, pressed = pressed, pressedScale = 0.97f, focusedScale = 1.06f)
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .scale(visuals.scale)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(AuroraRadius.Card))
            // A visible ring is what actually makes D-pad focus legible from
            // across a room — the scale bump alone is too subtle to notice
            // on a large screen, which is why a remote used to feel "stuck"
            // with nothing visibly highlighted.
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = visuals.ringAlpha), RoundedCornerShape(AuroraRadius.Card))
            // focusable() makes the card reachable by D-pad on Android TV;
            // the shared interactionSource drives the same scale/ring used
            // for press states, so touch and remote feel identical.
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(AuroraRadius.Card))
                .background(AuroraColors.SurfaceHigh)
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    // Poster artwork should fill the card; the bottom scrim
                    // protects the title even when the artwork itself is white.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(title.take(1), style = MaterialTheme.typography.headlineLarge, color = AuroraColors.TextTertiary)
                }
            }

            // Persistent lower scrim creates a comfortable reading zone for
            // white posters and keeps the media name inside its card.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(76.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f))
                        )
                    )
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 9.dp),
            )

            if (progress != null && progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    ProgressBarThin(progress)
                }
            }
        }
    }
}

/** Landscape-style card used for Live TV channels. */
@Composable
fun ChannelCard(
    name: String,
    logoUrl: String?,
    currentProgram: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val visuals = rememberTvFocusVisuals(interactionSource, pressed = pressed, pressedScale = 0.97f, focusedScale = 1.06f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .scale(visuals.scale)
            .clip(RoundedCornerShape(AuroraRadius.Card))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else AuroraColors.SurfaceDark)
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = visuals.ringAlpha), RoundedCornerShape(AuroraRadius.Card))
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AuroraColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (logoUrl != null) {
                AsyncImage(model = logoUrl, contentDescription = name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
            } else {
                androidx.compose.material3.Icon(Icons.Default.LiveTv, contentDescription = null, tint = AuroraColors.TextTertiary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = AuroraColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (currentProgram != null) {
                Spacer(Modifier.height(2.dp))
                Text(currentProgram, style = MaterialTheme.typography.bodySmall, color = AuroraColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected) {
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(7.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        onToggleFavorite?.let { toggle ->
            androidx.compose.material3.IconButton(onClick = toggle, modifier = Modifier.size(36.dp)) {
                androidx.compose.material3.Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favoritar",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else AuroraColors.TextTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Thin rounded progress track, used inside cards and continue-watching rows. */
@Composable
fun ProgressBarThin(
    progress: Float,
    modifier: Modifier = Modifier,
    /** Pill shape suits a bar floating inside a poster; a square one suits a
     * bar sitting flush against the edge of a still. */
    rounded: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(if (rounded) 100.dp else 0.dp))
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(100.dp))
                .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
        )
    }
}
