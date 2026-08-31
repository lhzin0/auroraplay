package com.auroraplay.iptv.presentation.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.domain.model.MediaItem
import com.auroraplay.iptv.domain.model.SectionLayout
import com.auroraplay.iptv.presentation.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenMovie: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onResume: (contentType: String, contentId: String) -> Unit,
    onOpenAddConnection: () -> Unit,
    onSeeAllMovies: () -> Unit,
    onSeeAllSeries: () -> Unit,
    onOpenDownloads: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Fade in as soon as the hero starts moving under the bar. Keying this to
    // firstVisibleItemIndex alone meant the background only appeared once the
    // whole hero (a very tall item) had scrolled past, so the bar stayed
    // transparent through most of the first screen and the logo sat unreadably
    // over the artwork.
    //
    // derivedStateOf is what makes this cheap: firstVisibleItemScrollOffset
    // changes on every pixel scrolled, and reading it directly in the
    // composable's own body (without this) meant the entire HomeScreen —
    // hero carousel, every genre rail, all of it — recomposed on every single
    // frame of a scroll gesture, which is exactly what a fast-scroll stutter
    // looks like. Wrapping it here means only the two moments the boolean
    // actually flips (crossing the threshold) trigger anything at all.
    val scrolledPast by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 80
        }
    }
    val barOpacity by animateFloatAsState(
        targetValue = if (scrolledPast) 1f else 0f,
        animationSpec = tween(160),
        label = "topBarBg",
    )

    fun openItem(item: MediaItem) = when (item) {
        is MediaItem.ChannelItem -> onOpenChannel(item.id)
        is MediaItem.MovieItem -> onOpenMovie(item.id)
        is MediaItem.SeriesItem -> onOpenSeries(item.id)
    }

    Box(Modifier.fillMaxSize().background(AuroraColors.BackgroundBase)) {
        when {
            !state.hasConnection -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ServerOfflineState(onRetry = onOpenAddConnection)
            }

            state.isLoading -> Column(Modifier.padding(top = 72.dp)) {
                LoadingSkeleton(Modifier.fillMaxWidth().height(420.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                Spacer(Modifier.height(16.dp))
                repeat(2) { HomeRowSkeleton() }
            }

            state.content == null || (state.content!!.heroItems.isEmpty() && state.content!!.sections.isEmpty()) ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState() }

            else -> {
                val content = state.content!!
                var isRefreshing by remember { mutableStateOf(false) }
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        viewModel.refresh { isRefreshing = false }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = com.auroraplay.iptv.presentation.components.floatingBarClearance),
                    // Explicit keys + stable item types let Compose reuse rows
                    // instead of recomposing the whole list while scrolling.
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (content.heroItems.isNotEmpty()) {
                        item(key = "hero", contentType = "hero") {
                          Column {
                            // Clears the overlaid top bar + status bar so the
                            // poster never starts underneath the notch.
                            Spacer(
                                Modifier
                                    .statusBarsPadding()
                                    .height(24.dp)
                            )
                            HeroCarousel(
                                entries = content.heroItems.map { hero ->
                                    HeroEntry(
                                        id = hero.id,
                                        // Providers often ship "Show: Season"
                                        // in one string; split it so the card
                                        // can stack them like the reference.
                                        title = hero.title.substringBefore(":").trim(),
                                        subtitle = hero.title.substringAfter(":", "").trim().ifBlank { null },
                                        posterUrl = hero.imageUrl,
                                        backdropUrl = heroBackdrop(hero),
                                        tags = listOfNotNull(heroCategory(hero), heroYear(hero)),
                                        isFavorite = state.favoriteIds.contains(hero.id),
                                    )
                                },
                                onWatch = { id ->
                                    val hero = content.heroItems.first { it.id == id }
                                    val resume = content.resumeByItemId[id]
                                    if (resume != null) onResume(heroTypeName(hero), resume.contentId)
                                    else openItem(hero)
                                },
                                onToggleFavorite = { id ->
                                    viewModel.toggleFavorite(content.heroItems.first { it.id == id })
                                },
                                onDetails = { id -> openItem(content.heroItems.first { it.id == id }) },
                                isLandscape = isLandscape,
                            )
                          }
                        }
                    }

                    items(
                        items = content.sections,
                        key = { it.id },
                        contentType = { it.layout },
                    ) { section ->
                        Column(Modifier.padding(top = 4.dp)) {
                            SectionHeader(
                                title = section.title,
                                onSeeAllClick = when {
                                    section.id.contains("movie") -> onSeeAllMovies
                                    section.id.contains("series") -> onSeeAllSeries
                                    else -> null
                                },
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = com.auroraplay.iptv.presentation.components.Spacing.gutter),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(section.items, key = { it.id }, contentType = { section.layout }) { item ->
                                    when (section.layout) {
                                        SectionLayout.LANDSCAPE -> {
                                            val resume = content.resumeByItemId[item.id]
                                            ContinueWatchingCard(
                                                title = item.title,
                                                imageUrl = item.imageUrl,
                                                progress = resume?.fraction ?: 0f,
                                                onClick = {
                                                    if (resume != null) onResume(heroTypeName(item), resume.contentId)
                                                    else openItem(item)
                                                },
                                                onInfo = { openItem(item) },
                                                onRemove = { viewModel.removeFromContinueWatching(item) },
                                            )
                                        }
                                        SectionLayout.CHANNEL -> ChannelTile(
                                            name = item.title,
                                            logoUrl = item.imageUrl,
                                            currentProgram = (item as? MediaItem.ChannelItem)?.channel?.currentProgram?.title,
                                            onClick = { openItem(item) },
                                        )
                                        SectionLayout.POSTER -> MovieCard(
                                            title = item.title,
                                            imageUrl = item.imageUrl,
                                            onClick = { openItem(item) },
                                            progress = content.resumeByItemId[item.id]?.fraction,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                }
                }
            }
        }

        // Portrait keeps the compact app bar. Landscape is intentionally
        // immersive: the hero/trailer owns the full horizontal stage and the
        // old top bar is removed so it never consumes a large strip of video.
        if (!isLandscape) Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(AuroraColors.BackgroundBase.copy(alpha = barOpacity))
                .statusBarsPadding()
                .padding(horizontal = com.auroraplay.iptv.presentation.components.Spacing.gutter, vertical = 8.dp),
        ) {
            Text(
                "AuroraPlay",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = AuroraColors.TextPrimary,
            )
            Spacer(Modifier.weight(1f))
            // Search and settings live in the bottom navigation bar, reachable
            // from every screen rather than only from Home — but downloads and
            // notifications sit here, top-right, the same spot every major
            // streaming app puts them.
            val hasUnreadNotifications by hiltViewModel<com.auroraplay.iptv.presentation.notifications.NotificationsViewModel>()
                .notifications.collectAsState()
            IconButton(onClick = onOpenDownloads) {
                Icon(Icons.Default.Download, contentDescription = "Downloads", tint = AuroraColors.TextPrimary)
            }
            Box {
                IconButton(onClick = onOpenNotifications) {
                    Icon(Icons.Default.Notifications, contentDescription = "Notificações", tint = AuroraColors.TextPrimary)
                }
                if (hasUnreadNotifications.any { !it.read }) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }
        }
    }
}

private fun heroTypeName(item: MediaItem): String = when (item) {
    is MediaItem.ChannelItem -> "LIVE"
    is MediaItem.MovieItem -> "MOVIE"
    is MediaItem.SeriesItem -> "SERIES"
}

private fun heroCategory(item: MediaItem): String? = when (item) {
    is MediaItem.MovieItem -> item.movie.genre ?: item.movie.categoryName
    is MediaItem.SeriesItem -> item.series.genre ?: item.series.categoryName
    is MediaItem.ChannelItem -> item.channel.categoryName
}

private fun heroYear(item: MediaItem): String? = when (item) {
    is MediaItem.MovieItem -> item.movie.year
    is MediaItem.SeriesItem -> item.series.year
    is MediaItem.ChannelItem -> null
}

private fun heroBackdrop(item: MediaItem): String? = when (item) {
    is MediaItem.MovieItem -> item.movie.backdropUrl ?: item.movie.posterUrl
    is MediaItem.SeriesItem -> item.series.backdropUrl ?: item.series.posterUrl
    is MediaItem.ChannelItem -> item.channel.logoUrl
}
