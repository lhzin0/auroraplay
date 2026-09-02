package com.auroraplay.iptv.presentation.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.util.toFileSizeLabel
import com.auroraplay.iptv.presentation.components.AppButton
import com.auroraplay.iptv.presentation.components.ErrorState
import com.auroraplay.iptv.presentation.components.GlassButton
import com.auroraplay.iptv.presentation.components.MovieCard
import com.auroraplay.iptv.presentation.components.SeasonDropdown
import com.auroraplay.iptv.presentation.components.Spacing
import com.auroraplay.iptv.presentation.components.DetailMediaPager

@Composable
fun SeriesDetailsScreen(
    onBack: () -> Unit,
    onWatchEpisode: (seriesId: String, episodeId: String) -> Unit,
    onOpenSeries: (String) -> Unit = {},
    viewModel: SeriesDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(AuroraColors.BackgroundBase)) {
        when {
            state.errorMessage != null -> ErrorState(message = state.errorMessage!!, onRetry = onBack)
            state.series != null -> {
                val series = state.series!!
                val selectedSeason = series.seasons.find { it.seasonNumber == state.selectedSeasonNumber } ?: series.seasons.firstOrNull()
                val firstEpisode = series.seasons.firstOrNull()?.episodes?.firstOrNull()
                val resumeEpisode = state.resumeEpisodeId?.let { id ->
                    series.seasons.flatMap { it.episodes }.find { it.id == id }
                }
                val targetEpisode = resumeEpisode ?: firstEpisode
                val hasResume = resumeEpisode != null && state.resumePositionMillis > 0L

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar",
                                    tint = Color.White,
                                )
                            }
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            DetailMediaPager(
                                title = series.name,
                                backdropUrl = series.backdropUrl ?: series.posterUrl,
                                subtitle = listOfNotNull(series.year, series.genre, "${series.seasons.size} temporada(s)").joinToString("  •  "),
                                trailerYoutubeId = state.trailerYoutubeId,
                            )
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Text(series.name, style = MaterialTheme.typography.headlineMedium, color = AuroraColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            val metaLine = listOfNotNull(series.year, series.genre, "${series.seasons.size} temporada(s)")
                            if (metaLine.isNotEmpty()) {
                                Text(
                                    text = metaLine.joinToString("  •  "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AuroraColors.TextSecondary,
                                )
                            }
                            Spacer(Modifier.height(16.dp))

                            // Primary action, full width — the single most
                            // prominent thing on the page, same emphasis as
                            // the reference's white "Assistir" button.
                            if (targetEpisode != null) {
                                AppButton(
                                    text = if (hasResume) "Continuar" else "Assistir",
                                    onClick = { onWatchEpisode(series.id, targetEpisode.id) },
                                    icon = Icons.Default.PlayArrow,
                                    fullWidth = true,
                                )
                                Spacer(Modifier.height(10.dp))
                                val epIsDownloaded = targetEpisode.id in state.downloadedEpisodeIds
                                val epIsDownloading = targetEpisode.id in state.downloadingEpisodeIds
                                val epHasKnownPercentage = state.downloadHasKnownPercentageByEpisodeId[targetEpisode.id] ?: true
                                GlassButton(
                                    text = when {
                                        epIsDownloaded -> "Baixado — T${targetEpisode.seasonNumber}:E${targetEpisode.episodeNumber}"
                                        epIsDownloading && epHasKnownPercentage -> "Baixando T${targetEpisode.seasonNumber}:E${targetEpisode.episodeNumber}…"
                                        epIsDownloading -> "Baixando… ${(state.downloadBytesByEpisodeId[targetEpisode.id] ?: 0L).toFileSizeLabel()}"
                                        else -> "Baixar T${targetEpisode.seasonNumber}:E${targetEpisode.episodeNumber}"
                                    },
                                    icon = if (epIsDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                    downloadProgress = if (epIsDownloading && epHasKnownPercentage) state.downloadProgressByEpisodeId[targetEpisode.id] ?: 0f else null,
                                    downloadIndeterminate = epIsDownloading && !epHasKnownPercentage,
                                    selected = epIsDownloaded,
                                    onClick = { viewModel.toggleEpisodeDownload(targetEpisode) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Spacer(Modifier.height(20.dp))
                            Text(
                                "Descrição",
                                style = MaterialTheme.typography.titleMedium,
                                color = AuroraColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            if (!series.plot.isNullOrBlank()) {
                                Text(
                                    text = series.plot.trim(),
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                                    color = AuroraColors.TextSecondary,
                                    modifier = Modifier.widthIn(max = 680.dp),
                                )
                            } else {
                                Text(
                                    "Descrição não disponível nesta playlist.",
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                                    color = AuroraColors.TextTertiary,
                                    modifier = Modifier.widthIn(max = 680.dp),
                                )
                            }

                            Spacer(Modifier.height(20.dp))
                            // Secondary actions as a row of icon+label buttons,
                            // same shape as the reference's Minha lista /
                            // Avaliar / Compartilhe / Baixar row — "Avaliar" is
                            // dropped since there's no rating backend to wire
                            // it to, and a button with no effect is worse than
                            // one fewer button.
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                DetailIconAction(
                                    modifier = Modifier.weight(1f),
                                    icon = if (state.isFavorite) Icons.Default.Check else Icons.Default.Add,
                                    label = if (state.isFavorite) "Na lista" else "Minha lista",
                                    tint = if (state.isFavorite) MaterialTheme.colorScheme.primary else AuroraColors.TextPrimary,
                                    onClick = { viewModel.toggleFavorite() },
                                )
                                DetailIconAction(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Share,
                                    label = "Compartilhe",
                                    onClick = {
                                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, "Assista \"${series.name}\" no AuroraPlay!")
                                        }
                                        context.startActivity(android.content.Intent.createChooser(sendIntent, null))
                                    },
                                )
                                selectedSeason?.let { season ->
                                    DetailIconAction(
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Default.Download,
                                        label = "Baixar T${season.seasonNumber}",
                                        onClick = { viewModel.downloadSeason(season.episodes) },
                                    )
                                }
                            }
                        }
                    }
                    if (series.seasons.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(28.dp))
                            Text(
                                "Episódios",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = AuroraColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            SeasonDropdown(
                                seasons = series.seasons.map { it.seasonNumber },
                                selectedSeason = state.selectedSeasonNumber,
                                onSelect = viewModel::selectSeason,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    selectedSeason?.episodes?.let { episodes ->
                        items(episodes, key = { it.id }) { episode ->
                            EpisodeRow(
                                episodeNumber = episode.episodeNumber,
                                title = episode.title,
                                description = episode.plot,
                                thumbnailUrl = episode.thumbnailUrl,
                                durationLabel = episode.durationLabel,
                                isDownloaded = episode.id in state.downloadedEpisodeIds,
                                isDownloading = episode.id in state.downloadingEpisodeIds,
                                downloadProgress = state.downloadProgressByEpisodeId[episode.id] ?: 0f,
                                hasKnownDownloadPercentage = state.downloadHasKnownPercentageByEpisodeId[episode.id] ?: true,
                                resumePositionMillis = state.resumePositionMillis.takeIf { episode.id == state.resumeEpisodeId },
                                resumeDurationMillis = state.resumeDurationMillis.takeIf { episode.id == state.resumeEpisodeId },
                                onClick = { onWatchEpisode(series.id, episode.id) },
                                onToggleDownload = { viewModel.toggleEpisodeDownload(episode) },
                            )
                        }
                    }
                    if (state.similar.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(28.dp))
                            Text(
                                "Títulos semelhantes",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = AuroraColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = Spacing.gutter),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            ) {
                                items(state.similar, key = { it.id }) { other ->
                                    MovieCard(
                                        title = other.name,
                                        imageUrl = other.posterUrl,
                                        onClick = { onOpenSeries(other.id) },
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(Spacing.navBarClearance)) }
                }
            }
        }
    }
}

