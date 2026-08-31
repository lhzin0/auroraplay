package com.auroraplay.iptv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.auroraplay.iptv.core.theme.AuroraColors

/**
 * Metadata line for a detail screen: year, age rating, duration and quality,
 * where the rating and quality render as small boxed badges and the plain
 * values render as text. Only non-blank entries appear, so a title missing a
 * rating simply shows one fewer badge instead of a stray separator.
 */
@Composable
fun MetadataBadgeRow(
    year: String?,
    ageRating: String?,
    duration: String?,
    quality: String?,
    genre: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = modifier,
    ) {
        year?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = AuroraColors.TextSecondary)
        }
        ageRating?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = AuroraColors.TextPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        duration?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = AuroraColors.TextSecondary)
        }
        genre?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = AuroraColors.TextSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        quality?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = AuroraColors.TextTertiary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, AuroraColors.Divider, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * Resume progress indicator with the remaining-time label to its right,
 * shown on a detail screen when the title has been partly watched.
 */
@Composable
fun RemainingTimeRow(
    progress: Float,
    remainingLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        ProgressBarThin(
            progress = progress,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = remainingLabel,
            style = MaterialTheme.typography.bodySmall,
            color = AuroraColors.TextSecondary,
            textAlign = TextAlign.End,
        )
    }
}

/** A single icon-over-label action, as used in the detail screen action row. */
@Composable
fun IconTextAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = AuroraColors.TextPrimary,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AuroraColors.TextSecondary,
            maxLines = 1,
        )
    }
}

/** Favorite / share style action row placed under the synopsis. */
@Composable
fun DetailActionRow(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    extraActions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier,
    ) {
        IconTextAction(
            icon = if (isFavorite) Icons.Default.Check else Icons.Default.Add,
            label = if (isFavorite) "Na lista" else "Minha lista",
            onClick = onToggleFavorite,
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else AuroraColors.TextPrimary,
        )
        extraActions?.invoke(this)
    }
}
