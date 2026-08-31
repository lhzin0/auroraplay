package com.auroraplay.iptv.presentation.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import coil.compose.AsyncImage
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.util.toFileSizeLabel
import com.auroraplay.iptv.player.download.DownloadState
import com.auroraplay.iptv.presentation.components.BackButton
import com.auroraplay.iptv.presentation.components.EmptyState
import com.auroraplay.iptv.presentation.components.Spacing

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlay: (contentType: String, contentId: String) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize().background(AuroraColors.BackgroundBase).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            BackButton(onClick = onBack)
            Spacer(Modifier.width(Spacing.sm))
            Text("Downloads", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuroraColors.TextPrimary)
        }

        if (state.groups.isEmpty() && !state.isLoading) {
            EmptyState(message = "Nada baixado ainda. Baixe um filme ou episódio para assistir offline.")
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
                items(state.groups, key = { it.key }) { group ->
                    if (group.isSeries) {
                        SeriesDownloadCard(
                            group = group,
                            onPlayEpisode = { onPlay(it.playbackContentType, it.playbackId) },
                            onRemoveEpisode = { viewModel.remove(it.contentId) },
                            onRemoveAll = { viewModel.removeGroup(group) },
                        )
                    } else {
                        MovieDownloadCard(
                            group = group,
                            onPlay = { item -> onPlay(item.playbackContentType, item.playbackId) },
                            onRemove = { viewModel.removeGroup(group) },
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                }
                item { Spacer(Modifier.height(Spacing.navBarClearance)) }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Movie card — one row, its own poster                                       */
/* -------------------------------------------------------------------------- */

@Composable
private fun MovieDownloadCard(
    group: DownloadGroup,
    onPlay: (DownloadState) -> Unit,
    onRemove: () -> Unit,
) {
    val item = group.items.first()
    val isCompleted = item.status == Download.STATE_COMPLETED

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AuroraColors.SurfaceDark)
            .clickable(enabled = isCompleted) { onPlay(item) }
            .padding(Spacing.md),
    ) {
        DownloadPoster(group.posterUrl, fallbackIcon = Icons.Default.Movie)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                group.title,
                style = MaterialTheme.typography.titleMedium,
                color = AuroraColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(statusLine(item), style = MaterialTheme.typography.bodySmall, color = statusColor(item))
        }
        if (isCompleted) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AuroraColors.Success, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remover download", tint = AuroraColors.TextTertiary)
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Series card — poster + episode count, expands to a per-episode list       */
/* -------------------------------------------------------------------------- */

@Composable
private fun SeriesDownloadCard(
    group: DownloadGroup,
    onPlayEpisode: (DownloadState) -> Unit,
    onRemoveEpisode: (DownloadState) -> Unit,
    onRemoveAll: () -> Unit,
) {
    var expanded by rememberSaveable(group.key) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AuroraColors.SurfaceDark),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(Spacing.md),
        ) {
            DownloadPoster(group.posterUrl, fallbackIcon = Icons.Default.Tv)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    group.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AuroraColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(seriesSubtitle(group), style = MaterialTheme.typography.bodySmall, color = AuroraColors.TextTertiary)
            }
            if (group.allCompleted) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AuroraColors.Success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Recolher" else "Expandir",
                tint = AuroraColors.TextSecondary,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = Spacing.md, end = Spacing.xs, bottom = Spacing.sm)) {
                group.items.forEach { episode ->
                    EpisodeRow(
                        item = episode,
                        onPlay = { onPlayEpisode(episode) },
                        onRemove = { onRemoveEpisode(episode) },
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onRemoveAll)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = AuroraColors.Error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Remover todos", style = MaterialTheme.typography.labelLarge, color = AuroraColors.Error)
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(item: DownloadState, onPlay: () -> Unit, onRemove: () -> Unit) {
    val isCompleted = item.status == Download.STATE_COMPLETED
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = isCompleted, onClick = onPlay)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
    ) {
        StatusBadge(item, size = 30.dp)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                item.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = AuroraColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(statusLine(item), style = MaterialTheme.typography.bodySmall, color = statusColor(item))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remover episódio", tint = AuroraColors.TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Shared pieces                                                              */
/* -------------------------------------------------------------------------- */

@Composable
private fun DownloadPoster(url: String?, fallbackIcon: ImageVector) {
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AuroraColors.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(fallbackIcon, contentDescription = null, tint = AuroraColors.TextTertiary, modifier = Modifier.size(22.dp))
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun StatusBadge(item: DownloadState, size: androidx.compose.ui.unit.Dp) {
    val isDownloading = item.status == Download.STATE_DOWNLOADING
    val isCompleted = item.status == Download.STATE_COMPLETED
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(AuroraColors.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        val inner = size * 0.62f
        when {
            isDownloading && item.hasKnownPercentage -> CircularProgressIndicator(
                progress = { (item.progressPercent / 100f).coerceIn(0f, 1f) },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(inner),
            )
            isDownloading -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(inner),
            )
            isCompleted -> Icon(Icons.Default.PlayArrow, contentDescription = "Assistir", tint = AuroraColors.TextPrimary, modifier = Modifier.size(inner))
            else -> Icon(Icons.Default.Download, contentDescription = null, tint = AuroraColors.TextTertiary, modifier = Modifier.size(inner))
        }
    }
}

private fun statusLine(item: DownloadState): String = when {
    item.status == Download.STATE_DOWNLOADING && item.hasKnownPercentage -> "Baixando — ${item.progressPercent.toInt()}%"
    item.status == Download.STATE_DOWNLOADING -> "Baixando — ${item.bytesDownloaded.toFileSizeLabel()}"
    item.status == Download.STATE_COMPLETED -> "Baixado"
    item.status == Download.STATE_FAILED -> "Falhou — toque em remover e tente de novo"
    else -> "Na fila"
}

@Composable
private fun statusColor(item: DownloadState): Color =
    if (item.status == Download.STATE_FAILED) AuroraColors.Error else AuroraColors.TextTertiary

private fun seriesSubtitle(group: DownloadGroup): String {
    val count = group.itemCount
    val episodes = if (count == 1) "1 episódio" else "$count episódios"
    val size = group.totalBytes.takeIf { it > 0 }?.toFileSizeLabel()
    return when {
        group.anyDownloading -> {
            val done = group.completedCount
            if (size != null) "Baixando $done/$count • $size" else "Baixando $done/$count"
        }
        size != null -> "$episodes • $size"
        else -> episodes
    }
}
