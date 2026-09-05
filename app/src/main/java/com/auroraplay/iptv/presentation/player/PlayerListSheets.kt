package com.auroraplay.iptv.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.presentation.components.rememberTvFocusVisuals
import com.auroraplay.iptv.presentation.components.tvBringIntoViewOnFocus
import com.auroraplay.iptv.presentation.components.tvFocusable

@Composable
fun LiveChannelQuickList(
    channels: List<com.auroraplay.iptv.domain.model.Channel>,
    currentChannelId: String,
    onSelect: (com.auroraplay.iptv.domain.model.Channel) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.42f)
                .fillMaxHeight()
                .background(AuroraColors.BackgroundElevated)
                .padding(top = 16.dp)
                // Swallow taps inside the panel so they don't bubble to the
                // scrim's dismiss handler.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
        ) {
            Text(
                "Canais",
                style = MaterialTheme.typography.titleLarge,
                color = AuroraColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            var query by remember { mutableStateOf("") }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AuroraColors.SurfaceHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = AuroraColors.TextTertiary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Buscar canal", color = AuroraColors.TextTertiary, style = MaterialTheme.typography.bodyMedium)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = AuroraColors.TextPrimary, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Limpar busca",
                        tint = AuroraColors.TextTertiary,
                        modifier = Modifier
                            .size(18.dp)
                            .tvFocusable(shape = CircleShape, accent = MaterialTheme.colorScheme.primary)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { query = "" },
                    )
                }
            }

            val visibleChannels = if (query.isBlank()) {
                channels
            } else {
                channels.filter { it.name.contains(query.trim(), ignoreCase = true) }
            }
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(visibleChannels, key = { it.id }) { channel ->
                    com.auroraplay.iptv.presentation.components.ChannelCard(
                        name = channel.name,
                        logoUrl = channel.logoUrl,
                        currentProgram = channel.currentProgram?.title,
                        selected = channel.id == currentChannelId,
                        onClick = { onSelect(channel) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
    }
}

/** Right-side drawer to jump to any episode of the current series without
 * leaving the player — grouped by season, current episode highlighted. */
@Composable
fun EpisodePickerSheet(
    episodes: List<com.auroraplay.iptv.domain.model.Episode>,
    currentEpisodeId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val currentIndex = episodes.indexOfFirst { it.id == currentEpisodeId }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) runCatching { listState.scrollToItem(currentIndex) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.46f)
                .fillMaxHeight()
                .background(AuroraColors.BackgroundElevated)
                .displayCutoutPadding()
                .navigationBarsPadding()
                .padding(top = 16.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            Text(
                "Episódios",
                style = MaterialTheme.typography.titleLarge,
                color = AuroraColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                var lastSeason = Int.MIN_VALUE
                episodes.forEach { ep ->
                    if (ep.seasonNumber != lastSeason) {
                        lastSeason = ep.seasonNumber
                        item(key = "s${ep.seasonNumber}") {
                            Text(
                                "Temporada ${ep.seasonNumber}",
                                style = MaterialTheme.typography.labelMedium,
                                color = AuroraColors.TextTertiary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
                            )
                        }
                    }
                    item(key = ep.id) {
                        val active = ep.id == currentEpisodeId
                        val interactionSource = remember { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        val visuals = rememberTvFocusVisuals(interactionSource, pressed = pressed, pressedScale = 0.99f, focusedScale = 1f)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .tvBringIntoViewOnFocus()
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    else Color.White.copy(alpha = 0.04f)
                                )
                                .background(Color.White.copy(alpha = visuals.ringAlpha * 0.12f))
                                .clickable(interactionSource = interactionSource, indication = null) { onSelect(ep.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                "E${ep.episodeNumber}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) MaterialTheme.colorScheme.primary else AuroraColors.TextSecondary,
                                modifier = Modifier.width(34.dp),
                            )
                            Text(
                                ep.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (active) AuroraColors.TextPrimary else AuroraColors.TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (active) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Right-side drawer with the full-day schedule for the channel that's
 * playing — opened by the "Programação" action in the live controls. */
@Composable
fun ChannelEpgSheet(
    channelName: String,
    programs: List<com.auroraplay.iptv.domain.model.EpgProgram>,
    onDismiss: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val timeFmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.forLanguageTag("pt-BR")) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.46f)
                .fillMaxHeight()
                .background(AuroraColors.BackgroundElevated)
                .padding(top = 16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Text(
                "Programação",
                style = MaterialTheme.typography.titleLarge,
                color = AuroraColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                channelName,
                style = MaterialTheme.typography.bodySmall,
                color = AuroraColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            Spacer(Modifier.height(8.dp))

            if (programs.isEmpty()) {
                Text(
                    "Sem guia de programação para este canal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuroraColors.TextTertiary,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    items(programs, key = { it.id }) { p ->
                        val isNow = now in p.startMillis until p.endMillis
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        ) {
                            Text(
                                timeFmt.format(java.util.Date(p.startMillis)),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isNow) MaterialTheme.colorScheme.primary else AuroraColors.TextSecondary,
                                modifier = Modifier.width(48.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    p.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isNow) AuroraColors.TextPrimary else AuroraColors.TextSecondary,
                                    fontWeight = if (isNow) androidx.compose.ui.text.font.FontWeight.Bold else null,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (p.description.isNotBlank()) {
                                    Text(
                                        p.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AuroraColors.TextTertiary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (isNow) {
                                Text(
                                    "AGORA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        HorizontalDivider(color = AuroraColors.Divider)
                    }
                }
            }
        }
    }
}
