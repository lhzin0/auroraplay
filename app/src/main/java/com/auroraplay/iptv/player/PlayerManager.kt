package com.auroraplay.iptv.player

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.auroraplay.iptv.core.util.AppLog
import com.auroraplay.iptv.player.download.PlaybackCacheReadOnly
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A single selectable audio or subtitle track, identified by its position in ExoPlayer's track groups. */
data class TrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean,
    /** ISO language code from the track's own Format, when the source provides one —
     * this, not groupIndex/trackIndex, is what carries over to the *next* video, since
     * group/track indices are meaningless once a different media item is loaded. */
    val language: String? = null,
)

data class PlaybackUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val errorMessage: String? = null,
    val playbackSpeed: Float = 1f,
    val volume: Float = 1f,
    val availableAudioTracks: List<TrackOption> = emptyList(),
    val availableSubtitleTracks: List<TrackOption> = emptyList(),
    val subtitlesEnabled: Boolean = false,
    val isCasting: Boolean = false,
    val castDeviceName: String? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
)

/**
 * Thin, testable wrapper around ExoPlayer (local playback) and, when a Cast
 * session is active, a CastPlayer (remote playback on a Chromecast/Android
 * TV device). Both implement Media3's common `Player` interface, so every
 * transport control (play/pause/seek/volume/tracks) below operates on
 * whichever one is currently active — the UI never needs to know which.
 *
 * A single shared instance is used for both the live-TV mini preview and
 * the full-screen player screen, so switching between them never re-buffers.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Singleton
class PlayerManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:PlaybackCacheReadOnly private val cacheDataSourceFactory: CacheDataSource.Factory,
) {
    /** True while the full-screen player is on screen. The Activity watches this
     * to keep PictureInPictureParams.setAutoEnterEnabled in sync (API 31+) and
     * reads it in onUserLeaveHint() for the manual-entry path (API 26–30). */
    val pipEligible = MutableStateFlow(false)

    /** Set by the Activity when it enters/leaves PiP, so the player UI can
     * strip its chrome down to just the video. */
    val pipActive = MutableStateFlow(false)

    val exoPlayer: ExoPlayer by lazy {
        // Downloaded content is served straight from the shared download
        // cache (instant, works offline); anything not downloaded falls
        // through to the network exactly as before, so live TV / VOD
        // streaming behavior is unchanged.
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(cacheDataSourceFactory))
            // Decoder fallback: if the primary (usually hardware) video
            // decoder fails to initialize or throws mid-stream on an odd IPTV
            // codec/profile, drop to another decoder instead of surfacing a
            // fatal error. This is the difference between a one-frame hiccup
            // and a dead black screen on a misbehaving channel.
            .setRenderersFactory(
                DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            )
            // Buffer policy tuned for streaming over shaky connections rather
            // than ExoPlayer's local-file defaults (50s cap, 5s post-rebuffer):
            //  - bank up to 2 min of VOD when bandwidth allows, so a later dip
            //    is ridden out from buffer instead of stalling;
            //  - after a stall, wait for a 6s cushion before resuming so it
            //    doesn't immediately re-stall into a buffering loop;
            //  - prioritise duration over a byte cap so high-bitrate 1080p/4K
            //    streams still pre-buffer enough seconds.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(30_000, 120_000, 2_500, 6_000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .setTargetBufferBytes(C.LENGTH_UNSET)
                    .build()
            )
            .setHandleAudioBecomingNoisy(true)
            // Audit #23: explicit audio focus. USAGE_MEDIA + CONTENT_TYPE_MOVIE
            // (live TV counts as a "movie" content type for focus purposes) with
            // handleAudioFocus = true makes ExoPlayer itself request focus on
            // play, release it on pause/stop, duck or pause on a transient loss
            // (a call, a notification sound) and resume playback when focus is
            // regained — without any of that being hand-rolled here.
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .build()
    }

    /** Null when Google Play services / Cast SDK isn't available on the device (e.g. some Android TV boxes). */
    private val castContext: CastContext? = runCatching { CastContext.getSharedInstance(context) }.getOrNull()

    private val castPlayer: CastPlayer? = castContext?.let {
        CastPlayer.Builder(context).build()
    }

    private var lastKnownUrl: String? = null
    /** Original URL requested by the screen. This remains stable if a live
     * stream later falls back from .m3u8 to .ts, so promoting its preview to
     * full screen still recognises the stream as the same playback. */
    private var lastRequestedUrl: String? = null
    private var lastKnownPositionMillis: Long = 0L
    // Guards the one-shot ".m3u8 → .ts" retry for live streams (see onPlayerError).
    private var triedLiveTsFallback = false
    /** ±10s or ±5s, per the user's setting. */
    @Volatile
    var seekIncrementMs: Long = 10_000L

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state

    /** The Player instance the UI (PlayerView) should currently attach to. */
    private val _activePlayer = MutableStateFlow<Player>(exoPlayer)
    val activePlayerFlow: StateFlow<Player> = _activePlayer

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.value = _state.value.copy(
                isBuffering = playbackState == Player.STATE_BUFFERING,
                durationMillis = activePlayer().duration.takeIf { it != C.TIME_UNSET } ?: 0L,
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            // Live channels are requested as HLS (…/live/…/<id>.m3u8), but a
            // lot of Xtream servers only package a given channel as raw
            // MPEG-TS (…<id>.ts) — the .m3u8 variant 404s and lands here even
            // though the channel is perfectly playable. Fall back to .ts once
            // before surfacing an error. This is per-channel, not global:
            // channels that do have an HLS playlist keep using it.
            val url = lastKnownUrl
            if (!triedLiveTsFallback && (url != null && (url.contains("/live/") && url.endsWith(".m3u8")))) {
                triedLiveTsFallback = true
                val tsUrl = url.removeSuffix(".m3u8") + ".ts"
                AppLog.w("Player", "playback error on .m3u8 (code=${error.errorCode}); retrying as .ts", error)
                val player = activePlayer()
                player.setMediaItem(MediaItem.fromUri(tsUrl))
                player.prepare()
                player.playWhenReady = true
                lastKnownUrl = tsUrl
                _state.value = _state.value.copy(isBuffering = true, errorMessage = null)
                return
            }
            AppLog.e("Player", "playback error (code=${error.errorCode}) for $url", error)
            _state.value = _state.value.copy(errorMessage = "Não foi possível reproduzir este conteúdo.")
        }

        override fun onTracksChanged(tracks: Tracks) {
            _state.value = _state.value.copy(
                availableAudioTracks = extractTracks(tracks, C.TRACK_TYPE_AUDIO),
                availableSubtitleTracks = extractTracks(tracks, C.TRACK_TYPE_TEXT),
            )
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _state.value = _state.value.copy(
                videoWidth = videoSize.width,
                videoHeight = videoSize.height,
            )
        }

        override fun onVolumeChanged(volume: Float) {
            _state.value = _state.value.copy(volume = volume)
        }
    }

    init {
        exoPlayer.addListener(playerListener)
        castPlayer?.addListener(playerListener)

        castContext?.sessionManager?.addSessionManagerListener(
            object : SessionManagerListener<CastSession> {
                override fun onSessionStarted(session: CastSession, sessionId: String) = switchToCast(session)
                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = switchToCast(session)
                override fun onSessionEnded(session: CastSession, error: Int) = switchToLocal()
                override fun onSessionStarting(session: CastSession) {}
                override fun onSessionStartFailed(session: CastSession, error: Int) {}
                override fun onSessionEnding(session: CastSession) {}
                override fun onSessionResuming(session: CastSession, sessionId: String) {}
                override fun onSessionResumeFailed(session: CastSession, error: Int) {}
                override fun onSessionSuspended(session: CastSession, reason: Int) {}
            },
            CastSession::class.java,
        )
    }

    private fun activePlayer(): Player = _activePlayer.value

    private fun switchToCast(session: CastSession) {
        val castPlayer = this.castPlayer ?: return
        lastKnownPositionMillis = exoPlayer.currentPosition
        exoPlayer.pause()
        lastKnownUrl?.let { url ->
            val mediaItem = MediaItem.fromUri(url)
            castPlayer.setMediaItem(mediaItem, lastKnownPositionMillis)
            castPlayer.prepare()
            castPlayer.playWhenReady = true
        }
        _activePlayer.value = castPlayer
        _state.value = _state.value.copy(isCasting = true, castDeviceName = session.castDevice?.friendlyName)
    }

    private fun switchToLocal() {
        val castPlayer = this.castPlayer
        val resumePosition = castPlayer?.currentPosition?.takeIf { it > 0 } ?: lastKnownPositionMillis
        castPlayer?.stop()
        lastKnownUrl?.let { url ->
            exoPlayer.setMediaItem(MediaItem.fromUri(url), resumePosition)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
        _activePlayer.value = exoPlayer
        _state.value = _state.value.copy(isCasting = false, castDeviceName = null)
    }

    private fun extractTracks(tracks: Tracks, type: Int): List<TrackOption> {
        val options = mutableListOf<TrackOption>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != type) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                val label = format.label ?: format.language?.uppercase() ?: "Faixa ${trackIndex + 1}"
                options += TrackOption(
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    label = label,
                    isSelected = group.isTrackSelected(trackIndex),
                    language = format.language,
                )
            }
        }
        return options
    }

    fun play(streamUrl: String, startPositionMillis: Long = 0L) {
        val url = streamUrl.trim()
        if (url.isBlank()) {
            _state.value = _state.value.copy(
                isBuffering = false,
                isPlaying = false,
                errorMessage = "Endereço de reprodução inválido.",
            )
            return
        }

        runCatching {
            val player = activePlayer()
            // A PlayerView is recreated when the inline preview is promoted
            // to the full-screen route. The ExoPlayer itself is shared, so do
            // not replace its media item or prepare it again for the exact
            // same request: doing so discarded the already buffered video and
            // made full screen visibly reload.
            if (
                lastRequestedUrl == url &&
                player.currentMediaItem != null &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED
            ) {
                syncPosition()
                return
            }

            lastRequestedUrl = url
            lastKnownUrl = url
            triedLiveTsFallback = false
            _state.value = PlaybackUiState(
                isBuffering = true,
                isCasting = _state.value.isCasting,
                castDeviceName = _state.value.castDeviceName,
            )
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem, startPositionMillis.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = true
        }.onFailure { error ->
            _state.value = _state.value.copy(
                isBuffering = false,
                isPlaying = false,
                errorMessage = error.message?.takeIf { it.isNotBlank() }
                    ?: "Não foi possível iniciar a reprodução.",
            )
        }
    }

    fun togglePlayPause() {
        val player = activePlayer()
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMillis: Long) = activePlayer().seekTo(positionMillis)

    fun seekForward(millis: Long = seekIncrementMs) {
        val player = activePlayer()
        player.seekTo((player.currentPosition + millis).coerceAtMost(player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
    }

    fun seekBackward(millis: Long = seekIncrementMs) {
        val player = activePlayer()
        player.seekTo((player.currentPosition - millis).coerceAtLeast(0))
    }

    /** Best-effort "pular introdução": Xtream provides no chapter/marker metadata,
     * so this jumps forward a fixed amount rather than detecting the intro precisely. */
    fun skipIntro(millis: Long = 85_000) = seekForward(millis)

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed) // Cast receivers rarely support variable speed; local-only.
        _state.value = _state.value.copy(playbackSpeed = speed)
    }

    /** Routed to whichever player is effectively active (audit #23): during a
     * Cast session this reaches the CastPlayer — and therefore the receiver —
     * instead of silently changing a local player nobody is listening to. */
    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        activePlayer().volume = clamped
        _state.value = _state.value.copy(volume = clamped)
    }

    fun selectAudioTrack(option: TrackOption) = selectTrack(option, C.TRACK_TYPE_AUDIO)
    fun selectSubtitleTrack(option: TrackOption) {
        selectTrack(option, C.TRACK_TYPE_TEXT)
        _state.value = _state.value.copy(subtitlesEnabled = true)
    }

    /**
     * Sets the *general* language preference (not tied to any one video's
     * track groups, unlike selectAudioTrack/selectSubtitleTrack's explicit
     * overrides) so whichever language someone picked once keeps being
     * picked automatically on every video after — including a brand new
     * ExoPlayer instance in a future app session, since this is called
     * from persisted settings before the first play().
     */
    fun setPreferredLanguages(audioLanguage: String?, subtitleLanguage: String?) {
        val player = activePlayer()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .apply {
                if (audioLanguage != null) {
                    setPreferredAudioLanguage(audioLanguage)
                }
                if (subtitleLanguage != null) {
                    setPreferredTextLanguage(subtitleLanguage)
                    setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                }
            }
            .build()
    }

    fun disableSubtitles() {
        val player = activePlayer()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        _state.value = _state.value.copy(subtitlesEnabled = false)
    }

    /**
     * Applies the "Qualidade" preference (audit #13). It caps the video track
     * ExoPlayer's adaptive selector may choose from an HLS/DASH stream; on a
     * progressive stream with a single video track it simply has nothing to
     * constrain, so playback is unaffected. `exceedVideoConstraintsIfNecessary`
     * stays true, so even if every rendition is above the cap the lowest is
     * still played — the picture is never dropped. Reapplied live whenever the
     * setting changes, and persists across media items on the shared player.
     */
    fun setMaxVideoQuality(quality: String) {
        val maxHeight = maxHeightForQuality(quality)
        val player = activePlayer()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .apply {
                if (maxHeight == null) clearVideoSizeConstraints()
                else setMaxVideoSize(Int.MAX_VALUE, maxHeight)
            }
            .build()
    }

    private fun selectTrack(option: TrackOption, type: Int) {
        val player = activePlayer()
        val tracks = player.currentTracks
        val group = tracks.groups.getOrNull(option.groupIndex) ?: return
        val override = TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .clearOverridesOfType(type)
            .addOverride(override)
            .build()
    }

    /** Pushes live position/duration into the UI state; called on a ticker by the player screen
     * because ExoPlayer has no position-changed callback. */
    fun syncPosition() {
        val player = activePlayer()
        _state.value = _state.value.copy(
            positionMillis = player.currentPosition.coerceAtLeast(0),
            durationMillis = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L,
        )
    }

    fun currentPosition(): Long = activePlayer().currentPosition
    fun currentDuration(): Long = activePlayer().duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L

    /** True once the current item has played to the end (ExoPlayer STATE_ENDED).
     * Used to fire the next-episode auto-advance even if the position poll never
     * lands on the final second. */
    fun hasPlaybackEnded(): Boolean =
        runCatching { activePlayer().playbackState == Player.STATE_ENDED }.getOrDefault(false)

    fun isCastAvailable(): Boolean = castContext != null

    fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        castPlayer?.stop()
        lastKnownUrl = null
        lastRequestedUrl = null
    }

    fun release() {
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
        castPlayer?.removeListener(playerListener)
        castPlayer?.release()
    }
}

/**
 * Maps a "Qualidade" setting value to a maximum video height, or null for
 * "no cap" (adaptive/auto). Unknown values fall back to no cap so a future
 * option can't accidentally throttle playback (audit #13).
 */
internal fun maxHeightForQuality(quality: String): Int? = when (quality) {
    "low" -> 480
    "medium" -> 720
    "high" -> 1080
    else -> null // "auto" and anything unrecognised
}
