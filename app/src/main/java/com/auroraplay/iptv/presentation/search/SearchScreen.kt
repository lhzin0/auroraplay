package com.auroraplay.iptv.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.domain.model.MediaItem
import com.auroraplay.iptv.presentation.components.CategoryChip
import com.auroraplay.iptv.presentation.components.ContextualSearchField
import com.auroraplay.iptv.presentation.components.EmptyState
import com.auroraplay.iptv.presentation.components.Spacing
import com.auroraplay.iptv.presentation.components.SuggestionRow
import com.auroraplay.iptv.presentation.components.floatingBarClearance

@Composable
fun SearchScreen(
    onOpenMovie: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    fun open(item: MediaItem) = when (item) {
        is MediaItem.MovieItem -> onOpenMovie(item.id)
        is MediaItem.SeriesItem -> onOpenSeries(item.id)
        is MediaItem.ChannelItem -> onOpenChannel(item.id)
    }

    fun thumbFor(item: MediaItem): String? = when (item) {
        is MediaItem.MovieItem -> item.movie.posterUrl ?: item.movie.backdropUrl
        is MediaItem.SeriesItem -> item.series.posterUrl ?: item.series.backdropUrl
        is MediaItem.ChannelItem -> item.channel.logoUrl
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AuroraColors.BackgroundBase)
            .statusBarsPadding()
    ) {
        Text(
            "Buscar",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = AuroraColors.TextPrimary,
            modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.md),
        )
        Spacer(Modifier.height(Spacing.md))

        ContextualSearchField(
            query = state.query,
            onQueryChange = viewModel::updateQuery,
            placeholder = "Buscar filmes, séries e canais",
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        )

        Spacer(Modifier.height(Spacing.md))
        // Four fixed filters, spread edge-to-edge across the same width as the
        // search field above — no scrolling, no dead space on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            listOf(
                SearchFilter.ALL to "Tudo",
                SearchFilter.MOVIES to "Filmes",
                SearchFilter.SERIES to "Séries",
                SearchFilter.CHANNELS to "Canais",
            ).forEach { (filter, label) ->
                CategoryChip(
                    text = label,
                    selected = state.filter == filter,
                    onClick = { viewModel.updateFilter(filter) },
                    modifier = Modifier.weight(1f),
                    horizontalPadding = 8.dp,
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))

        val showingSuggestions = state.query.isBlank()
        val list = if (showingSuggestions) state.suggestions else state.results
        // The results pipeline is debounced, so right after a keystroke it
        // hasn't caught up yet — don't call it "no results" until it has.
        val resultsReady = state.searchedQuery == state.query.trim()

        when {
            !showingSuggestions && list.isEmpty() && resultsReady ->
                EmptyState(message = "Nenhum resultado para \"${state.query}\"")

            showingSuggestions && list.isEmpty() && state.recentSearches.isEmpty() ->
                EmptyState(message = "Nada para sugerir ainda.")

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    bottom = floatingBarClearance,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (showingSuggestions && state.recentSearches.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Buscas recentes", style = MaterialTheme.typography.titleMedium, color = AuroraColors.TextPrimary)
                            TextButton(onClick = viewModel::clearRecentSearches) {
                                Text("Limpar", style = MaterialTheme.typography.labelMedium, color = AuroraColors.TextSecondary)
                            }
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            items(state.recentSearches, key = { it }) { recent ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(AuroraColors.SurfaceHigh)
                                        .clickable { viewModel.updateQuery(recent) }
                                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = AuroraColors.TextTertiary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(Spacing.xs))
                                    Text(recent, style = MaterialTheme.typography.labelLarge, color = AuroraColors.TextSecondary)
                                }
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))
                    }
                }
                if (showingSuggestions && list.isNotEmpty()) {
                    item {
                        Text(
                            "Recomendados para você",
                            style = MaterialTheme.typography.titleLarge,
                            color = AuroraColors.TextPrimary,
                            modifier = Modifier.padding(vertical = Spacing.md),
                        )
                    }
                }
                items(list, key = { item -> "${item::class.simpleName}:${item.id}" }) { item ->
                    SuggestionRow(
                        title = item.title,
                        imageUrl = thumbFor(item),
                        onClick = { open(item) },
                    )
                }
                if (!showingSuggestions && state.hasMoreResults) {
                    item(key = "load-more") {
                        androidx.compose.material3.TextButton(
                            onClick = { viewModel.loadMoreResults() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.sm),
                        ) {
                            Text("Carregar mais", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
