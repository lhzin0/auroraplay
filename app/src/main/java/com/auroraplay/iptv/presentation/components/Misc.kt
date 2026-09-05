package com.auroraplay.iptv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.toColorInt
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auroraplay.iptv.core.theme.AuroraColors
import coil.compose.AsyncImage

/** Section title + optional "ver tudo" affordance used above every home carousel. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAllClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = AuroraColors.TextPrimary)
        if (onSeeAllClick != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .tvFocusable(shape = RoundedCornerShape(8.dp), accent = MaterialTheme.colorScheme.primary)
                    .clickable { onSeeAllClick() },
            ) {
                Text("Ver tudo", style = MaterialTheme.typography.labelLarge, color = AuroraColors.TextSecondary)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AuroraColors.TextSecondary)
            }
        }
    }
}

@Composable
fun ProfileAvatar(
    emoji: String,
    colorHex: String,
    avatarUri: String? = null,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    onClick: (() -> Unit)? = null,
) {
    val color = runCatching { Color(colorHex.toColorInt()) }.getOrDefault(AuroraColors.AccentDefault)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .then(
                if (onClick != null) {
                    Modifier
                        .tvFocusable(shape = CircleShape, accent = MaterialTheme.colorScheme.primary)
                        .clickable { onClick() }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUri.isNullOrBlank()) {
            AsyncImage(
                model = avatarUri,
                contentDescription = "Foto do perfil",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun ProfileCard(
    name: String,
    emoji: String,
    colorHex: String,
    avatarUri: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    avatarSize: androidx.compose.ui.unit.Dp = 104.dp,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = when {
            pressed -> 0.96f
            focused || selected -> 1.05f
            else -> 1f
        },
        animationSpec = androidx.compose.animation.core.tween(160),
        label = "profileScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // Fixed slot width keeps every card the same size, so a long name
        // can never make one card wider (and its avatar off-grid) than the rest.
        modifier = modifier
            .width(avatarSize + 24.dp)
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickableCompat(
                interactionSource = interactionSource,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 10.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ProfileAvatar(emoji = emoji, colorHex = colorHex, avatarUri = avatarUri, size = avatarSize)
            if (selected || focused) {
                Box(
                    Modifier
                        .size(avatarSize + 8.dp)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // Fixed-height, single-line name slot: this is what stops short names
        // ("nn") and wrapping names ("Adicionar perfil") from sitting at
        // different vertical positions across cards.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) AuroraColors.TextPrimary else AuroraColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** combinedClickable wrapper that tolerates a null long-press handler. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): Modifier = this.combinedClickable(
    interactionSource = interactionSource,
    indication = null,
    onClick = onClick,
    onLongClick = onLongClick,
)