@Composable
private fun DetailIconAction(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = AuroraColors.TextPrimary,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = AuroraColors.TextSecondary)
    }
}

@Composable
private fun EpisodeRow(
    episodeNumber: Int,
    title: String,
    description: String?,
    thumbnailUrl: String?,
    durationLabel: String?,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    hasKnownDownloadPercentage: Boolean,
    resumePositionMillis: Long?,
    resumeDurationMillis: Long?,
    onClick: () -> Unit,
    onToggleDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AuroraColors.SurfaceHigh)
                    .clickable(onClick = onClick),
            ) {
                if (thumbnailUrl != null) {
                    AsyncImage(model = thumbnailUrl, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.Center).size(26.dp),
                )
            }

            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
            ) {
                Text(
                    "$episodeNumber. $title",
                    style = MaterialTheme.typography.titleSmall,
                    color = AuroraColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                durationLabel?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AuroraColors.TextTertiary)
                }
                resumePositionMillis?.takeIf { it > 0L }?.let { position ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Continuar de ${formatTime(position)} / ${formatTime(resumeDurationMillis ?: 0L)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }

            // Beside the title, at the same row level — not a separate strip
            // stacked above or below the episode, the way a full-width
            // "Baixar para assistir offline" label would read.
            Spacer(Modifier.width(4.dp))
            EpisodeDownloadIcon(
                isDownloaded = isDownloaded,
                isDownloading = isDownloading,
                progress = downloadProgress,
                hasKnownPercentage = hasKnownDownloadPercentage,
                onClick = onToggleDownload,
            )
        }

        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = AuroraColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EpisodeDownloadIcon(
    isDownloaded: Boolean,
    isDownloading: Boolean,
    progress: Float,
    hasKnownPercentage: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isDownloaded -> Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Episódio baixado — toque para remover",
                tint = AuroraColors.Success,
                modifier = Modifier.size(22.dp),
            )
            isDownloading && hasKnownPercentage -> CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
            isDownloading -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )
            else -> Icon(
                Icons.Default.Download,
                contentDescription = "Baixar episódio",
                tint = AuroraColors.TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
