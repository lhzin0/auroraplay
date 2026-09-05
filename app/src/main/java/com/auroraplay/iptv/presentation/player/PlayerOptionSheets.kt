package com.auroraplay.iptv.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.player.TrackOption
import com.auroraplay.iptv.presentation.components.rememberTvFocusVisuals
import com.auroraplay.iptv.presentation.components.tvBringIntoViewOnFocus

/**
 * One sheet for both audio and subtitles — the two were separate before and
 * the subtitle one had no way to be opened. "Áudio" lists the stream's
 * embedded audio tracks; "Legendas" lists "Desativado" plus every embedded
 * subtitle track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioAndSubtitlesSheet(
    audioTracks: List<TrackOption>,
    onSelectAudio: (TrackOption) -> Unit,
    subtitleTracks: List<TrackOption>,
    subtitlesEnabled: Boolean,
    onSelectSubtitle: (TrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    /** Opens the system file picker for a local .srt — Xtream never offers
     * one itself, only whatever's muxed into the stream. Null hides the row
     * entirely (e.g. while casting, where a local file can't reach the
     * receiver). */
    onLoadSubtitleFile: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AuroraColors.BackgroundElevated) {
        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            item { SheetHeader("Áudio") }

            if (audioTracks.size > 1) {
                items(audioTracks) { track ->
                    OptionRow(track.label, track.isSelected) { onSelectAudio(track); onDismiss() }
                }
            } else {
                item { SheetNote("Somente uma faixa de áudio disponível.") }
            }

            item { SheetHeader("Legendas") }
            item { OptionRow("Desativado", !subtitlesEnabled) { onDisableSubtitles(); onDismiss() } }
            items(subtitleTracks) { track ->
                OptionRow(track.label, subtitlesEnabled && track.isSelected) { onSelectSubtitle(track); onDismiss() }
            }
            if (subtitleTracks.isEmpty()) {
                item { SheetNote("Este conteúdo não possui legendas incorporadas.") }
            }
            if (onLoadSubtitleFile != null) {
                item {
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val visuals = rememberTvFocusVisuals(interactionSource, pressed = pressed, pressedScale = 0.99f, focusedScale = 1f)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .tvBringIntoViewOnFocus()
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = visuals.ringAlpha * 0.12f))
                            .clickable(interactionSource = interactionSource, indication = null) { onLoadSubtitleFile() }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Carregar legenda (.srt)", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = AuroraColors.TextPrimary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun SheetNote(text: String) {
    Text(text, color = AuroraColors.TextSecondary, modifier = Modifier.padding(20.dp))
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val visuals = rememberTvFocusVisuals(interactionSource, pressed = pressed, pressedScale = 0.99f, focusedScale = 1f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .tvBringIntoViewOnFocus()
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = visuals.ringAlpha * 0.12f))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (selected) MaterialTheme.colorScheme.primary else AuroraColors.TextPrimary, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}
