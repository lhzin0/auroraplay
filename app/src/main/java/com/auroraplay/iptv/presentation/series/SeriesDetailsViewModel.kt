package com.auroraplay.iptv.presentation.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.Episode
import com.auroraplay.iptv.domain.model.Series
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.FavoriteRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import com.auroraplay.iptv.data.repository.MetadataEnricher
import com.auroraplay.iptv.domain.repository.WatchProgressRepository
import com.auroraplay.iptv.domain.usecase.ToggleFavoriteUseCase
import com.auroraplay.iptv.player.download.DownloadTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeriesDetailsUiState(
    val isLoading: Boolean = true,
    val series: Series? = null,
    val isFavorite: Boolean = false,
    val selectedSeasonNumber: Int = 1,
    val errorMessage: String? = null,
    // Last episode + position saved for this profile. The detail page uses
    // this for a true one-tap resume action.
    val resumeEpisodeId: String? = null,
    val resumeSeasonNumber: Int? = null,
    val resumeEpisodeNumber: Int? = null,
    val resumePositionMillis: Long = 0L,
    val resumeDurationMillis: Long = 0L,
    /** Per-episode download state, keyed by episode id — lets each row show
     * its own download icon without every row re-deriving it independently. */
    val downloadedEpisodeIds: Set<String> = emptySet(),
    val downloadingEpisodeIds: Set<String> = emptySet(),
    val downloadProgressByEpisodeId: Map<String, Float> = emptyMap(),
    /** False entries mean the source never sent a Content-Length — no
     * percentage to compute, show bytes downloaded instead of a fake 0%. */
    val downloadHasKnownPercentageByEpisodeId: Map<String, Boolean> = emptyMap(),
    val downloadBytesByEpisodeId: Map<String, Long> = emptyMap(),
    val similar: List<Series> = emptyList(),
    /** Official YouTube trailer id from TMDB — never an Xtream/episode URL. */
    val trailerYoutubeId: String? = null,
)

