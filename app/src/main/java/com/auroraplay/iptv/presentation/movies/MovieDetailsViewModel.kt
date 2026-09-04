package com.auroraplay.iptv.presentation.movies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.data.repository.MetadataEnricher
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.Movie
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.FavoriteRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import com.auroraplay.iptv.domain.usecase.ToggleFavoriteUseCase
import com.auroraplay.iptv.player.download.DownloadTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MovieDetailsUiState(
    val isLoading: Boolean = true,
    val movie: Movie? = null,
    val isFavorite: Boolean = false,
    val similar: List<Movie> = emptyList(),
    val errorMessage: String? = null,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    /** False on many Xtream VOD streams that never send a Content-Length —
     * there's no percentage to compute then, and showing one anyway would be
     * a permanent, fake "0%" rather than an honest signal. */
    val hasKnownDownloadPercentage: Boolean = true,
    val downloadBytesDownloaded: Long = 0L,
    /** Playback progress for this profile, when the title was partly watched. */
    val resumeFraction: Float = 0f,
    val resumePositionMillis: Long = 0L,
    val remainingLabel: String? = null,
    val trailerYoutubeId: String? = null,
)

@HiltViewModel
class MovieDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val connectionRepository: ConnectionRepository,
    private val contentRepository: ContentRepository,
    private val profileRepository: ProfileRepository,
    private val favoriteRepository: FavoriteRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val downloadTracker: DownloadTracker,
    private val watchProgressRepository: com.auroraplay.iptv.domain.repository.WatchProgressRepository,
    private val metadataEnricher: MetadataEnricher,
    private val contentPolicy: com.auroraplay.iptv.domain.policy.ContentPolicy,
) : ViewModel() {

    private val movieId: String = checkNotNull(savedStateHandle["movieId"])
    private var activeProfileId: String? = null
    private var activeConnectionId: String? = null

    private val _uiState = MutableStateFlow(MovieDetailsUiState())
    val uiState: StateFlow<MovieDetailsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val connection = connectionRepository.getDefaultConnection()
            if (connection == null) {
                _uiState.value = MovieDetailsUiState(isLoading = false, errorMessage = "Não foi possível conectar ao servidor.")
                return@launch
            }
            val profile = profileRepository.observeActiveProfile().first()
            activeProfileId = profile?.id
            activeConnectionId = connection.id

            // The freshest movie we have. Seeded from the local catalog row (a
            // single indexed DB read — instant), then upgraded in the background
            // once get_vod_info / TMDB come back. The page paints on the first,
            // not the round-trips — that wait was the "demora para abrir".
            val movieFlow = MutableStateFlow(contentRepository.getCachedMovie(connection.id, movieId))
            if (movieFlow.value == null) {
                val fetched = contentRepository.getMovieDetail(connection.id, movieId)
                if (fetched == null) {
                    _uiState.value = MovieDetailsUiState(isLoading = false, errorMessage = "Não foi possível carregar o conteúdo.")
                    return@launch
                }
                movieFlow.value = fetched
            } else {
                launch {
                    contentRepository.getMovieDetail(connection.id, movieId)?.let { movieFlow.value = it }
                }
            }

            // A kids profile must not reach a non-kids title through a deep link
            // or a stale recommendation.
            val isKids = profile?.isKids == true
            if (isKids && movieFlow.value?.let { contentPolicy.allows(true, it) } != true) {
                _uiState.value = MovieDetailsUiState(isLoading = false, errorMessage = "Este conteúdo não está disponível neste perfil.")
                return@launch
            }

            val progress = profile?.let { watchProgressRepository.getProgress(connection.id, it.id, movieId, com.auroraplay.iptv.domain.model.ContentType.MOVIE) }
            val remaining = progress?.let { p ->
                val remainingSeconds = ((p.durationMillis - p.positionMillis) / 1000).coerceAtLeast(0)
                val minutes = remainingSeconds / 60
                if (minutes > 0) "Tempo restante: ${minutes}m" else null
            }

            // "Você também pode gostar" scans the whole catalog — kept off the
            // critical path so it can never delay the first paint.
            val similarFlow = MutableStateFlow<List<Movie>>(emptyList())
            launch {
                val genre = movieFlow.value?.genre
                val allMovies = contentPolicy.movies(isKids, contentRepository.observeMovies(connection.id).first())
                similarFlow.value = allMovies
                    .filter { it.id != movieId && it.genre != null && it.genre == genre }
                    .take(12)
            }

            launch {
                val base = movieFlow.value ?: return@launch
                val trailerYoutubeId = metadataEnricher.youtubeTrailerForMovie(base.name, base.year)
                _uiState.update { it.copy(trailerYoutubeId = trailerYoutubeId) }
            }

            val favoriteFlow = if (profile != null) favoriteRepository.isFavorite(connection.id, profile.id, movieId, com.auroraplay.iptv.domain.model.ContentType.MOVIE) else flowOf(false)
            combine(movieFlow, similarFlow, favoriteFlow, downloadTracker.downloads) { movie, similar, isFav, downloads ->
                val download = downloads[movieId]
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    movie = movie,
                    isFavorite = isFav,
                    similar = similar,
                    isDownloaded = download?.status == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED,
                    isDownloading = download?.status == androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING,
                    downloadProgress = (download?.progressPercent ?: 0f) / 100f,
                    hasKnownDownloadPercentage = download?.hasKnownPercentage ?: true,
                    downloadBytesDownloaded = download?.bytesDownloaded ?: 0L,
                    resumeFraction = progress?.fraction ?: 0f,
                    resumePositionMillis = progress?.positionMillis ?: 0L,
                    remainingLabel = remaining,
                )
            }.collect {}
        }
    }

    fun toggleFavorite() {
        val profileId = activeProfileId ?: return
        val connectionId = activeConnectionId ?: return
        viewModelScope.launch { toggleFavoriteUseCase(connectionId, profileId, movieId, ContentType.MOVIE) }
    }

    fun toggleDownload() {
        val movie = _uiState.value.movie ?: return
        if (_uiState.value.isDownloaded || _uiState.value.isDownloading) {
            downloadTracker.removeDownload(movie.id)
        } else {
            downloadTracker.startDownload(
                contentId = movie.id,
                title = movie.name,
                streamUrl = movie.streamUrl,
                playbackContentType = "MOVIE",
                playbackId = movie.id,
                posterUrl = movie.posterUrl,
                groupKey = "movie:${movie.id}",
                groupTitle = movie.name,
            )
        }
    }
}
