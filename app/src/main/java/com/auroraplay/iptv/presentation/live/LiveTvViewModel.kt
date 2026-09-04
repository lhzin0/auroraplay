package com.auroraplay.iptv.presentation.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.domain.model.Category
import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.FavoriteRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import com.auroraplay.iptv.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveTvUiState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val channels: List<Channel> = emptyList(),
    val selectedChannel: Channel? = null,
    val favoriteIds: Set<String> = emptySet(),
    val showOnlyFavorites: Boolean = false,
    val query: String = "",
) {
    /** Channel list after the favorites filter and the page's own search are applied. */
    val visibleChannels: List<Channel>
        get() {
            val afterFavorites = if (showOnlyFavorites) channels.filter { favoriteIds.contains(it.id) } else channels
            return if (query.isBlank()) {
                afterFavorites
            } else {
                afterFavorites.filter { it.name.contains(query.trim(), ignoreCase = true) }
            }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val contentRepository: ContentRepository,
    private val profileRepository: ProfileRepository,
    private val favoriteRepository: FavoriteRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val contentPolicy: com.auroraplay.iptv.domain.policy.ContentPolicy,
) : ViewModel() {

    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private var activeProfileId: String? = null
    private var activeConnectionId: String? = null
    // Throttles per-channel EPG fetches — LiveTvScreen calls ensureEpg() every
    // time a channel row composes, which happens a lot while scrolling, but
    // "now/next" data is only worth re-fetching every few minutes.
    private val epgFetchedAtMillis = mutableMapOf<String, Long>()

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Re-drive everything when the active profile OR the default
            // connection changes (audit #11) — collectLatest cancels the
            // previous connection's flows.
            combine(
                profileRepository.observeActiveProfile(),
                connectionRepository.observeDefaultConnection(),
            ) { profile, connection -> profile to connection }
                .distinctUntilChanged()
                .collectLatest { (profile, connection) ->
                    activeProfileId = profile?.id
                    activeConnectionId = connection?.id
                    if (connection == null) {
                        _uiState.value = LiveTvUiState(isLoading = false)
                        return@collectLatest
                    }
                    // A category id belongs to one playlist — drop it and any
                    // channel selection so nothing from the old connection lingers.
                    selectedCategoryId.value = null
                    epgFetchedAtMillis.clear()
                    _uiState.value = LiveTvUiState(isLoading = true)

                    val categoriesFlow = contentRepository.observeCategories(connection.id, ContentType.LIVE)
                        .map { list ->
                            if (profile?.isKids == true) list.filter { contentPolicy.allowsFields(true, it.name) } else list
                        }
                    val channelsFlow = selectedCategoryId.flatMapLatest { contentRepository.observeChannels(connection.id, it) }
                        .map { list -> contentPolicy.channels(profile?.isKids == true, list) }
                    val favoritesFlow = profile?.let { favoriteRepository.observeFavorites(connection.id, it.id, ContentType.LIVE) }
                        ?: flowOf(emptyList())

                    combine(categoriesFlow, channelsFlow, favoritesFlow, selectedCategoryId) { categories, channels, favorites, selectedId ->
                        val current = _uiState.value
                        _uiState.value = current.copy(
                            isLoading = false,
                            categories = categories,
                            channels = channels,
                            selectedCategoryId = selectedId,
                            favoriteIds = favorites.map { it.contentId }.toSet(),
                            // Keep the current selection if it's still in the list; otherwise fall back.
                            // Deliberately no auto-select: opening the tab used to
                            // start buffering the first channel in the list, which is
                            // why the screen greeted the user with a black rectangle
                            // and a spinner above the title.
                            selectedChannel = channels.find { it.id == current.selectedChannel?.id },
                        )
                    }.collect {}
                }
        }
    }

    fun selectCategory(categoryId: String?) {
        _uiState.value = _uiState.value.copy(showOnlyFavorites = false)
        selectedCategoryId.value = categoryId
    }

    fun toggleFavoritesFilter() {
        // "Favoritos" is exclusive with a category chip, the same way selectCategory()
        // clears showOnlyFavorites — otherwise a category chosen earlier stays selected
        // underneath, so turning Favoritos on shows only that category's favorites (often
        // empty) instead of every favorited channel, and both chips render as active.
        selectedCategoryId.value = null
        _uiState.value = _uiState.value.copy(showOnlyFavorites = !_uiState.value.showOnlyFavorites)
    }

    fun updateQuery(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
    }

    fun selectChannel(channel: Channel) {
        _uiState.value = _uiState.value.copy(selectedChannel = channel)
    }

    fun toggleFavorite(channel: Channel) {
        val profileId = activeProfileId ?: return
        val connectionId = activeConnectionId ?: return
        viewModelScope.launch { toggleFavoriteUseCase(connectionId, profileId, channel.id, ContentType.LIVE) }
    }

    /**
     * Fetches "now/next" for one channel on demand. Xtream's short EPG was
     * previously never called at all — currentProgram/nextProgram stayed
     * null everywhere they were read. Calling this from each visible channel
     * row (LazyColumn only composes what's on screen) means the cost scales
     * with what the person can actually see, not the whole channel list.
     */
    fun ensureEpg(channel: Channel) {
        val connectionId = activeConnectionId ?: return
        val now = System.currentTimeMillis()
        val lastFetch = epgFetchedAtMillis[channel.id]
        if (lastFetch != null && now - lastFetch < EPG_REFRESH_INTERVAL_MS) return
        epgFetchedAtMillis[channel.id] = now

        viewModelScope.launch {
            val (current, next) = contentRepository.getShortEpg(connectionId, channel.id)
            if (current == null && next == null) return@launch
            val updated = channel.copy(currentProgram = current, nextProgram = next)
            val state = _uiState.value
            _uiState.value = state.copy(
                channels = state.channels.map { if (it.id == updated.id) updated else it },
                selectedChannel = if (state.selectedChannel?.id == updated.id) updated else state.selectedChannel,
            )
        }
    }

    private companion object {
        const val EPG_REFRESH_INTERVAL_MS = 5 * 60_000L
    }
}
