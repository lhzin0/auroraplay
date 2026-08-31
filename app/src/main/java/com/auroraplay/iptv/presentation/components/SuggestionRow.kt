package com.auroraplay.iptv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.auroraplay.iptv.core.theme.AuroraColors

/** Search result/suggestion row with a true poster treatment. */
@Composable
fun SuggestionRow(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            // No opaque background between the shape and its border here, so
            // the bundled helper (scale + ring + focusable in one go) is safe.
            .tvFocusable(shape = RoundedCornerShape(10.dp), accent = MaterialTheme.colorScheme.primary, interactionSource = interactionSource, focusedScale = 1.02f)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = Spacing.sm),
    ) {
        // Just the poster — the title already sits as the row's own label to
        // the right, so an overlay caption here only repeated the name.
        Box(
            modifier = Modifier
                .width(92.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(AuroraColors.SurfaceHigh),
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = AuroraColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.md))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AuroraColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = AuroraColors.TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
