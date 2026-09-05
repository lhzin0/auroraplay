package com.auroraplay.iptv.presentation.movies

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.util.toFileSizeLabel
import com.auroraplay.iptv.presentation.components.AppButton
import com.auroraplay.iptv.presentation.components.GlassButton
import com.auroraplay.iptv.presentation.components.MetadataBadgeRow
import com.auroraplay.iptv.presentation.components.RemainingTimeRow
import com.auroraplay.iptv.presentation.components.DetailMediaPager
import com.auroraplay.iptv.presentation.components.Spacing
import com.auroraplay.iptv.presentation.components.ErrorState
import com.auroraplay.iptv.presentation.components.MovieCard
import com.auroraplay.iptv.presentation.components.SectionHeader
import com.auroraplay.iptv.presentation.components.tvFocusable

@Composable
fun MovieDetailsScreen(
    onBack: () -> Unit,
    onWatch: (String) -> Unit,
    onOpenMovie: (String) -> Unit,
    viewModel: MovieDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(AuroraColors.BackgroundBase)) {
        when {
            state.errorMessage != null -> ErrorState(message = state.errorMessage!!, onRetry = onBack)
            state.movie != null -> {
                val movie = state.movie!!
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
                                    .tvFocusable(shape = CircleShape, accent = Color.White)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar",
                                    tint = Color.White,
                                )
                            }
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            // Banner is page 1. Swipe horizontally to reveal the inline trailer.
                            // The back affordance lives above this stage, never over artwork.
                            DetailMediaPager(
                                title = movie.name,
                                backdropUrl = movie.backdropUrl ?: movie.posterUrl,
                                subtitle = listOfNotNull(movie.year, movie.genre, movie.audioLabel).joinToString("  •  ").ifBlank { null },
                                trailerYoutubeId = state.trailerYoutubeId,
                            )
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Text(movie.name, style = MaterialTheme.typography.headlineMedium, color = AuroraColors.TextPrimary)
                            Spacer(Modifier.height(10.dp))
                            // One metadata row, not two: this used to also
                            // render a plain "2004 • 1h46min • Drama" line
                            // above this badge row, which repeated the year
                            // and duration a second time right underneath.
                            MetadataBadgeRow(
                                year = movie.year,
                                ageRating = movie.rating?.let { String.format("%.1f", it) },
                                duration = movie.durationLabel,
                                genre = movie.genre,
                                quality = "HD",
                            )
                            movie.audioLabel?.let { label ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    softWrap = false,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }

                            Spacer(Modifier.height(18.dp))
                            // "Minha lista" sits right beside "Assistir" (matching
                            // the series details screen) instead of a full-width
                            // play button with the favorite toggle stranded
                            // below the synopsis.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AppButton(
                                    text = if (state.resumeFraction > 0f) "Continuar" else "Assistir",
                                    onClick = { onWatch(movie.id) },
                                    icon = Icons.Default.PlayArrow,
                                    modifier = Modifier.weight(1f),
                                )
                                GlassButton(
                                    text = if (state.isFavorite) "Na lista" else "Minha lista",
                                    icon = if (state.isFavorite) Icons.Default.Check else Icons.Default.Add,
                                    selected = state.isFavorite,
                                    onClick = { viewModel.toggleFavorite() },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            if (state.resumeFraction > 0f && state.remainingLabel != null) {
                                Spacer(Modifier.height(16.dp))
                                RemainingTimeRow(
                                    progress = state.resumeFraction,
                                    remainingLabel = state.remainingLabel!!,
                                )
                            }

                            // Download was fully wired in the ViewModel
                            // (toggleDownload, isDownloaded/isDownloading,
                            // progress) but this screen never rendered
                            // anything for it — there was no button anywhere
                            // to actually start a movie download from.
                            Spacer(Modifier.height(10.dp))
                            GlassButton(
                                text = when {
                                    state.isDownloaded -> "Baixado — toque para remover"
                                    state.isDownloading && state.hasKnownDownloadPercentage -> "Baixando ${(state.downloadProgress * 100).toInt()}%"
                                    state.isDownloading -> "Baixando… ${state.downloadBytesDownloaded.toFileSizeLabel()}"
                                    else -> "Baixar para assistir offline"
                                },
                                icon = if (state.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                downloadProgress = if (state.isDownloading && state.hasKnownDownloadPercentage) state.downloadProgress else null,
                                downloadIndeterminate = state.isDownloading && !state.hasKnownDownloadPercentage,
                                selected = state.isDownloaded,
                                onClick = { viewModel.toggleDownload() },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(Modifier.height(20.dp))
                            Text(
                                "Descrição",
                                style = MaterialTheme.typography.titleMedium,
                                color = AuroraColors.TextPrimary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            if (!movie.plot.isNullOrBlank()) {
                                Text(
                                    text = movie.plot.trim(),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        lineHeight = 24.sp,
                                    ),
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
                        }
                    }
                    if (state.similar.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(28.dp))
                            SectionHeader(title = "Mais como este")
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                                items(state.similar, key = { it.id }) { similarMovie ->
                                    MovieCard(
                                        title = similarMovie.name,
                                        imageUrl = similarMovie.posterUrl,
                                        onClick = { onOpenMovie(similarMovie.id) },
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(com.auroraplay.iptv.presentation.components.Spacing.navBarClearance)) }
                }
            }
        }
    }
}
