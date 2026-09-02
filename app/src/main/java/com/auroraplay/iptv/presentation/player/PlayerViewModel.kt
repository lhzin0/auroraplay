package com.auroraplay.iptv.presentation.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.ui.AspectRatioFrameLayout
import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.EpgProgram
import com.auroraplay.iptv.domain.model.Episode
import com.auroraplay.iptv.domain.model.WatchProgress
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.FavoriteRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import com.auroraplay.iptv.domain.repository.WatchProgressRepository
import com.auroraplay.iptv.domain.usecase.SaveWatchProgressUseCase
import com.auroraplay.iptv.domain.usecase.ToggleFavoriteUseCase
import com.auroraplay.iptv.player.PlayerManager
import com.auroraplay.iptv.player.ThumbnailPreviewGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

data class PlayerLoadState(
    val title: String = "",
    val subtitle: String? = null,
    val streamUrl: String? = null,
    val isLive: Boolean = false,
    val contentType: ContentType = ContentType.MOVIE,
    val contentId: String = "",
    val nextEpisode: Episode? = null,
    val liveChannels: List<Channel> = emptyList(),
    val resumePositionMillis: Long = 0L,
    val isFavorite: Boolean = false,
    val resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    val currentProgramLabel: String? = null,
    val programProgress: Float? = null,
    /** Dubbed/subtitled sibling streams of the current movie (provider split
     * "DUBLADO" / "LEGENDADO" into separate entries). 0–1 items = nothing to
     * switch between; the player's audio picker shows them when there are ≥2. */
    val audioVariants: List<com.auroraplay.iptv.domain.model.AudioStreamVariant> = emptyList(),
    val loadError: String? = null,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerManager: PlayerManager,
    private val connectionRepository: ConnectionRepository,
    private val contentRepository: ContentRepository,
    private val profileRepository: ProfileRepository,
    private val favoriteRepository: FavoriteRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val saveWatchProgressUseCase: SaveWatchProgressUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val thumbnailPreviewGenerator: ThumbnailPreviewGenerator,
    private val settingsRepository: com.auroraplay.iptv.domain.repository.SettingsRepository,
) : ViewModel() {

    /** ±10 or ±5 — drives the seek buttons' jump and their "10"/"5" glyph. */
    private val _seekSeconds = MutableStateFlow(10)
    val seekSeconds: StateFlow<Int> = _seekSeconds.asStateFlow()

    init {
        // Whatever language someone picked once on any video keeps getting
        // picked automatically from here on — including on a brand new
        // ExoPlayer instance in a future app session, since this reads from
        // persisted settings rather than a value carried over in memory.
        viewModelScope.launch {
            val settings = runCatching { settingsRepository.observeSettings().first() }.getOrNull()
            if (settings?.preferredAudioLang != null || settings?.preferredSubtitleLang != null) {
                playerManager.setPreferredLanguages(settings.preferredAudioLang, settings.preferredSubtitleLang)
            }
        }
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                _seekSeconds.value = settings.seekSeconds
                playerManager.seekIncrementMs = settings.seekSeconds * 1000L
                autoPlayNextEnabled = settings.autoPlayNext
            }
        }
    }

    /** From Settings > Reprodução > "Próximo episódio automático". */
    @Volatile
    private var autoPlayNextEnabled: Boolean = true
    private var autoAdvancedForUrl: String? = null
    private var autoNextCancelledForUrl: String? = null

    /** Seconds left before the player jumps to the next episode, or null when
     * no auto-advance is pending. Drives the small on-player countdown. */
    private val _autoNextInSeconds = MutableStateFlow<Int?>(null)
    val autoNextInSeconds: StateFlow<Int?> = _autoNextInSeconds.asStateFlow()

    /**
     * Auto-advance heuristic: Xtream gives us no chapter/credits markers, so
     * "detect the end credits" becomes "the last [CREDITS_WINDOW_MS] of the
     * episode". While inside that window a countdown is published; it fires
     * (once per stream) when it reaches zero. The viewer can dismiss it for
     * the current episode or trigger the jump immediately.
     */
    private fun maybeAutoAdvance() {
        val state = _loadState.value
        val url = state.streamUrl
        val eligible = autoPlayNextEnabled && url != null && state.nextEpisode != null &&
            state.contentType == ContentType.SERIES &&
            autoAdvancedForUrl != url && autoNextCancelledForUrl != url
        if (!eligible) {
            if (_autoNextInSeconds.value != null) _autoNextInSeconds.value = null
            return
        }
        val duration = playerManager.currentDuration()
        val position = playerManager.currentPosition()
        if (duration <= 0L) {
            if (_autoNextInSeconds.value != null) _autoNextInSeconds.value = null
            return
        }
        val remainingMs = duration - position
        if (remainingMs in 1..CREDITS_WINDOW_MS) {
            val secs = ((remainingMs + 999L) / 1000L).toInt()
            if (secs <= 0) {
                autoAdvancedForUrl = url
                _autoNextInSeconds.value = null
                playNextEpisode()
            } else {
                _autoNextInSeconds.value = secs
            }
        } else if (_autoNextInSeconds.value != null) {
            _autoNextInSeconds.value = null
        }
    }

    /** Viewer dismissed the countdown — no auto-jump for this episode. */
    fun cancelAutoNext() {
        autoNextCancelledForUrl = _loadState.value.streamUrl
        _autoNextInSeconds.value = null
    }

    /** Viewer chose to jump now instead of waiting out the countdown. */
    fun playNextEpisodeNow() {
        autoAdvancedForUrl = _loadState.value.streamUrl
        _autoNextInSeconds.value = null
        playNextEpisode()
    }

    private val _loadState = MutableStateFlow(PlayerLoadState())
    val loadState: StateFlow<PlayerLoadState> = _loadState.asStateFlow()

    // Scrubbing preview: kept separate from PlayerLoadState since it changes
    // on every drag pixel and has nothing to do with what content is loaded.
    private val _scrubThumbnail = MutableStateFlow<android.graphics.Bitmap?>(null)
    val scrubThumbnail: StateFlow<android.graphics.Bitmap?> = _scrubThumbnail.asStateFlow()
    private var scrubJob: Job? = null
    private var lastScrubBucket: Long = Long.MIN_VALUE

    /** Full-day schedule for the channel currently playing, fetched on demand
     * when the "Programação" sheet opens (get_short_epg with a larger limit). */
    private val _channelEpg = MutableStateFlow<List<EpgProgram>>(emptyList())
    val channelEpg: StateFlow<List<EpgProgram>> = _channelEpg.asStateFlow()

    fun loadChannelEpg() {
        val id = _loadState.value.contentId
        if (id.isBlank() || !_loadState.value.isLive) return
        viewModelScope.launch {
            val conn = connectionRepository.getDefaultConnection() ?: return@launch
            _channelEpg.value = runCatching {
                contentRepository.getEpgTimeline(conn.id, id, limit = 24)
            }.getOrDefault(emptyList())
        }
    }

    private var activeProfileId: String? = null
    private var progressLoopRunning = false
    private var currentBrightness = 0.5f

    private val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
    )

    fun load(contentType: ContentType, contentId: String) {
        if (contentId.isBlank()) {
            _loadState.value = _loadState.value.copy(loadError = "Conteúdo inválido.")
            return
        }
        _autoNextInSeconds.value = null

        viewModelScope.launch {
            _loadState.value = _loadState.value.copy(loadError = null)
            runCatching {
                val connection = connectionRepository.getDefaultConnection()
                    ?: error("Nenhuma conexão ativa foi encontrada.")
                val profile = profileRepository.observeActiveProfile().first()
                activeProfileId = profile?.id

                when (contentType) {
                    ContentType.LIVE -> loadLive(connection.id, contentId, profile?.id)
                    ContentType.MOVIE -> loadMovie(connection.id, contentId, profile?.id)
                    ContentType.SERIES -> loadSeries(connection.id, contentId, profile?.id)
                }

                val url = _loadState.value.streamUrl?.trim()
                if (url.isNullOrBlank()) {
                    error("O conteúdo não possui um endereço de reprodução válido.")
                }

                // Warm only valid VOD/episode URLs. Never let a preview decoder
                // failure crash the player screen.
                if (!_loadState.value.isLive) {
                    runCatching { thumbnailPreviewGenerator.prewarm(url) }
                }
                startProgressLoop()
            }.onFailure { error ->
                _loadState.value = _loadState.value.copy(
                    loadError = error.message?.takeIf { it.isNotBlank() }
                        ?: "Não foi possível carregar este conteúdo."
                )
            }
        }
    }

    private suspend fun loadLive(connectionId: String, contentId: String, profileId: String?) {
        val channels = contentRepository.observeChannels(connectionId).first()
        val channel = channels.find { it.id == contentId } ?: channels.firstOrNull() ?: return
        val isFav = profileId?.let { favoriteRepository.isFavorite(it, channel.id).first() } ?: false
        // channel.currentProgram is always null straight out of the DB — the
        // catalog sync never calls the short-EPG endpoint, so this is the
        // only place that actually populates "now playing" for the player.
        // "Next" isn't shown anywhere in the player UI yet, so it's discarded
        // here rather than threaded through PlayerLoadState for no reader.
        val (current, _) = runCatching { contentRepository.getShortEpg(connectionId, channel.id) }.getOrDefault(null to null)
        _loadState.value = _loadState.value.copy(
            title = channel.name,
            subtitle = channel.categoryName,
            streamUrl = channel.streamUrl,
            isLive = true,
            contentType = ContentType.LIVE,
            contentId = channel.id,
            liveChannels = channels,
            resumePositionMillis = 0L,
            isFavorite = isFav,
            currentProgramLabel = current?.title,
            programProgress = current?.progressFraction(),
            audioVariants = emptyList(),
        )
    }

    private suspend fun loadMovie(connectionId: String, contentId: String, profileId: String?) {
        if (contentId.isBlank()) error("ID do filme inválido.")
        val movie = contentRepository.getMovieDetail(connectionId, contentId)
            ?: error("Não foi possível encontrar este filme.")
        // Resume exactly where this profile left off (Netflix-style "continuar assistindo").
        val saved = profileId?.let { watchProgressRepository.getProgress(it, movie.id) }
        val isFav = profileId?.let { favoriteRepository.isFavorite(it, movie.id).first() } ?: false
        _loadState.value = _loadState.value.copy(
            title = movie.name,
            // No subtitle for movies: categoryName is the provider's raw
            // catalog bucket (e.g. "# Legendados [2]"), not something a
            // person watching the movie needs to see under its title.
            subtitle = null,
            streamUrl = movie.streamUrl,
            isLive = false,
            contentType = ContentType.MOVIE,
            contentId = movie.id,
            nextEpisode = null,
            resumePositionMillis = saved?.positionMillis ?: 0L,
            isFavorite = isFav,
            audioVariants = emptyList(),
        )

        // Best-effort: offer the dubbed/subtitled twin as an in-player audio
        // choice. A failure here must never break playback.
        runCatching { contentRepository.getMovieAudioVariants(connectionId, movie.id) }
            .getOrNull()
            ?.takeIf { it.size >= 2 }
            ?.let { variants ->
                if (_loadState.value.contentId == movie.id) {
                    _loadState.value = _loadState.value.copy(audioVariants = variants)
                }
            }
    }

    private suspend fun loadSeries(connectionId: String, contentId: String, profileId: String?) {
        // contentId format: "<seriesId>:<episodeId>"
        val seriesId = contentId.substringBefore(":").trim()
        val episodeId = contentId.substringAfter(":", "").ifBlank { null }
        if (seriesId.isBlank()) error("ID da série inválido.")
        val series = contentRepository.getSeriesDetail(connectionId, seriesId)
            ?: error("Não foi possível encontrar esta série.")
        val allEpisodes = series.seasons.flatMap { it.episodes }
        if (allEpisodes.isEmpty()) error("Esta série não possui episódios disponíveis.")

        // With no explicit episode, resume the last one this profile watched.
        val episode = when {
            episodeId != null -> allEpisodes.find { it.id == episodeId }
            else -> profileId?.let { pid ->
                allEpisodes.firstNotNullOfOrNull { ep ->
                    watchProgressRepository.getProgress(pid, "$seriesId:${ep.id}")?.let { ep to it }
                }?.first
            }
        } ?: allEpisodes.first()

        val progressKey = "$seriesId:${episode.id}"
        val saved = profileId?.let { watchProgressRepository.getProgress(it, progressKey) }
        val next = allEpisodes.getOrNull(allEpisodes.indexOf(episode) + 1)
        val isFav = profileId?.let { favoriteRepository.isFavorite(it, seriesId).first() } ?: false

        _loadState.value = _loadState.value.copy(
            title = series.name,
            subtitle = "T${episode.seasonNumber} E${episode.episodeNumber} • ${episode.title}",
            streamUrl = episode.streamUrl,
            isLive = false,
            contentType = ContentType.SERIES,
            contentId = progressKey,
            nextEpisode = next,
            resumePositionMillis = saved?.positionMillis ?: 0L,
            isFavorite = isFav,
            audioVariants = emptyList(),
        )
    }

    fun switchChannel(channel: Channel) {
        _loadState.value = _loadState.value.copy(
            title = channel.name,
            subtitle = channel.categoryName,
            streamUrl = channel.streamUrl,
            contentId = channel.id,
            currentProgramLabel = null,
            programProgress = null,
        )
        viewModelScope.launch {
            val id = _loadState.value.contentId
            // Reuses whatever connection loaded this session — the channel
            // list itself came from one connection, so EPG must come from
            // the same one rather than re-resolving the default.
            val conn = connectionRepository.getDefaultConnection() ?: return@launch
            val (current, _) = runCatching { contentRepository.getShortEpg(conn.id, id) }.getOrDefault(null to null)
            // Guards against a slow EPG response landing after the person
            // has already switched channels again.
            if (_loadState.value.contentId == id) {
                _loadState.value = _loadState.value.copy(
                    currentProgramLabel = current?.title,
                    programProgress = current?.progressFraction(),
                )
            }
        }
    }

    fun nextChannel() = stepChannel(1)
    fun previousChannel() = stepChannel(-1)

    private fun stepChannel(delta: Int) {
        val state = _loadState.value
        if (!state.isLive || state.liveChannels.isEmpty()) return
        val index = state.liveChannels.indexOfFirst { it.id == state.contentId }
        if (index < 0) return
        val nextIndex = (index + delta).mod(state.liveChannels.size)
        switchChannel(state.liveChannels[nextIndex])
    }

    fun playNextEpisode() {
        val next = _loadState.value.nextEpisode ?: return
        val seriesId = _loadState.value.contentId.substringBefore(":")
        persistProgressNow()
        load(ContentType.SERIES, "$seriesId:${next.id}")
    }

    fun toggleFavoriteChannel() {
        val profileId = activeProfileId ?: return
        val state = _loadState.value
        val targetId = if (state.contentType == ContentType.SERIES) state.contentId.substringBefore(":") else state.contentId
        viewModelScope.launch {
            toggleFavoriteUseCase(profileId, targetId, state.contentType)
            _loadState.value = _loadState.value.copy(isFavorite = !state.isFavorite)
        }
    }

    fun cycleResizeMode() {
        val current = _loadState.value.resizeMode
        val next = resizeModes[(resizeModes.indexOf(current) + 1).mod(resizeModes.size)]
        _loadState.value = _loadState.value.copy(resizeMode = next)
    }

    /** Sets the resize mode directly — used by the pinch-to-zoom gesture,
     * which maps to a specific fit/fill rather than cycling blindly. */
    fun setResizeMode(mode: Int) {
        _loadState.value = _loadState.value.copy(resizeMode = mode)
    }

    /** Adjusts window brightness via the activity's own attributes (doesn't touch system settings). */
    fun adjustBrightness(activity: Activity?, delta: Float): Float {
        val window = activity?.window ?: return currentBrightness
        currentBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
        window.attributes = window.attributes.apply { screenBrightness = currentBrightness }
        return currentBrightness
    }

    fun enterPictureInPicture(activity: Activity?) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        runCatching { activity.enterPictureInPictureMode(params) }
    }

    fun retry() {
        val url = _loadState.value.streamUrl ?: return
        playerManager.play(url, playerManager.currentPosition())
    }

    /** Pushes the live position into PlaybackUiState so the seek bar tracks playback. */
    fun refreshPosition() {
        playerManager.syncPosition()
        runCatching { maybeAutoAdvance() }
    }

    /** Wraps PlayerManager's version to also persist the language, so it's
     * remembered as the default for every video after this one — not just
     * applied to the one currently playing. */
    fun selectAudioTrack(option: com.auroraplay.iptv.player.TrackOption) {
        playerManager.selectAudioTrack(option)
        val lang = option.language
        if (lang != null) viewModelScope.launch { settingsRepository.updatePreferredAudioLang(lang) }
    }

    fun selectSubtitleTrack(option: com.auroraplay.iptv.player.TrackOption) {
        playerManager.selectSubtitleTrack(option)
        val lang = option.language
        if (lang != null) viewModelScope.launch { settingsRepository.updatePreferredSubtitleLang(lang) }
    }

    /**
     * Switches to a dubbed/subtitled sibling stream ("DUBLADO" ⇄ "LEGENDADO")
     * without leaving playback. Only the stream URL + resume point change;
     * [PlayerScreenContent] re-prepares the player from there, so playback
     * resumes at the same spot on the other audio.
     */
    fun selectAudioVariant(variant: com.auroraplay.iptv.domain.model.AudioStreamVariant) {
        val state = _loadState.value
        if (variant.streamUrl.isBlank() || variant.streamUrl == state.streamUrl) return
        persistProgressNow()
        val position = playerManager.currentPosition().coerceAtLeast(0L)
        autoAdvancedForUrl = null
        autoNextCancelledForUrl = null
        _autoNextInSeconds.value = null
        _loadState.value = state.copy(
            streamUrl = variant.streamUrl,
            resumePositionMillis = position,
        )
    }

    // NOTE: the "Cinema" ambient glow no longer lives here. It used to spin up
    // a second headless ExoPlayer (via ThumbnailPreviewGenerator) to decode
    // background frames — a MediaCodec instance running alongside the main one,
    // which is exactly what crashed the app on devices with a small decoder
    // pool. The player screen now samples the on-screen video TextureView
    // directly (a cheap GPU read-back, no extra decoder), so there is nothing
    // to drive from the ViewModel.

    /**
     * Requests a scrubbing-preview frame near [positionMillis]. Cancelling
     * any in-flight request before starting a new one means a fast drag
     * only ever updates the thumbnail to the *last* position asked for,
     * instead of frames finishing out of order and flickering backwards.
     */
    fun requestScrubThumbnail(positionMillis: Long) {
        val url = _loadState.value.streamUrl ?: return
        val bucket = positionMillis / 3000L
        if (bucket == lastScrubBucket && _scrubThumbnail.value != null) return
        lastScrubBucket = bucket

        // Do not start a decoder seek for every pixel of a drag. Keep only the
        // latest target and give the finger a tiny settling window; this avoids
        // a queue of expensive ExoPlayer seeks while keeping the preview quick.
        scrubJob?.cancel()
        scrubJob = viewModelScope.launch(Dispatchers.Default) {
            delay(70.milliseconds)
            val frame = runCatching {
                thumbnailPreviewGenerator.frameAt(url, positionMillis)
            }.getOrNull()
            if (frame != null && _loadState.value.streamUrl == url) {
                _scrubThumbnail.value = frame
            }
        }
    }

    fun clearScrubThumbnail() {
        scrubJob?.cancel()
        scrubJob = null
        lastScrubBucket = Long.MIN_VALUE
        _scrubThumbnail.value = null
    }

    private fun startProgressLoop() {
        if (progressLoopRunning) return
        progressLoopRunning = true
        viewModelScope.launch {
            while (true) {
                delay(5.seconds)
                persistProgressNow()
            }
        }
    }

    /** Saves position + episode so "Continuar assistindo" can resume exactly here. */
    fun persistProgressNow() {
        val profileId = activeProfileId ?: return
        val state = _loadState.value
        if (state.isLive || state.contentId.isBlank()) return
        val position = playerManager.currentPosition()
        val duration = playerManager.currentDuration()
        if (duration <= 0 || position <= 0) return

        val episodeParts = state.subtitle?.let { Regex("T(\\d+) E(\\d+)").find(it) }
        viewModelScope.launch {
            saveWatchProgressUseCase(
                WatchProgress(
                    contentId = state.contentId,
                    type = state.contentType,
                    profileId = profileId,
                    positionMillis = position,
                    durationMillis = duration,
                    seasonNumber = episodeParts?.groupValues?.getOrNull(1)?.toIntOrNull(),
                    episodeNumber = episodeParts?.groupValues?.getOrNull(2)?.toIntOrNull(),
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        persistProgressNow()
        playerManager.stop()
        thumbnailPreviewGenerator.release()
    }

    private companion object {
        /** Treated as "the credits" for auto-advance (no chapter data from Xtream). */
        const val CREDITS_WINDOW_MS = 40_000L
    }
}
