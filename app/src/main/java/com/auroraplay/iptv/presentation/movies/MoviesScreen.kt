package com.auroraplay.iptv.presentation.movies

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
fun MoviesScreen(
    onOpenMovie: (String) -> Unit,
    viewModel: MoviesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchOpen by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(AuroraColors.BackgroundBase)) {
        PageHeader(
            title = "Filmes",
            searchQuery = state.query,
            onSearchQueryChange = viewModel::updateQuery,
            searchPlaceholder = "Pesquisar filmes...",
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
                // Usage-based suggestions while the field is open and empty, so the
                // viewer gets a starting point rather than a blank page.
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
                    items(state.searchSuggestions, key = { it.id }) { movie ->
                        SuggestionRow(
                            title = movie.name,
                            imageUrl = movie.backdropUrl ?: movie.posterUrl,
                            onClick = { onOpenMovie(movie.id) },
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (state.genreChips.isNotEmpty()) {
                        CategoryStrip(
                            categories = listOf<Pair<String?, String>>(null to "Todos") +
                                state.genreChips.map { it as String? to it },
                            selectedId = state.selectedGenre,
                            onSelect = viewModel::selectGenre,
                            modifier = Modifier.padding(vertical = Spacing.sm),
                        )
                    }

                    when {
                        state.isLoading -> MovieGridSkeleton()
                        state.movies.isEmpty() && state.query.isNotBlank() ->
                            EmptyState(message = "Nenhum filme encontrado para \"${state.query}\"")
                        state.movies.isEmpty() ->
                            EmptyState(message = "Não há títulos disponíveis nesta categoria.")
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 116.dp),
                            contentPadding = PaddingValues(
                                start = Spacing.gutter,
                                end = Spacing.gutter,
                                top = Spacing.sm,
                                // Clearance so the last row isn't hidden behind the floating nav bar.
                                bottom = Spacing.navBarClearance,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.movies, key = { it.id }) { movie ->
                                MovieCard(
                                    title = movie.name,
                                    imageUrl = movie.posterUrl,
                                    onClick = { onOpenMovie(movie.id) },
                                    width = null, // fills the grid cell; prevents titles clipping past the edge
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
private fun MovieGridSkeleton() {
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
