package com.auroraplay.iptv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.auroraplay.iptv.core.theme.AuroraColors

/**
 * Compact "Baixar" affordance reused on movie details and episode rows.
 * Three states: not downloaded (tap to start), downloading (tap to cancel,
 * shows progress ring), downloaded (tap to remove).
 */
@Composable
fun DownloadRow(
    isDownloaded: Boolean,
    isDownloading: Boolean,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val visuals = rememberTvFocusVisuals(interactionSource, pressed = pressed, pressedScale = 0.99f, focusedScale = 1f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .tvBringIntoViewOnFocus()
            .clip(RoundedCornerShape(10.dp))
            .background(AuroraColors.SurfaceHigh)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = visuals.ringAlpha * 0.12f))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        when {
            isDownloaded -> {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AuroraColors.Success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Baixado — toque para remover", style = MaterialTheme.typography.labelLarge, color = AuroraColors.TextSecondary)
            }
            isDownloading -> {
                Box(Modifier.size(18.dp)) {
                    CircularProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("Baixando ${(progress * 100).toInt()}% — toque para cancelar", style = MaterialTheme.typography.labelLarge, color = AuroraColors.TextSecondary)
            }
            else -> {
                Icon(Icons.Default.Download, contentDescription = null, tint = AuroraColors.TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Baixar para assistir offline", style = MaterialTheme.typography.labelLarge, color = AuroraColors.TextSecondary)
            }
        }
    }
}
