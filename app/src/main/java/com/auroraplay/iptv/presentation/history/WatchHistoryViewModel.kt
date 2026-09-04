package com.auroraplay.iptv.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.domain.model.ContentType
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

/** One row in the Histórico list — one per title, most-recent play first. */
data class HistoryEntry(
    val id: String,
    val type: ContentType,
    val title: String,
    val posterUrl: String?,
    /** 0f–1f of the last-watched movie / episode. */
    val fraction: Float,
    val lastWatchedMillis: Long,
    /** "T1 E3" for a series, null for a movie. */
    val episodeLabel: String?,
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
            if (profile == null || connection == null) {
                _uiState.value = WatchHistoryUiState(loading = false)
                return@launch
            }

            combine(
                watchProgressRepository.observeWatchHistory(profile.id),
                contentRepository.observeMovies(connection.id),
                contentRepository.observeSeries(connection.id),
            ) { history, movies, series ->
                val movieById = movies.associateBy { it.id }
                val seriesById = series.associateBy { it.id }
                val seen = HashSet<String>()
                val entries = ArrayList<HistoryEntry>(history.size)
                for (wp in history) {
                    val isEpisode = wp.contentId.contains(":")
                    val parentId = if (isEpisode) wp.contentId.substringBefore(":") else wp.contentId
                    if (!seen.add(parentId)) continue
                    when {
                        !isEpisode && movieById.containsKey(parentId) -> {
                            val m = movieById.getValue(parentId)
                            entries += HistoryEntry(
                                id = parentId,
                                type = ContentType.MOVIE,
                                title = m.name,
                                posterUrl = m.posterUrl,
                                fraction = wp.fraction,
                                lastWatchedMillis = wp.lastWatchedMillis,
                                episodeLabel = null,
                            )
                        }
                        seriesById.containsKey(parentId) -> {
                            val s = seriesById.getValue(parentId)
                            val label = if (wp.seasonNumber != null && wp.episodeNumber != null) {
                                "T${wp.seasonNumber} E${wp.episodeNumber}"
                            } else null
                            entries += HistoryEntry(
                                id = parentId,
                                type = ContentType.SERIES,
                                title = s.name,
                                posterUrl = s.posterUrl,
                                fraction = wp.fraction,
                                lastWatchedMillis = wp.lastWatchedMillis,
                                episodeLabel = label,
                            )
                        }
                        // Title no longer in the catalog (unsynced / removed by
                        // the provider): keep it out of the list rather than
                        // showing an untitled row.
                    }
                }
                entries
            }.collect { entries ->
                _uiState.value = WatchHistoryUiState(loading = false, entries = entries)
            }
        }
    }

    /** Manual only — never called on a timer or on sign-out. */
    fun clearHistory() {
        val id = profileId ?: return
        viewModelScope.launch { watchProgressRepository.clearWatchHistory(id) }
    }
}
