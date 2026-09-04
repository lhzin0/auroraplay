package com.auroraplay.iptv.presentation.series

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.presentation.components.CategoryStrip
import com.auroraplay.iptv.presentation.components.EmptyState
import com.auroraplay.iptv.presentation.components.LoadingSkeleton
import com.auroraplay.iptv.presentation.components.MovieCard
import com.auroraplay.iptv.presentation.components.PageHeader
import com.auroraplay.iptv.presentation.components.Spacing
import com.auroraplay.iptv.presentation.components.SuggestionRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    onOpenSeries: (String) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchOpen by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(AuroraColors.BackgroundBase)) {
        PageHeader(
            title = "Séries",
            searchQuery = state.query,
            onSearchQueryChange = viewModel::updateQuery,
            searchPlaceholder = "Pesquisar séries...",
            onSearchOpenChange = { searchOpen = it },
        )

        val showSuggestions = searchOpen && state.query.isBlank() && state.searchSuggestions.isNotEmpty()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.refresh { isRefreshing = false }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (showSuggestions) {
                // Usage-based suggestions while the field is open and empty —
                // same treatment as Movies, so search feels the same across
                // both catalogs instead of Series being the plainer one.
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = Spacing.gutter,
                        end = Spacing.gutter,
                        bottom = Spacing.navBarClearance,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            "Recomendados para você",
                            style = MaterialTheme.typography.titleLarge,
                            color = AuroraColors.TextPrimary,
                            modifier = Modifier.padding(vertical = Spacing.md),
                        )
                    }
                    items(state.searchSuggestions, key = { it.id }) { series ->
                        SuggestionRow(
                            title = series.name,
                            imageUrl = series.backdropUrl ?: series.posterUrl,
                            onClick = { onOpenSeries(series.id) },
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (state.genreChips.isNotEmpty()) {
                        CategoryStrip(
                            categories = listOf<Pair<String?, String>>(null to "Todas") +
                                state.genreChips.map { it as String? to it },
                            selectedId = state.selectedGenre,
                            onSelect = viewModel::selectGenre,
                            modifier = Modifier.padding(vertical = Spacing.sm),
                        )
                    }

                    when {
                        state.isLoading -> SeriesGridSkeleton()
                        state.series.isEmpty() && state.query.isNotBlank() ->
                            EmptyState(message = "Nenhuma série encontrada para \"${state.query}\"")
                        state.series.isEmpty() ->
                            EmptyState(message = "Não há títulos disponíveis nesta categoria.")
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 116.dp),
                            contentPadding = PaddingValues(
                                start = Spacing.gutter,
                                end = Spacing.gutter,
                                top = Spacing.sm,
                                bottom = Spacing.navBarClearance,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.series, key = { it.id }) { series ->
                                MovieCard(
                                    title = series.name,
                                    imageUrl = series.posterUrl,
                                    onClick = { onOpenSeries(series.id) },
                                    width = null,
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun SeriesGridSkeleton() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 116.dp),
        contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(12) {
            Column {
                LoadingSkeleton(Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                Spacer(Modifier.height(Spacing.sm))
                LoadingSkeleton(Modifier.fillMaxWidth(0.75f).height(12.dp))
            }
        }
    }
}
