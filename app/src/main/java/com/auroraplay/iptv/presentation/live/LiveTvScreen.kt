package com.auroraplay.iptv.presentation.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.presentation.components.CategoryChip
import com.auroraplay.iptv.presentation.components.ChannelCard
import com.auroraplay.iptv.presentation.components.EmptyState
import com.auroraplay.iptv.presentation.player.PlayerScreenContent

@Composable
fun LiveTvScreen(
    onOpenFullscreen: (String) -> Unit,
    onOpenGuide: () -> Unit = {},
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val playerManager = com.auroraplay.iptv.presentation.player.hiltPlayerManager()

    // The Live tab owns the embedded preview. Leaving the tab must silence it
    // immediately; returning creates a fresh PlayerScreenContent, which plays
    // the selected channel again from the live edge.
    DisposableEffect(Unit) {
        onDispose {
            playerManager.stop()
        }
    }

    Column(Modifier.fillMaxSize().background(AuroraColors.BackgroundBase)) {
        // --- Page header first, so the title never sits under the notch and
        // the screen doesn't open with a black rectangle where no channel is
        // playing yet. ---
        com.auroraplay.iptv.presentation.components.PageHeader(
            title = "Canais",
            searchQuery = state.query,
            onSearchQueryChange = viewModel::updateQuery,
            searchPlaceholder = "Pesquisar canais...",
            trailing = {
                IconButton(onClick = onOpenGuide) {
                    Icon(Icons.Default.CalendarViewDay, contentDescription = "Guia de programação", tint = AuroraColors.TextPrimary)
                }
            },
        )

        // --- Preview player: only once a channel is chosen ---
        val channel = state.selectedChannel
        if (channel != null) {
            // The now-playing strip below reads channel.currentProgram, which
            // is only ever populated by actually calling the short-EPG
            // endpoint — this is that call for whichever channel is playing
            // in the embedded preview, regardless of whether its row is
            // currently scrolled into view in the list below.
            LaunchedEffect(channel.id) { viewModel.ensureEpg(channel) }

            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
            ) {
                PlayerScreenContent(streamUrl = channel.streamUrl, isLive = true, playerManager = playerManager)

                // Tapping the preview promotes it to the landscape full player.
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable { onOpenFullscreen(channel.id) }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    IconButton(onClick = { viewModel.toggleFavorite(channel) }) {
                        Icon(
                            if (state.favoriteIds.contains(channel.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favoritar",
                            tint = if (state.favoriteIds.contains(channel.id)) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    IconButton(onClick = { onOpenFullscreen(channel.id) }) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Tela cheia", tint = Color.White)
                    }
                }

                // Now-playing strip with EPG progress, when the server provides one.
                channel.currentProgram?.let { program ->
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(program.title, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(Color.White.copy(alpha = 0.25f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(program.progressFraction())
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }

        // --- Category chips ---
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                CategoryChip(
                    text = "Todos",
                    selected = state.selectedCategoryId == null,
                    onClick = { viewModel.selectCategory(null) },
                )
            }
            item {
                CategoryChip(
                    text = "Favoritos",
                    selected = state.showOnlyFavorites,
                    onClick = { viewModel.toggleFavoritesFilter() },
                )
            }
            items(state.categories, key = { it.id }) { category ->
                CategoryChip(
                    text = category.name,
                    selected = state.selectedCategoryId == category.id,
                    onClick = { viewModel.selectCategory(category.id) },
                )
            }
        }

        val visibleChannels = state.visibleChannels
        if (visibleChannels.isEmpty() && !state.isLoading) {
            EmptyState(message = if (state.query.isNotBlank()) "Nenhum canal encontrado para \"${state.query}\"" else "Nenhum canal disponível nesta categoria")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = com.auroraplay.iptv.presentation.components.floatingBarClearance),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleChannels, key = { it.id }) { channel ->
                    // Fires once per row composition; LazyColumn only composes
                    // rows actually on screen, so this scales with what's
                    // visible while scrolling rather than the whole list.
                    LaunchedEffect(channel.id) { viewModel.ensureEpg(channel) }
                    ChannelCard(
                        name = channel.name,
                        logoUrl = channel.logoUrl,
                        currentProgram = channel.currentProgram?.title ?: channel.categoryName,
                        selected = channel.id == state.selectedChannel?.id,
                        isFavorite = state.favoriteIds.contains(channel.id),
                        onClick = { viewModel.selectChannel(channel) },
                        onToggleFavorite = { viewModel.toggleFavorite(channel) },
                    )
                }
            }
        }
    }
}