@HiltViewModel
class SeriesDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val connectionRepository: ConnectionRepository,
    private val contentRepository: ContentRepository,
    private val profileRepository: ProfileRepository,
    private val favoriteRepository: FavoriteRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val watchProgressRepository: WatchProgressRepository,
    private val downloadTracker: DownloadTracker,
    private val metadataEnricher: MetadataEnricher,
    private val contentPolicy: com.auroraplay.iptv.domain.policy.ContentPolicy,
) : ViewModel() {

    private val seriesId: String = checkNotNull(savedStateHandle["seriesId"])
    private var activeProfileId: String? = null
    // Kept only so toggleEpisodeDownload/downloadSeason can label a download
    // (series name + poster) without accepting the whole Series object as a
    // parameter everywhere. The poster travels with the download so the
    // Downloads screen can render one card per series, offline, no catalog.
    private var loadedSeriesName: String? = null
    private var loadedSeriesPoster: String? = null

    private val _uiState = MutableStateFlow(SeriesDetailsUiState())
    val uiState: StateFlow<SeriesDetailsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val connection = connectionRepository.getDefaultConnection()
            if (connection == null) {
                _uiState.value = SeriesDetailsUiState(isLoading = false, errorMessage = "Não foi possível conectar ao servidor.")
                return@launch
            }
            val profile = profileRepository.observeActiveProfile().first()
            activeProfileId = profile?.id

            // Seeded from the local row + already-cached episodes (instant),
            // then upgraded once get_series_info / TMDB return. First paint no
            // longer waits on those round-trips.
            val seriesFlow = MutableStateFlow(contentRepository.getCachedSeries(connection.id, seriesId))
            if (seriesFlow.value == null) {
                val fetched = contentRepository.getSeriesDetail(connection.id, seriesId)
                if (fetched == null) {
                    _uiState.value = SeriesDetailsUiState(isLoading = false, errorMessage = "Não foi possível carregar o conteúdo.")
                    return@launch
                }
                seriesFlow.value = fetched
            } else {
                launch {
                    contentRepository.getSeriesDetail(connection.id, seriesId)?.let { seriesFlow.value = it }
                }
            }
            val isKids = profile?.isKids == true
            if (isKids && seriesFlow.value?.let { contentPolicy.allows(true, it) } != true) {
                _uiState.value = SeriesDetailsUiState(isLoading = false, errorMessage = "Este conteúdo não está disponível neste perfil.")
                return@launch
            }
            loadedSeriesName = seriesFlow.value?.name
            loadedSeriesPoster = seriesFlow.value?.posterUrl

            val similarFlow = MutableStateFlow<List<Series>>(emptyList())
            launch {
                val genre = seriesFlow.value?.genre
                val allSeries = contentPolicy.series(isKids, contentRepository.observeSeries(connection.id).first())
                similarFlow.value = allSeries
                    .filter { it.id != seriesId && it.genre != null && it.genre == genre }
                    .take(12)
            }

            // Resolve the trailer off the critical path so the page renders
            // immediately and just gains the trailer tab when it arrives.
            launch {
                val base = seriesFlow.value ?: return@launch
                loadedSeriesName = base.name
                loadedSeriesPoster = base.posterUrl
                val trailer = metadataEnricher.youtubeTrailerForSeries(base.name, base.year)
                _uiState.update { it.copy(trailerYoutubeId = trailer) }
            }

            val favoriteFlow = if (profile != null) favoriteRepository.isFavorite(profile.id, seriesId, com.auroraplay.iptv.domain.model.ContentType.SERIES) else flowOf(false)
            // Most recently watched episode of this series, recomputed if the
            // episode list grows when the full fetch lands. Every episode is
            // eligible (even near-complete ones) so Resume never skips a chapter.
            val latestProgressFlow = seriesFlow.map { s ->
                val p = profile ?: return@map null
                s?.seasons.orEmpty()
                    .flatMap { it.episodes }
                    .mapNotNull { episode -> watchProgressRepository.getProgress(p.id, "$seriesId:${episode.id}", com.auroraplay.iptv.domain.model.ContentType.SERIES) }
                    .maxByOrNull { it.lastWatchedMillis }
            }

            combine(seriesFlow, similarFlow, latestProgressFlow, favoriteFlow, downloadTracker.downloads) { series, similar, latestProgress, isFav, downloads ->
                if (series == null) return@combine
                val episodeIds = series.seasons.flatMap { it.episodes }.map { it.id }.toSet()
                val relevant = downloads.filterKeys { it in episodeIds }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    series = series,
                    isFavorite = isFav,
                    similar = similar,
                    selectedSeasonNumber = _uiState.value.selectedSeasonNumber.takeIf { it != 1 }
                        ?: latestProgress?.seasonNumber
                        ?: (series.seasons.firstOrNull()?.seasonNumber ?: 1),
                    resumeEpisodeId = latestProgress?.contentId?.substringAfter(":")?.ifBlank { null },
                    resumeSeasonNumber = latestProgress?.seasonNumber,
                    resumeEpisodeNumber = latestProgress?.episodeNumber,
                    resumePositionMillis = latestProgress?.positionMillis ?: 0L,
                    resumeDurationMillis = latestProgress?.durationMillis ?: 0L,
                    downloadedEpisodeIds = relevant.filterValues { it.status == Download.STATE_COMPLETED }.keys,
                    downloadingEpisodeIds = relevant.filterValues { it.status == Download.STATE_DOWNLOADING }.keys,
                    downloadProgressByEpisodeId = relevant.mapValues { it.value.progressPercent / 100f },
                    downloadHasKnownPercentageByEpisodeId = relevant.mapValues { it.value.hasKnownPercentage },
                    downloadBytesByEpisodeId = relevant.mapValues { it.value.bytesDownloaded },
                )
            }.collect {}
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _uiState.value = _uiState.value.copy(selectedSeasonNumber = seasonNumber)
    }

    fun toggleFavorite() {
        val profileId = activeProfileId ?: return
        viewModelScope.launch { toggleFavoriteUseCase(profileId, seriesId, ContentType.SERIES) }
    }

    /** Per-episode download toggle — mirrors MovieDetailsViewModel.toggleDownload,
     * just keyed by the episode's own id instead of a movie id. */
    fun toggleEpisodeDownload(episode: Episode) {
        val state = _uiState.value
        if (episode.id in state.downloadedEpisodeIds || episode.id in state.downloadingEpisodeIds) {
            downloadTracker.removeDownload(episode.id)
        } else {
            startEpisodeDownload(episode)
        }
    }

    /** "Baixar temporada": starts whichever episodes in [episodes] aren't
     * already downloaded or downloading — never re-queues one that's
     * already in progress just because the button was tapped again. */
    fun downloadSeason(episodes: List<Episode>) {
        val state = _uiState.value
        episodes.forEach { episode ->
            if (episode.id !in state.downloadedEpisodeIds && episode.id !in state.downloadingEpisodeIds) {
                startEpisodeDownload(episode)
            }
        }
    }

    /** Single place that queues an episode, so the grouping metadata (series
     * key + name + poster, and the per-episode sort order) is attached
     * identically whether it came from a single tap or "Baixar temporada". */
    private fun startEpisodeDownload(episode: Episode) {
        downloadTracker.startDownload(
            contentId = episode.id,
            title = episodeItemLabel(episode),
            streamUrl = episode.streamUrl,
            playbackContentType = "SERIES",
            playbackId = "${episode.seriesId}:${episode.id}",
            posterUrl = loadedSeriesPoster ?: episode.thumbnailUrl,
            groupKey = "series:${episode.seriesId}",
            groupTitle = loadedSeriesName ?: "Série",
            sortKey = episode.seasonNumber * 1000 + episode.episodeNumber,
        )
    }

    /** Per-row label under the series card — the series name is already the
     * card header, so the row only needs "T1E2 • Título do episódio". */
    private fun episodeItemLabel(episode: Episode): String =
        "T${episode.seasonNumber}E${episode.episodeNumber} • ${episode.title}"
}
