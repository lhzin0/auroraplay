package com.auroraplay.iptv.presentation.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.core.util.KidsContentFilter
import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.EpgProgram
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelEpgRow(val channel: Channel, val programs: List<EpgProgram> = emptyList())

data class EpgGuideUiState(
    val isLoading: Boolean = true,
    val rows: List<ChannelEpgRow> = emptyList(),
)

/**
 * "Guia de programação": one row per channel, each with its own short
 * timeline, fetched lazily as rows scroll into view. Not a single
 * absolute-time-synced grid across all channels — Xtream's short EPG isn't
 * reliable enough hour-by-hour to justify that complexity, and a per-channel
 * strip is still a real upgrade over the previous "now/next" only.
 */
@HiltViewModel
class EpgGuideViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val contentRepository: ContentRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EpgGuideUiState())
    val uiState: StateFlow<EpgGuideUiState> = _uiState.asStateFlow()

    private var connectionId: String? = null
    private val fetchedAtMillis = mutableMapOf<String, Long>()

    init {
        viewModelScope.launch {
            val connection = connectionRepository.getDefaultConnection()
            connectionId = connection?.id
            if (connection == null) {
                _uiState.value = EpgGuideUiState(isLoading = false)
                return@launch
            }
            val profile = profileRepository.observeActiveProfile().first()

            contentRepository.observeChannels(connection.id).collect { channels ->
                val visible = if (profile?.isKids == true) {
                    channels.filter { KidsContentFilter.isKidsAppropriate(it.categoryName) }
                } else channels

                // Preserve whatever timelines were already fetched for
                // channels that are still present, instead of resetting
                // every row back to empty each time the catalog re-syncs.
                val existingById = _uiState.value.rows.associateBy { it.channel.id }
                _uiState.value = EpgGuideUiState(
                    isLoading = false,
                    rows = visible.map { channel ->
                        existingById[channel.id]?.copy(channel = channel) ?: ChannelEpgRow(channel)
                    },
                )
            }
        }
    }

    fun ensureTimeline(channelId: String) {
        val connId = connectionId ?: return
        val now = System.currentTimeMillis()
        val lastFetch = fetchedAtMillis[channelId]
        if (lastFetch != null && now - lastFetch < 5 * 60_000L) return
        fetchedAtMillis[channelId] = now

        viewModelScope.launch {
            val programs = contentRepository.getEpgTimeline(connId, channelId, limit = 6)
            if (programs.isEmpty()) return@launch
            val state = _uiState.value
            _uiState.value = state.copy(
                rows = state.rows.map { if (it.channel.id == channelId) it.copy(programs = programs) else it },
            )
        }
    }
}
