package com.auroraplay.iptv.presentation.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.domain.model.Series
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import com.auroraplay.iptv.domain.repository.WatchProgressRepository
import com.auroraplay.iptv.domain.usecase.SmartCategoryBuilder
import com.auroraplay.iptv.domain.usecase.SyncContentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesListUiState(
    val isLoading: Boolean = true,
    val genreChips: List<String> = emptyList(),
    val selectedGenre: String? = null,
    val query: String = "",
    val series: List<Series> = emptyList(),
    /** Shown while the search field is open and empty — parity with Movies,
     * which had this and Series didn't, despite being the same kind of screen. */
    val searchSuggestions: List<Series> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val contentRepository: ContentRepository,
    private val profileRepository: ProfileRepository,
    private val smartCategoryBuilder: SmartCategoryBuilder,
    private val syncContentUseCase: SyncContentUseCase,
    private val watchProgressRepository: WatchProgressRepository,
    private val contentPolicy: com.auroraplay.iptv.domain.policy.ContentPolicy,
) : ViewModel() {

    private val selectedGenre = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private var activeConnectionId: String? = null

    private val _uiState = MutableStateFlow(SeriesListUiState())
    val uiState: StateFlow<SeriesListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val connection = connectionRepository.getDefaultConnection()
            activeConnectionId = connection?.id
            if (connection == null) {
                _uiState.value = SeriesListUiState(isLoading = false)
                return@launch
            }
            val profile = profileRepository.observeActiveProfile().first()

            // Genres this profile has actually watched drive the suggestions —
            // same signal Movies uses, so both screens recommend consistently.
            val watched = profile?.let { watchProgressRepository.observeContinueWatching(connection.id, it.id).first() }.orEmpty()
            val watchedIds = watched.map { it.contentId.substringBefore(":") }.toSet()

            combine(
                contentRepository.observeSeries(connection.id),
                selectedGenre,
                query.debounce(200),
            ) { all, genre, q ->
                val kidsFiltered = contentPolicy.series(profile?.isKids == true, all)
                val deduped = kidsFiltered.distinctBy { smartCategoryBuilder.cleanTitle(it.name).lowercase() to it.year }

                val genreFiltered = if (genre == null) deduped else {
                    val keywords = smartCategoryBuilder.genreChips(deduped.map { it.genre })
                        .firstOrNull { it.first == genre }?.second ?: emptyList()
                    deduped.filter { s -> keywords.any { k -> s.genre?.lowercase()?.contains(k) == true } }
                }

                val searched = if (q.isBlank()) genreFiltered else {
                    genreFiltered.filter { it.name.contains(q.trim(), ignoreCase = true) }
                }

                val watchedGenres = deduped.filter { it.id in watchedIds }.mapNotNull { it.genre }.toSet()
                val suggestions = if (watchedGenres.isEmpty()) {
                    deduped.sortedByDescending { it.addedAtMillis }.take(12)
                } else {
                    deduped.filter { s -> s.id !in watchedIds && s.genre in watchedGenres }.take(12)
                        .ifEmpty { deduped.sortedByDescending { it.addedAtMillis }.take(12) }
                }

                SeriesListUiState(
                    isLoading = false,
                    genreChips = smartCategoryBuilder.genreChips(deduped.map { it.genre }).map { it.first },
                    selectedGenre = genre,
                    query = q,
                    series = searched,
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

    /** Pull-to-refresh — same re-sync Home/Movies use. */
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
