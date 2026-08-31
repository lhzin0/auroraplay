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
) : ViewModel() {

    private val movieId: String = checkNotNull(savedStateHandle["movieId"])
    private var activeProfileId: String? = null

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

            val movie = contentRepository.getMovieDetail(connection.id, movieId)
            if (movie == null) {
                _uiState.value = MovieDetailsUiState(isLoading = false, errorMessage = "Não foi possível carregar o conteúdo.")
                return@launch
            }

            val allMovies = contentRepository.observeMovies(connection.id).first()
            val similar = allMovies.filter { it.id != movie.id && it.genre != null && it.genre == movie.genre }.take(12)

            val progress = profile?.let { watchProgressRepository.getProgress(it.id, movie.id) }
            val remaining = progress?.let { p ->
                val remainingSeconds = ((p.durationMillis - p.positionMillis) / 1000).coerceAtLeast(0)
                val minutes = remainingSeconds / 60
                if (minutes > 0) "Tempo restante: ${minutes}m" else null
            }

            launch {
                val trailerYoutubeId = metadataEnricher.youtubeTrailerForMovie(movie.name, movie.year)
                _uiState.update { it.copy(trailerYoutubeId = trailerYoutubeId) }
            }

            val favoriteFlow = if (profile != null) favoriteRepository.isFavorite(profile.id, movie.id) else flowOf(false)
            combine(favoriteFlow, downloadTracker.downloads) { isFav, downloads ->
                val download = downloads[movie.id]
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
        viewModelScope.launch { toggleFavoriteUseCase(profileId, movieId, ContentType.MOVIE) }
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
