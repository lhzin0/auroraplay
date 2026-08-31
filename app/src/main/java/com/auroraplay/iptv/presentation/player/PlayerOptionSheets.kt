package com.auroraplay.iptv.presentation.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.player.TrackOption

/** Bottom sheet listing selectable audio tracks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrackSheet(
    tracks: List<TrackOption>,
    onSelect: (TrackOption) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AuroraColors.BackgroundElevated) {
        Text("Áudio", style = MaterialTheme.typography.titleLarge, color = AuroraColors.TextPrimary, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
        if (tracks.isEmpty()) {
            Text("Somente uma faixa de áudio disponível.", color = AuroraColors.TextSecondary, modifier = Modifier.padding(20.dp))
        }
        LazyColumn {
            items(tracks) { track ->
                OptionRow(label = track.label, selected = track.isSelected, onClick = { onSelect(track); onDismiss() })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Bottom sheet listing selectable subtitle tracks, plus a "Desativado" option. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleTrackSheet(
    tracks: List<TrackOption>,
    subtitlesEnabled: Boolean,
    onSelect: (TrackOption) -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AuroraColors.BackgroundElevated) {
        Text("Legendas", style = MaterialTheme.typography.titleLarge, color = AuroraColors.TextPrimary, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
        LazyColumn {
            item {
                OptionRow(label = "Desativado", selected = !subtitlesEnabled, onClick = { onDisable(); onDismiss() })
            }
            items(tracks) { track ->
                OptionRow(label = track.label, selected = subtitlesEnabled && track.isSelected, onClick = { onSelect(track); onDismiss() })
            }
        }
        if (tracks.isEmpty()) {
            Row(Modifier.padding(20.dp)) {
                Icon(Icons.Default.Subtitles, contentDescription = null, tint = AuroraColors.TextTertiary)
                Spacer(Modifier.width(8.dp))
                Text("Este conteúdo não possui legendas incorporadas.", color = AuroraColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Bottom sheet for playback speed — only meaningful for local (non-Cast) VOD playback. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedSheet(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AuroraColors.BackgroundElevated) {
        Text("Velocidade de reprodução", style = MaterialTheme.typography.titleLarge, color = AuroraColors.TextPrimary, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
        LazyColumn {
            items(speedOptions) { speed ->
                OptionRow(label = if (speed == 1f) "Normal" else "${speed}x", selected = speed == currentSpeed, onClick = { onSelect(speed); onDismiss() })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (selected) MaterialTheme.colorScheme.primary else AuroraColors.TextPrimary, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}
