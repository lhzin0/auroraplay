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
import com.auroraplay.iptv.domain.policy.ContentPolicy
import com.auroraplay.iptv.domain.usecase.SaveWatchProgressUseCase
import com.auroraplay.iptv.domain.usecase.ToggleFavoriteUseCase
import com.auroraplay.iptv.player.PlayerManager
import com.auroraplay.iptv.player.ScrubPreviewEngine
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

data class PlayerLoadState(
    val title: String = "",
    val subtitle: String? = null,
    val streamUrl: String? = null,
    val isLive: Boolean = false,
    val contentType: ContentType = ContentType.MOVIE,
    val contentId: String = "",
    val nextEpisode: Episode? = null,
    /** Every episode of the current series (all seasons), for the in-player
     * quick episode switcher. Empty for movies/live. */
    val episodes: List<Episode> = emptyList(),
    val currentEpisodeId: String? = null,
    /** Poster of the current movie / series — snapshotted into the Histórico. */
    val posterUrl: String? = null,
    /** Connection the current content belongs to (part of the progress identity). */
    val connectionId: String = "",
    val liveChannels: List<Channel> = emptyList(),
    val resumePositionMillis: Long = 0L,
    val isFavorite: Boolean = false,
    val resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    val currentProgramLabel: String? = null,
    val programProgress: Float? = null,
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
    private val scrubPreview: ScrubPreviewEngine,
    private val settingsRepository: com.auroraplay.iptv.domain.repository.SettingsRepository,
    private val contentPolicy: ContentPolicy,
    private val downloadTracker: com.auroraplay.iptv.player.download.DownloadTracker,
) : ViewModel() {

    /** Active profile's kids flag — every content-load and channel-switch path
     * is gated by ContentPolicy so a kids profile can't reach a blocked title
     * through the player, a deep link, or the quick channel switcher. */
    private var activeProfileIsKids = false

    /** Active connection — part of the favourites / watch-progress identity
     * (audit #3), used by the channel-switch and favourite-toggle paths that
     * run after the initial load. */
    private var activeConnectionId: String? = null

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
                _cinemaMode.value = settings.cinemaMode
            }
        }
    }

    /** Player "Cinema" ambient glow — a sticky, persisted toggle. The in-player
     * button is the only thing that flips it, and it holds across episode
     * changes / re-opening the player / app restarts. */
    private val _cinemaMode = MutableStateFlow(false)
    val cinemaMode: StateFlow<Boolean> = _cinemaMode.asStateFlow()

    fun setCinemaMode(enabled: Boolean) {
        _cinemaMode.value = enabled
        viewModelScope.launch { runCatching { settingsRepository.updateCinemaMode(enabled) } }
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
        if (duration <= 0L && !playerManager.hasPlaybackEnded()) {
            if (_autoNextInSeconds.value != null) _autoNextInSeconds.value = null
            return
        }
        val remainingMs = (duration - position).coerceAtLeast(0L)
        when {
            // The episode is over (STATE_ENDED) or within its final moment —
            // advance now. This is the branch the old code never reached:
            // `remainingMs in 1..WINDOW` can't hold once playback ends
            // (remaining is 0), so the jump never fired.
            playerManager.hasPlaybackEnded() || remainingMs <= 1_200L -> {
                autoAdvancedForUrl = url
                _autoNextInSeconds.value = null
                playNextEpisode()
            }
            // Inside the "credits" window — show the countdown. Its non-null
            // value also tightens the position poll to 1s (see PlayerScreen).
            remainingMs <= CREDITS_WINDOW_MS -> {
                _autoNextInSeconds.value = ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(1)
            }
            else -> if (_autoNextInSeconds.value != null) _autoNextInSeconds.value = null
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
    // The position the finger is at right now. The preview is always the frame
    // nearest to THIS — so a decode that lands late can never show an old spot,
    // it just fills in around wherever the finger currently is.
    @Volatile private var scrubTargetMillis: Long? = null
    private var scrubCollector: Job? = null

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

    /**
     * Offline playback of a downloaded item (audit #8). Resolves everything
     * from the Media3 download index — no active connection, no catalog, no
     * network — so it works on a cold start and even after the connection the
     * item came from was deleted. A download that isn't finished is reported,
     * not played as a broken stream.
     */
    fun loadOfflineDownload(downloadKey: String) {
        if (downloadKey.isBlank()) {
            _loadState.value = _loadState.value.copy(loadError = "Download inválido.")
            return
        }
        _autoNextInSeconds.value = null

        viewModelScope.launch {
            _loadState.value = _loadState.value.copy(loadError = null)
            runCatching {
                val profile = profileRepository.observeActiveProfile().first()
                activeProfileId = profile?.id
                activeProfileIsKids = profile?.isKids == true

                val dl = downloadTracker.offlineDownload(downloadKey)
                // Title-only kids check (the download index carries no genre),
                // mirroring the Downloads list filter — defence in depth.
                val allowed = dl != null && contentPolicy.visibleLoose(activeProfileIsKids, dl.title)
                com.auroraplay.iptv.player.download.offlineLoadFailure(
                    exists = dl != null,
                    allowedForProfile = allowed,
                    isComplete = dl?.isComplete == true,
                )?.let { error(it) }
                checkNotNull(dl)

                val isSeries = dl.playbackContentType.equals("SERIES", ignoreCase = true)
                val contentType = if (isSeries) ContentType.SERIES else ContentType.MOVIE
                activeConnectionId = dl.connectionId.ifBlank { null }

                // Resume from the same watch_progress row the online path uses:
                // its identity is (connectionId, playbackId, type, profile) and
                // the download carried the connectionId it was queued under.
                val saved = profile?.id?.let {
                    runCatching {
                        watchProgressRepository.getProgress(dl.connectionId, it, dl.playbackId, contentType)
                    }.getOrNull()
                }

                // season/episode for the Histórico snapshot — recovered from the
                // sortKey the download stored (season * 1000 + episode).
                val subtitle = com.auroraplay.iptv.player.download.seasonEpisodeFromSortKey(dl.sortKey)
                    ?.takeIf { isSeries }
                    ?.let { (s, e) -> "T$s E$e" }

                _loadState.value = PlayerLoadState(
                    title = dl.title,
                    subtitle = subtitle,
                    streamUrl = dl.uri,
                    isLive = false,
                    contentType = contentType,
                    contentId = dl.playbackId,
                    connectionId = dl.connectionId,
                    posterUrl = dl.posterUrl,
                    // The in-player episode switcher / auto-next need the full
                    // catalog episode list, which isn't available offline — the
                    // user picks the next episode from the Downloads screen.
                    episodes = emptyList(),
                    nextEpisode = null,
                    resumePositionMillis = saved?.positionMillis ?: 0L,
                    isFavorite = false,
                )

                runCatching { scrubPreview.open(dl.uri) }
                startProgressLoop()
            }.onFailure { error ->
                _loadState.value = _loadState.value.copy(
                    loadError = error.message?.takeIf { it.isNotBlank() }
                        ?: "Não foi possível abrir este download.",
                )
            }
        }
    }

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
                activeProfileIsKids = profile?.isKids == true
                activeConnectionId = connection.id

                when (contentType) {
                    ContentType.LIVE -> loadLive(connection.id, contentId, profile?.id, activeProfileIsKids)
                    ContentType.MOVIE -> loadMovie(connection.id, contentId, profile?.id, activeProfileIsKids)
                    ContentType.SERIES -> loadSeries(connection.id, contentId, profile?.id, activeProfileIsKids)
                }

                val url = _loadState.value.streamUrl?.trim()
                if (url.isNullOrBlank()) {
                    error("O conteúdo não possui um endereço de reprodução válido.")
                }

                // Prepare the scrub-preview decoder once for this VOD/episode.
                // Never lets a preview failure disturb playback.
                if (!_loadState.value.isLive) {
                    runCatching { scrubPreview.open(url) }
                } else {
                    runCatching { scrubPreview.close() }
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

    private suspend fun loadLive(connectionId: String, contentId: String, profileId: String?, isKids: Boolean) {
        val channels = contentRepository.observeChannels(connectionId).first()
        val channel = channels.find { it.id == contentId } ?: channels.firstOrNull() ?: return
        if (!contentPolicy.allows(isKids, channel)) error("Este conteúdo não está disponível neste perfil.")
        val isFav = profileId?.let { favoriteRepository.isFavorite(connectionId, it, channel.id, ContentType.LIVE).first() } ?: false
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
            connectionId = connectionId,
            liveChannels = contentPolicy.channels(isKids, channels),
            resumePositionMillis = 0L,
            isFavorite = isFav,
            currentProgramLabel = current?.title,
            programProgress = current?.progressFraction(),
        )
        recordChannelHistory(channel.id)
    }

    /** "Canais recentes" on Home — fire-and-forget, never blocks playback. */
    private fun recordChannelHistory(channelId: String) {
        val profileId = activeProfileId ?: return
        val connectionId = activeConnectionId ?: return
        viewModelScope.launch {
            runCatching { watchProgressRepository.recordChannelWatch(connectionId, profileId, channelId) }
        }
    }

    private suspend fun loadMovie(connectionId: String, contentId: String, profileId: String?, isKids: Boolean) {
        if (contentId.isBlank()) error("ID do filme inválido.")
        val movie = contentRepository.getMovieDetail(connectionId, contentId)
            ?: error("Não foi possível encontrar este filme.")
        if (!contentPolicy.allows(isKids, movie)) error("Este conteúdo não está disponível neste perfil.")
        // Resume exactly where this profile left off (Netflix-style "continuar assistindo").
        val saved = profileId?.let { watchProgressRepository.getProgress(connectionId, it, movie.id, ContentType.MOVIE) }
        val isFav = profileId?.let { favoriteRepository.isFavorite(connectionId, it, movie.id, ContentType.MOVIE).first() } ?: false
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
            connectionId = connectionId,
            posterUrl = movie.posterUrl,
            nextEpisode = null,
            resumePositionMillis = saved?.positionMillis ?: 0L,
            isFavorite = isFav,
        )
    }

    private suspend fun loadSeries(connectionId: String, contentId: String, profileId: String?, isKids: Boolean) {
        // contentId format: "<seriesId>:<episodeId>"
        val seriesId = contentId.substringBefore(":").trim()
        val episodeId = contentId.substringAfter(":", "").ifBlank { null }
        if (seriesId.isBlank()) error("ID da série inválido.")
        // allowStaleRefresh = false: never make starting playback wait on a
        // get_series_info round-trip (audit #7 / #18). A still-empty episode
        // list still fetches once.
        val series = contentRepository.getSeriesDetail(connectionId, seriesId, allowStaleRefresh = false)
            ?: error("Não foi possível encontrar esta série.")
        if (!contentPolicy.allows(isKids, series)) error("Este conteúdo não está disponível neste perfil.")
        val allEpisodes = series.seasons.flatMap { it.episodes }
        if (allEpisodes.isEmpty()) error("Esta série não possui episódios disponíveis.")

        // With no explicit episode, resume the last one this profile watched.
        val episode = when {
            episodeId != null -> allEpisodes.find { it.id == episodeId }
            else -> profileId?.let { pid ->
                allEpisodes.firstNotNullOfOrNull { ep ->
                    watchProgressRepository.getProgress(connectionId, pid, "$seriesId:${ep.id}", ContentType.SERIES)?.let { ep to it }
                }?.first
            }
        } ?: allEpisodes.first()

        val progressKey = "$seriesId:${episode.id}"
        val saved = profileId?.let { watchProgressRepository.getProgress(connectionId, it, progressKey, ContentType.SERIES) }
        val next = allEpisodes.getOrNull(allEpisodes.indexOf(episode) + 1)
        val isFav = profileId?.let { favoriteRepository.isFavorite(connectionId, it, seriesId, ContentType.SERIES).first() } ?: false

        // Drop the episode title from the subtitle when it just repeats the
        // series name (a very common provider habit: the "title" of every
        // episode is the show's own name).
        val epLabel = episode.title
            .takeIf { it.isNotBlank() && !it.equals(series.name, ignoreCase = true) && !it.startsWith("Episódio", true) }
        _loadState.value = _loadState.value.copy(
            title = series.name,
            subtitle = listOfNotNull(
                "T${episode.seasonNumber} E${episode.episodeNumber}",
                epLabel,
            ).joinToString(" • "),
            streamUrl = episode.streamUrl,
            isLive = false,
            contentType = ContentType.SERIES,
            contentId = progressKey,
            connectionId = connectionId,
            posterUrl = series.posterUrl,
            nextEpisode = next,
            episodes = allEpisodes,
            currentEpisodeId = episode.id,
            resumePositionMillis = saved?.positionMillis ?: 0L,
            isFavorite = isFav,
        )
    }

    /** Jump straight to another episode of the current series, keeping the
     * player open (persists progress on the way out, like the next-episode
     * auto-advance). */
    fun switchToEpisode(episodeId: String) {
        val state = _loadState.value
        if (state.contentType != ContentType.SERIES || episodeId == state.currentEpisodeId) return
        val seriesId = state.contentId.substringBefore(":")
        persistProgressNow()
        load(ContentType.SERIES, "$seriesId:$episodeId")
    }

    fun switchChannel(channel: Channel) {
        // Belt-and-braces: liveChannels is already filtered, but never let a
        // blocked channel through the quick switcher for a kids profile.
        if (!contentPolicy.allows(activeProfileIsKids, channel)) return
        _loadState.value = _loadState.value.copy(
            title = channel.name,
            subtitle = channel.categoryName,
            streamUrl = channel.streamUrl,
            contentId = channel.id,
            currentProgramLabel = null,
            programProgress = null,
        )
        recordChannelHistory(channel.id)
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
        val connectionId = state.connectionId.ifBlank { activeConnectionId } ?: return
        viewModelScope.launch {
            toggleFavoriteUseCase(connectionId, profileId, targetId, state.contentType)
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

    // NOTE: the in-player "Dublado ⇄ Legendado" stream switch was removed —
    // provider metadata was too inconsistent to reliably pair the two copies
    // of a title on-device. The catalog now simply keeps the dubbed copy and
    // drops the subtitled twin (movies and series), so there is nothing to
    // switch between at playback time.

    // NOTE: the "Cinema" ambient glow no longer lives here. It used to spin up
    // a second headless ExoPlayer to decode background frames — a MediaCodec
    // instance running alongside the main one, which is exactly what crashed
    // the app on devices with a small decoder pool. The player screen now
    // samples the on-screen video TextureView directly (a cheap GPU
    // read-back, no extra decoder), so there is nothing
    // to drive from the ViewModel.

    /**
     * Called continuously while dragging the timeline. Cheap: it just records
     * where the finger is and shows the nearest already-decoded frame right
     * away. [ScrubPreviewEngine] decodes around that position asynchronously
     * and never blocks this call or the main player.
     */
    fun requestScrubThumbnail(positionMillis: Long) {
        if (_loadState.value.isLive || !scrubPreview.available) return
        scrubTargetMillis = positionMillis
        scrubPreview.requestAt(positionMillis)
        _scrubThumbnail.value = scrubPreview.nearest(positionMillis) ?: _scrubThumbnail.value

        if (scrubCollector?.isActive != true) {
            scrubCollector = viewModelScope.launch(Dispatchers.Default) {
                scrubPreview.cacheVersion.collect {
                    val target = scrubTargetMillis ?: return@collect
                    scrubPreview.nearest(target)?.let { _scrubThumbnail.value = it }
                }
            }
        }
    }

    fun clearScrubThumbnail() {
        scrubTargetMillis = null
        scrubCollector?.cancel()
        scrubCollector = null
        scrubPreview.setIdle()
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
                    connectionId = state.connectionId.ifBlank { activeConnectionId ?: "" },
                    contentId = state.contentId,
                    type = state.contentType,
                    profileId = profileId,
                    positionMillis = position,
                    durationMillis = duration,
                    seasonNumber = episodeParts?.groupValues?.getOrNull(1)?.toIntOrNull(),
                    episodeNumber = episodeParts?.groupValues?.getOrNull(2)?.toIntOrNull(),
                    // Snapshot for the Histórico (survives the title leaving the
                    // catalog). For a series this is the show name.
                    title = state.title.ifBlank { null },
                    posterUrl = state.posterUrl,
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        persistProgressNow()
        playerManager.stop()
        scrubCollector?.cancel()
        scrubPreview.release()
    }

    private companion object {
        /** Treated as "the credits" for auto-advance (no chapter data from Xtream). */
        const val CREDITS_WINDOW_MS = 40_000L
    }
}
