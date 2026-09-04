package com.auroraplay.iptv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.auroraplay.iptv.core.theme.AuroraColors

/**
 * Portrait 2:3 poster card for the "Continuar assistindo" row, matching the
 * Netflix layout the reference shows: poster art, one centred play ring, a
 * thin progress bar pinned to the poster's bottom edge, and an attached
 * info / overflow strip. No title or "continuar de …" caption underneath —
 * the section header already names the row and the artwork carries the title.
 * "Remove from row" lives behind the ⋮ (overflow) action, not a corner ✕.
 */
@Composable
fun ContinueWatchingCard(
    title: String,
    imageUrl: String?,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    width: androidx.compose.ui.unit.Dp = 138.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val visuals = rememberTvFocusVisuals(interactionSource, pressed = pressed, pressedScale = 0.97f, focusedScale = 1.05f)

    var showSheet by remember { mutableStateOf(false) }
    if (showSheet) {
        ContinueWatchingSheet(
            title = title,
            onInfo = onInfo,
            onRemove = onRemove,
            onDismiss = { showSheet = false },
        )
    }

    Column(modifier = modifier.width(width).scale(visuals.scale)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(AuroraColors.SurfaceHigh)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = visuals.ringAlpha), RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Soft bottom scrim so the play ring and progress bar stay legible
            // over a bright poster.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))
                )
            )
            // Netflix-style ring: white outline, faint fill, filled play glyph.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .border(2.dp, Color.White.copy(alpha = 0.95f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }

        // Progress hugs the poster's bottom edge so it reads as the title's
        // own timeline, exactly like the reference.
        ProgressBarThin(
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
            rounded = false,
        )

        // Attached action strip: info + overflow, split by a hairline. The
        // overflow (⋮) opens a bottom sheet of actions, like the reference,
        // instead of firing "remove" straight away.
        if (onInfo != null || onRemove != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                    .background(AuroraColors.SurfaceDark),
            ) {
                CardFooterAction(
                    icon = Icons.Outlined.Info,
                    contentDescription = "Detalhes de $title",
                    onClick = onInfo ?: {},
                    enabled = onInfo != null,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(AuroraColors.Divider)
                )
                CardFooterAction(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "Mais opções",
                    onClick = { showSheet = true },
                    enabled = onInfo != null || onRemove != null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Netflix-style action sheet for a "Continuar assistindo" card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContinueWatchingSheet(
    title: String,
    onInfo: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = AuroraColors.BackgroundElevated,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = AuroraColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = AuroraColors.TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            onInfo?.let {
                SheetRow(Icons.Outlined.Info, "Mais informações") { onDismiss(); it() }
            }
            onRemove?.let {
                SheetRow(Icons.Default.Close, "Remover de Continuar assistindo") { onDismiss(); it() }
            }
        }
    }
}

@Composable
private fun SheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = AuroraColors.TextPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = AuroraColors.TextPrimary)
    }
}

@Composable
private fun CardFooterAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(40.dp)
            // No background here to hide behind, so this can use the bundled
            // helper directly instead of computing scale/ring by hand.
            .tvFocusable(shape = RoundedCornerShape(8.dp), accent = MaterialTheme.colorScheme.primary, interactionSource = interactionSource, enabled = enabled)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) AuroraColors.TextSecondary else AuroraColors.TextTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Compact 16:9 tile for live channels in a horizontal row. The old build
 * reused the 2:3 poster card here, which is why channel rows rendered as
 * tall empty grey boxes — logos are wide, not portrait, and need
 * ContentScale.Fit on a padded surface rather than Crop.
 */
@Composable
fun ChannelTile(
    name: String,
    logoUrl: String?,
    currentProgram: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 150.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val visuals = rememberTvFocusVisuals(interactionSource, pressed = pressed, pressedScale = 0.97f, focusedScale = 1.05f)

    Column(modifier = modifier.width(width).scale(visuals.scale)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(AuroraColors.SurfaceHigh)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = visuals.ringAlpha), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                )
            } else {
                Icon(Icons.Default.LiveTv, contentDescription = null, tint = AuroraColors.TextTertiary, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, color = AuroraColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        currentProgram?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = AuroraColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
