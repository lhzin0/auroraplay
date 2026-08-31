package com.auroraplay.iptv.presentation.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.Movie
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.FavoriteRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import com.auroraplay.iptv.domain.usecase.SmartCategoryBuilder
import com.auroraplay.iptv.domain.usecase.SyncContentUseCase
import com.auroraplay.iptv.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MoviesUiState(
    val isLoading: Boolean = true,
    /** Genre filter chips derived from real content, not raw playlist names. */
    val genreChips: List<String> = emptyList(),
    val selectedGenre: String? = null,
    val query: String = "",
    val movies: List<Movie> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    /** Shown while the search field is open and empty, so the panel offers a
     * starting point instead of a blank screen. */
    val searchSuggestions: List<Movie> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val contentRepository: ContentRepository,
    private val profileRepository: ProfileRepository,
    private val favoriteRepository: FavoriteRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val smartCategoryBuilder: SmartCategoryBuilder,
    private val syncContentUseCase: SyncContentUseCase,
    private val watchProgressRepository: com.auroraplay.iptv.domain.repository.WatchProgressRepository,
) : ViewModel() {

    private val selectedGenre = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private var activeProfileId: String? = null
    private var activeConnectionId: String? = null

    private val _uiState = MutableStateFlow(MoviesUiState())
    val uiState: StateFlow<MoviesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val connection = connectionRepository.getDefaultConnection()
            val profile = profileRepository.observeActiveProfile().first()
            activeProfileId = profile?.id
            activeConnectionId = connection?.id
            if (connection == null) {
                _uiState.value = MoviesUiState(isLoading = false)
                return@launch
            }

            // Genres this profile has actually watched drive the suggestions.
            val watched = profile?.let { watchProgressRepository.observeContinueWatching(it.id).first() }.orEmpty()
            val watchedIds = watched.map { it.contentId.substringBefore(":") }.toSet()

            val allMoviesFlow = contentRepository.observeMovies(connection.id)
                .map { list -> if (profile?.isKids == true) list.filter { com.auroraplay.iptv.core.util.KidsContentFilter.isKidsAppropriate(it.categoryName, it.genre) } else list }
            val favoritesFlow = if (profile != null) {
                favoriteRepository.observeFavorites(profile.id, ContentType.MOVIE)
            } else {
                flowOf(emptyList())
            }

            combine(
                allMoviesFlow,
                favoritesFlow,
                selectedGenre,
                query.debounce(200),
            ) { all, favorites, genre, q ->
                // De-duplicate: providers frequently list the same title twice
                // under different quality tags, which produced visibly repeated
                // posters in the grid.
                val deduped = all.distinctBy { smartCategoryBuilder.cleanTitle(it.name).lowercase() to it.year }

                val genreFiltered = if (genre == null) deduped else {
                    val keywords = smartCategoryBuilder.genreChips(deduped.map { it.genre })
                        .firstOrNull { it.first == genre }?.second ?: emptyList()
                    deduped.filter { m -> keywords.any { k -> m.genre?.lowercase()?.contains(k) == true } }
                }

                val searched = if (q.isBlank()) genreFiltered else {
                    genreFiltered.filter { it.name.contains(q.trim(), ignoreCase = true) }
                }

                val watchedGenres = deduped.filter { it.id in watchedIds }.mapNotNull { it.genre }.toSet()
                val suggestions = if (watchedGenres.isEmpty()) {
                    deduped.sortedByDescending { it.addedAtMillis }.take(12)
                } else {
                    deduped.filter { m -> m.id !in watchedIds && m.genre in watchedGenres }.take(12)
                        .ifEmpty { deduped.sortedByDescending { it.addedAtMillis }.take(12) }
                }

                MoviesUiState(
                    isLoading = false,
                    genreChips = smartCategoryBuilder.genreChips(deduped.map { it.genre }).map { it.first },
                    selectedGenre = genre,
                    query = q,
                    movies = searched,
                    favoriteIds = favorites.map { it.contentId }.toSet(),
                    searchSuggestions = suggestions,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun selectGenre(genre: String?) { selectedGenre.value = genre }

    fun updateQuery(newQuery: String) {
        query.value = newQuery
        _uiState.value = _uiState.value.copy(query = newQuery)
    }

    fun toggleFavorite(movieId: String) {
        val profileId = activeProfileId ?: return
        viewModelScope.launch { toggleFavoriteUseCase(profileId, movieId, ContentType.MOVIE) }
    }

    /** Pull-to-refresh — same re-sync Home uses; the reactive movies flow
     * above picks up whatever lands in the DB automatically. */
    fun refresh(onDone: () -> Unit) {
        val connectionId = activeConnectionId
        if (connectionId == null) {
            onDone()
            return
        }
        viewModelScope.launch {
            runCatching { syncContentUseCase(connectionId).collect {} }
            onDone()
        }
    }
}
