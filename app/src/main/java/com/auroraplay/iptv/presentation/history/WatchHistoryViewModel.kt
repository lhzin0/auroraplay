package com.auroraplay.iptv.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.WatchProgress
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import com.auroraplay.iptv.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One watched episode inside a series' Histórico entry. */
data class HistoryEpisode(
    /** Full progress key, "<seriesId>:<episodeId>". */
    val contentId: String,
    val label: String,
    val fraction: Float,
    val lastWatchedMillis: Long,
)

/** One row in the Histórico — a movie, or a series grouping its episodes. */
data class HistoryEntry(
    val id: String,
    val type: ContentType,
    val title: String,
    val posterUrl: String?,
    /** Most-recently-watched movie/episode fraction. */
    val fraction: Float,
    /** Most-recent play across the whole group. */
    val lastWatchedMillis: Long,
    /** Still present in the current catalog. When false the row shows
     * "Indisponível no catálogo" and doesn't navigate anywhere. */
    val available: Boolean,
    /** "T1 E3" of the most-recent episode, for the collapsed series row. */
    val episodeLabel: String?,
    /** Series only, newest first; empty for a movie. */
    val episodes: List<HistoryEpisode> = emptyList(),
)

data class WatchHistoryUiState(
    val loading: Boolean = true,
    val entries: List<HistoryEntry> = emptyList(),
)

@HiltViewModel
class WatchHistoryViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val contentRepository: ContentRepository,
    private val profileRepository: ProfileRepository,
    private val watchProgressRepository: WatchProgressRepository,
) : ViewModel() {

    private var profileId: String? = null

    private val _uiState = MutableStateFlow(WatchHistoryUiState())
    val uiState: StateFlow<WatchHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileRepository.observeActiveProfile().first()
            val connection = connectionRepository.getDefaultConnection()
            profileId = profile?.id
            if (profile == null) {
                _uiState.value = WatchHistoryUiState(loading = false)
                return@launch
            }

            // The catalog flows are optional context — the history itself is
            // the source of truth (audit #17: it must survive a title leaving
            // the catalog, or the connection being removed).
            val moviesFlow = connection?.let { contentRepository.observeMovies(it.id) }
                ?: kotlinx.coroutines.flow.flowOf(emptyList())
            val seriesFlow = connection?.let { contentRepository.observeSeries(it.id) }
                ?: kotlinx.coroutines.flow.flowOf(emptyList())

            combine(
                watchProgressRepository.observeWatchHistory(profile.id),
                moviesFlow,
                seriesFlow,
            ) { history, movies, series ->
                buildEntries(history, movies.associateBy { it.id }, series.associateBy { it.id })
            }.collect { entries ->
                _uiState.value = WatchHistoryUiState(loading = false, entries = entries)
            }
        }
    }

    private fun buildEntries(
        history: List<WatchProgress>,
        movieById: Map<String, com.auroraplay.iptv.domain.model.Movie>,
        seriesById: Map<String, com.auroraplay.iptv.domain.model.Series>,
    ): List<HistoryEntry> {
        // Insertion order == recency (the DAO returns newest first), so the
        // first row seen for a group is its most-recent play.
        data class Acc(
            var id: String,
            var type: ContentType,
            var title: String,
            var posterUrl: String?,
            var available: Boolean,
            var fraction: Float,
            var lastWatchedMillis: Long,
            var episodeLabel: String?,
            val episodes: MutableList<HistoryEpisode> = mutableListOf(),
        )

        val acc = LinkedHashMap<String, Acc>()
        for (wp in history) {
            val isEpisode = wp.contentId.contains(":")
            val parentId = if (isEpisode) wp.contentId.substringBefore(":") else wp.contentId
            val movie = movieById[parentId]
            val series = seriesById[parentId]
            val label = episodeLabelOf(wp)

            val group = acc.getOrPut(parentId) {
                Acc(
                    id = parentId,
                    type = if (isEpisode || series != null) ContentType.SERIES else ContentType.MOVIE,
                    title = movie?.name
                        ?: series?.name
                        ?: wp.title?.takeIf { it.isNotBlank() }
                        ?: "Conteúdo indisponível",
                    posterUrl = movie?.posterUrl ?: series?.posterUrl ?: wp.posterUrl,
                    available = movie != null || series != null,
                    fraction = wp.fraction,
                    lastWatchedMillis = wp.lastWatchedMillis,
                    episodeLabel = label,
                )
            }
            if (isEpisode) {
                group.episodes += HistoryEpisode(
                    contentId = wp.contentId,
                    label = label ?: "Episódio",
                    fraction = wp.fraction,
                    lastWatchedMillis = wp.lastWatchedMillis,
                )
            }
        }

        return acc.values.map { a ->
            HistoryEntry(
                id = a.id,
                type = a.type,
                title = a.title,
                posterUrl = a.posterUrl,
                fraction = a.fraction,
                lastWatchedMillis = a.lastWatchedMillis,
                available = a.available,
                episodeLabel = a.episodeLabel,
                episodes = a.episodes,
            )
        }
    }

    private fun episodeLabelOf(wp: WatchProgress): String? =
        if (wp.seasonNumber != null && wp.episodeNumber != null) {
            "T${wp.seasonNumber} E${wp.episodeNumber}"
        } else null

    /** Remove one entry: a movie, or a whole series (its row + every episode). */
    fun deleteEntry(entry: HistoryEntry) {
        val id = profileId ?: return
        viewModelScope.launch {
            if (entry.type == ContentType.SERIES) {
                watchProgressRepository.deleteSeriesFromHistory(id, entry.id)
            } else {
                watchProgressRepository.deleteHistoryItem(id, entry.id)
            }
        }
    }

    /** Remove a single episode from a series' Histórico. */
    fun deleteEpisode(contentId: String) {
        val id = profileId ?: return
        viewModelScope.launch { watchProgressRepository.deleteHistoryItem(id, contentId) }
    }

    /** Manual only — never called on a timer or on sign-out. */
    fun clearHistory() {
        val id = profileId ?: return
        viewModelScope.launch { watchProgressRepository.clearWatchHistory(id) }
    }
}
