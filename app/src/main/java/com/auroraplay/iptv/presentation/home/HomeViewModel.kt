package com.auroraplay.iptv.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.HomeContent
import com.auroraplay.iptv.domain.model.MediaItem
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.FavoriteRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import com.auroraplay.iptv.domain.repository.WatchProgressRepository
import com.auroraplay.iptv.domain.usecase.GetHomeContentUseCase
import com.auroraplay.iptv.domain.usecase.SyncContentUseCase
import com.auroraplay.iptv.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@androidx.compose.runtime.Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val hasConnection: Boolean = true,
    val activeProfileId: String? = null,
    val activeConnectionId: String? = null,
    val content: HomeContent? = null,
    val favoriteIds: Set<String> = emptySet(),
    val isKidsProfile: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val profileRepository: ProfileRepository,
    private val getHomeContentUseCase: GetHomeContentUseCase,
    private val syncContentUseCase: SyncContentUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val favoriteRepository: FavoriteRepository,
    private val watchProgressRepository: WatchProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                profileRepository.observeActiveProfile(),
                connectionRepository.observeConnections(),
            ) { profile, connections -> profile to connections }.collect { (profile, connections) ->
                val connection = connections.find { it.isDefault } ?: connections.firstOrNull()
                if (profile == null || connection == null) {
                    _uiState.value = HomeUiState(isLoading = false, hasConnection = connection != null)
                    return@collect
                }
                _uiState.value = _uiState.value.copy(
                    activeProfileId = profile.id,
                    activeConnectionId = connection.id,
                    hasConnection = true,
                    isKidsProfile = profile.isKids,
                )
                loadHomeContent(connection.id, profile.id, profile.isKids)
            }
        }
    }

    private var contentJob: kotlinx.coroutines.Job? = null

    private fun loadHomeContent(connectionId: String, profileId: String, isKids: Boolean) {
        // Cancel any previous collection so switching profile/connection
        // doesn't leave two flows writing into the same UI state.
        contentJob?.cancel()
        contentJob = viewModelScope.launch {
            combine(
                getHomeContentUseCase(connectionId, profileId, isKids),
                favoriteRepository.observeFavorites(connectionId, profileId),
            ) { content, favorites ->
                content to favorites.map { it.contentId }.toSet()
            }.collect { (content, favoriteIds) ->
                _uiState.value = _uiState.value.copy(isLoading = false, content = content, favoriteIds = favoriteIds)
            }
        }
    }

    /**
     * "Remover de Continuar assistindo". Only drops the card from that rail —
     * history, watched time and progress are kept, so resuming the title later
     * picks up where it stopped. For a series, every episode is removed from
     * the rail at once.
     */
    fun removeFromContinueWatching(item: MediaItem) {
        val profileId = _uiState.value.activeProfileId ?: return
        val connectionId = _uiState.value.activeConnectionId ?: return
        if (item is MediaItem.ChannelItem) return
        val isSeries = item is MediaItem.SeriesItem
        viewModelScope.launch {
            watchProgressRepository.removeFromContinueWatching(connectionId, profileId, item.id, isSeries)
        }
    }

    fun toggleFavorite(item: MediaItem) {
        val profileId = _uiState.value.activeProfileId ?: return
        val connectionId = _uiState.value.activeConnectionId ?: return
        val type = when (item) {
            is MediaItem.ChannelItem -> ContentType.LIVE
            is MediaItem.MovieItem -> ContentType.MOVIE
            is MediaItem.SeriesItem -> ContentType.SERIES
        }
        viewModelScope.launch { toggleFavoriteUseCase(connectionId, profileId, item.id, type) }
    }

    /** Pull-to-refresh: re-syncs the active connection against the server.
     * Local content already updates reactively as soon as the sync writes
     * fresh rows, so [onDone] only needs to end the refresh spinner. */
    fun refresh(onDone: () -> Unit) {
        val connectionId = _uiState.value.activeConnectionId
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
