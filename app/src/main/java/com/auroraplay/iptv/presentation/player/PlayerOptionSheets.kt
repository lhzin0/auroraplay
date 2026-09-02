package com.auroraplay.iptv.presentation.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.domain.model.AudioStreamVariant
import com.auroraplay.iptv.player.TrackOption

/**
 * One sheet for both audio and subtitles — the two were separate before and
 * the subtitle one had no way to be opened. "Áudio" gathers, in order:
 *  - the dubbed/subtitled sibling streams a provider split apart
 *    ("Dublado" / "Legendado (áudio original)"), and
 *  - the real embedded audio tracks of the current stream.
 * "Legendas" lists "Desativado" plus every embedded subtitle track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioAndSubtitlesSheet(
    audioVariants: List<AudioStreamVariant>,
    currentStreamUrl: String?,
    onSelectVariant: (AudioStreamVariant) -> Unit,
    audioTracks: List<TrackOption>,
    onSelectAudio: (TrackOption) -> Unit,
    subtitleTracks: List<TrackOption>,
    subtitlesEnabled: Boolean,
    onSelectSubtitle: (TrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AuroraColors.BackgroundElevated) {
        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            item { SheetHeader("Áudio") }

            val hasVariants = audioVariants.size >= 2
            if (hasVariants) {
                items(audioVariants) { v ->
                    OptionRow(
                        label = v.label,
                        selected = v.streamUrl == currentStreamUrl,
                        onClick = { onSelectVariant(v); onDismiss() },
                    )
                }
            }
            if (audioTracks.size > 1) {
                if (hasVariants) item { SheetSubHeader("Faixas deste stream") }
                items(audioTracks) { track ->
                    OptionRow(track.label, track.isSelected) { onSelectAudio(track); onDismiss() }
                }
            } else if (!hasVariants) {
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
private fun SheetSubHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = AuroraColors.TextTertiary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
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
