package com.auroraplay.iptv.presentation.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.theme.frostSurface
import com.auroraplay.iptv.core.util.toTimeLabel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.auroraplay.iptv.domain.model.ContentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

/**
 * Full-screen player that always opens in landscape while hiding the system
 * bars (immersive). Exiting through
 * the player back action returns to the exact page that launched fullscreen.
 * The video fills the display instead of sitting letterboxed in a portrait window.
 *
 * Gestures follow the conventions people already expect from streaming apps:
 * double-tap left/right to seek, pinch to switch between fit and fill, single
 * tap to toggle controls. Brightness and volume are dedicated vertical
 * sliders inside the controls overlay (left/right edges) instead of an
 * invisible full-screen drag zone — a raw drag-anywhere gesture was too easy
 * to trigger by accident while just trying to tap through the video, and
 * gave no visible target to aim for.
 */

/** Transient state for the double-tap seek glyph shown while the controls are
 * hidden: an id (so each fresh double-tap re-triggers the animation) plus the
 * direction it should spin. Non-null only between a hidden double-tap and the
 * end of that one animation. */
private data class HiddenSeekRipple(val id: Int, val forward: Boolean)

@Composable
fun PlayerScreen(
    contentType: ContentType? = null,
    contentId: String? = null,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    /** When set, playback is resolved entirely from the offline download index
     * (audit #8) and [contentType]/[contentId] are ignored. */
    offlineDownloadKey: String? = null,
) {
    LaunchedEffect(contentType, contentId, offlineDownloadKey) {
        when {
            offlineDownloadKey != null -> viewModel.loadOfflineDownload(offlineDownloadKey)
            contentType != null && contentId != null -> viewModel.load(contentType, contentId)
        }
    }

    val context = LocalContext.current
    val activity = context as? Activity

    // Playback is always landscape, independent of the device rotation lock.
    // The previous orientation and immersive mode are restored on exit.
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            viewModel.persistProgressNow()
            viewModel.closeScrubPreview()
        }
    }

    // Tell the Activity that Picture-in-Picture is available while this screen
    // is up (it reads this in onUserLeaveHint).
    DisposableEffect(Unit) {
        viewModel.playerManager.pipEligible.value = true
        onDispose { viewModel.playerManager.pipEligible.value = false }
    }
    val pipActive by viewModel.playerManager.pipActive.collectAsState()

    val loadState by viewModel.loadState.collectAsState()
    val playbackState by viewModel.playerManager.state.collectAsState()
    val seekSeconds by viewModel.seekSeconds.collectAsState()
    val scrubThumbnail by viewModel.scrubThumbnail.collectAsState()
    val autoNextInSeconds by viewModel.autoNextInSeconds.collectAsState()
    // Sticky, persisted (see PlayerViewModel.cinemaMode): stays on until the
    // user taps the button again — survives auto-advance to the next episode,
    // leaving/re-opening the player, and app restarts.
    val cinematicModeEnabled by viewModel.cinemaMode.collectAsState()
    // The on-screen video surface (a TextureView — see player_surface.xml) and
    // the latest ambient-glow sample taken from it. Kept here, not in the
    // ViewModel: capturing an already-rendered frame is a local, decoder-free
    // GPU read-back, and the previous ViewModel path (a 2nd ExoPlayer) is what
    // crashed the app.
    var videoTextureView by remember { mutableStateOf<TextureView?>(null) }
    // Cinema mode: a small copy of the current frame, drawn heavily blurred and
    // stretched into the letterbox bars (YouTube "ambient" look). Two layers —
    // the previous frame stays fully opaque underneath while the new one fades
    // in ON TOP — so there's never an opacity dip (that dip is what read as
    // flicker with a plain Crossfade).
    var cinemaPrev by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var cinemaCur by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val cinemaFade = remember { Animatable(1f) }
    // Backdrop the ⋮ panel blurs (FrostGlass): the video surface is the source.
    val playerHaze = remember { HazeState() }

    var controlsVisible by remember { mutableStateOf(value = true) }
    var isLocked by remember { mutableStateOf(false) }
    var showChannelList by remember { mutableStateOf(false) }
    var showChannelEpg by remember { mutableStateOf(false) }
    var showEpisodeList by remember { mutableStateOf(false) }
    // One combined Áudio + Legendas sheet (the subtitle one used to be
    // unreachable). Also carries the Dublado/Legendado stream switch.
    var showAudioSubsSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    // Three-dots panel with the secondary settings (speed, aspect).
    var showSettingsSheet by remember { mutableStateOf(false) }
    // Centre toast is pinch-to-resize feedback ONLY now — the ±10s text was
    // replaced by the icon ripple below.
    var toastLabel by remember { mutableStateOf<String?>(null) }
    // Double-tap seek feedback: a side + a bump counter that re-triggers the
    // short icon animation each tap.
    var seekBump by remember { mutableIntStateOf(0) }
    var seekForward by remember { mutableStateOf(true) }
    // One-shot glyph shown ONLY for a genuine double-tap-seek made while the
    // controls are hidden. It clears itself the instant its animation ends and
    // again whenever the controls come back, so it can never re-appear merely
    // because the user later single-taps the video to hide the controls again.
    // (The old `id > 0` guard stayed true forever after the first use, so the
    // glyph flashed on every subsequent control-hide with no seek behind it.)
    var hiddenSeekRipple by remember { mutableStateOf<HiddenSeekRipple?>(null) }
    val controlsVisibleState by rememberUpdatedState(controlsVisible)
    // Pauses the controls auto-hide while the timeline is being dragged.
    var isScrubbing by remember { mutableStateOf(false) }
    // Brightness has no natural StateFlow source (it's a raw window attribute,
    // not something the player itself tracks), so the slider's fill is kept
    // here and updated from adjustBrightness()'s return value. Volume already
    // lives in playbackState and is used directly.
    var brightnessValue by remember { mutableFloatStateOf(0.5f) }
    // Hoisted here (not inside the overlay) so it survives the overlay being
    // removed from composition while controls fade out and back in — a
    // person's choice of "-remaining" vs "elapsed" shouldn't reset itself
    // just because they tapped the screen to hide the controls.
    var showRemainingTime by remember { mutableStateOf(true) }

    // Live position ticker: ExoPlayer doesn't push position updates, so poll.
    // While the controls (seek bar / time label) are on screen it needs to be
    // smooth — 500ms. With them hidden nothing shows the position, so it drops
    // to 2s: enough to keep the value from going stale for ambient mode and to
    // resume instantly when the controls reappear, without forcing the whole
    // player tree to re-evaluate twice a second through the stretch the
    // decoder wants the CPU to itself.
    LaunchedEffect(loadState.streamUrl, controlsVisible, isScrubbing, autoNextInSeconds != null) {
        val intervalMs = when {
            autoNextInSeconds != null -> 1000L      // keep the countdown ticking smoothly
            controlsVisible || isScrubbing -> 500L
            else -> 2000L
        }
        while (true) {
            viewModel.refreshPosition()
            delay(intervalMs)
        }
    }

    // In PiP the window is tiny — strip everything down to just the video.
    LaunchedEffect(pipActive) { if (pipActive) { controlsVisible = false } }

    LaunchedEffect(controlsVisible, playbackState.isPlaying, isScrubbing) {
        if (controlsVisible && playbackState.isPlaying && !isScrubbing) {
            delay(4.seconds)
            controlsVisible = false
        }
    }

    LaunchedEffect(toastLabel) {
        if (toastLabel != null) { delay(700.milliseconds); toastLabel = null }
    }

    // The hidden-controls seek glyph is strictly transient: drop it the moment
    // the controls are shown so it can't linger into the next hide.
    LaunchedEffect(controlsVisible) { if (controlsVisible) hiddenSeekRipple = null }

    // Cinema mode. Sample the on-screen video TextureView itself — frames that
    // are already decoded and composited — instead of running a second decoder.
    // A tiny `getBitmap` is a cheap read-back; drawn upscaled + blurred it
    // reads as a soft continuation of the scene. Each new sample crossfades
    // over the settled one (2s, linear) so scene cuts melt.
    //
    // Keyed on isPlaying too: while the video is PAUSED the picture isn't
    // changing, so re-running the fade every cycle just made the bars pulse
    // ("piscando"). Paused -> take one steady sample and stop.
    LaunchedEffect(cinematicModeEnabled, videoTextureView, playbackState.isPlaying) {
        if (!cinematicModeEnabled) { cinemaPrev = null; cinemaCur = null; return@LaunchedEffect }
        val tv = videoTextureView ?: return@LaunchedEffect
        fun grab() = runCatching {
            if (tv.isAvailable && tv.width > 0 && tv.height > 0) tv.getBitmap(96, 54) else null
        }.getOrNull()

        // Fresh steady frame on entry (covers the play<->pause transition).
        grab()?.let { shot ->
            cinemaPrev = shot
            cinemaCur = shot
            cinemaFade.snapTo(1f)
        }
        if (!playbackState.isPlaying) return@LaunchedEffect  // hold it, no re-fading

        while (true) {
            delay(900.milliseconds)
            val shot = grab() ?: continue
            cinemaPrev = cinemaCur ?: shot   // settled layer stays fully opaque underneath
            cinemaCur = shot
            cinemaFade.snapTo(0f)
            cinemaFade.animateTo(1f, tween(2000, easing = LinearEasing))
            cinemaPrev = shot                // promote: the new frame is now the settled one
        }
    }

    BackHandler {
        when {
            showChannelList -> showChannelList = false
            isLocked -> Unit // locked: ignore back so a pocket-press can't exit playback
            else -> onBack()
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Keep the tap detector as an ANCESTOR of the transport controls.
            // Compose dispatches button consumption before the parent handles
            // the Main pass, so a button tap cannot leak into this detector.
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (!isLocked) controlsVisible = !controlsVisibleState
                    },
                    onDoubleTap = { offset ->
                        if (isLocked || loadState.isLive) return@detectTapGestures

                        val x = offset.x / size.width.coerceAtLeast(1)
                        val y = offset.y / size.height.coerceAtLeast(1)
                        if (y !in 0.22f..0.78f) return@detectTapGestures

                        val forward = when {
                            x >= 0.75f -> true
                            x <= 0.25f -> false
                            else -> return@detectTapGestures
                        }

                        if (forward) viewModel.playerManager.seekForward()
                        else viewModel.playerManager.seekBackward()
                        seekForward = forward
                        seekBump++
                        if (!controlsVisibleState) hiddenSeekRipple = HiddenSeekRipple(seekBump, forward)
                    },
                )
            }
            .pointerInput(Unit) {
                var pinchAccumulator = 1f
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom == 1f) return@detectTransformGestures
                    pinchAccumulator *= zoom
                    when {
                        pinchAccumulator > 1.25f -> {
                            viewModel.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                            toastLabel = "Preenchendo a tela"
                            pinchAccumulator = 1f
                        }
                        pinchAccumulator < 0.8f -> {
                            viewModel.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT)
                            toastLabel = "Ajustado à tela"
                            pinchAccumulator = 1f
                        }
                    }
                }
            }
    ) {
        loadState.loadError?.let { error ->
            Box(
                Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AuroraColors.Error, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(error, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(18.dp))
                    com.auroraplay.iptv.presentation.components.GlassButton(
                        text = "Voltar",
                        onClick = onBack,
                    )
                }
            }
        }

        loadState.streamUrl?.takeIf { it.isNotBlank() }?.let { url ->
            PlayerScreenContent(
                streamUrl = url,
                isLive = loadState.isLive,
                startPositionMillis = loadState.resumePositionMillis,
                playerManager = viewModel.playerManager,
                resizeMode = loadState.resizeMode,
                showBufferingIndicator = !controlsVisible,
                onVideoTextureView = { videoTextureView = it },
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(playerHaze),
            )
        }

        // Cinema is drawn OVER the opaque PlayerView only inside the real
        // letterbox regions. This is important: placing it behind PlayerView
        // cannot work reliably with SurfaceView/TextureView because the video
        // surface may remain opaque. The video itself is never covered.
        val cCur = cinemaCur
        if (!pipActive && cinematicModeEnabled && cCur != null && !cCur.isRecycled && cCur.width > 0 &&
            playbackState.videoWidth > 0 && playbackState.videoHeight > 0 &&
            loadState.resizeMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) {
            CinematicBarsOverlay(
                prev = cinemaPrev?.takeIf { !it.isRecycled },
                cur = cCur,
                curAlpha = cinemaFade.value,
                videoWidth = playbackState.videoWidth,
                videoHeight = playbackState.videoHeight,
            )
        }

        // Pinch-to-resize feedback only — small, brief, no big centre text.
        toastLabel?.let { label ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }

        // Double-tap seek feedback. With the controls up, the matching −10/+10
        // button spins (via seekBump → externalSpinTick). With them hidden,
        // there's no button to spin, so a lone spinning glyph shows on the
        // tapped side, well out towards the edge — one shot per double-tap,
        // then it removes itself.
        hiddenSeekRipple?.takeIf { !controlsVisible }?.let { ripple ->
            key(ripple.id) {
                SeekRipple(
                    forward = ripple.forward,
                    seconds = seekSeconds,
                    onFinished = { if (hiddenSeekRipple?.id == ripple.id) hiddenSeekRipple = null },
                    modifier = Modifier
                        .align(if (ripple.forward) Alignment.CenterEnd else Alignment.CenterStart)
                        .fillMaxWidth(0.5f)
                        .wrapContentWidth(Alignment.CenterHorizontally),
                )
            }
        }

        // Discreet "jumping to the next episode" countdown — bottom-right,
        // always visible (controls up or not), clear of the gesture areas.
        val autoNextSecs = autoNextInSeconds
        if (!isLocked && !pipActive && autoNextSecs != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .displayCutoutPadding()
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = if (controlsVisible) 100.dp else 22.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "Próximo ep. em ${autoNextSecs}s",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Cancelar",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.cancelAutoNext() }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        if (pipActive) {
            // PiP: video only, nothing else.
        } else if (isLocked) {
            // Only the unlock affordance remains tappable while locked.
            IconButton(
                onClick = { isLocked = false },
                modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp),
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = "Desbloquear", tint = Color.White)
            }
        } else {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.985f),
                exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.985f),
                // Must fill the screen: AnimatedVisibility introduces its own
                // layout node, and without this the overlay's internal
                // Alignment.BottomStart / TopStart resolve against a
                // wrap-content box, which bunches every control at the top.
                // The background gesture detector is an ancestor of this overlay,
                // so child controls can consume their own taps before the parent
                // handles the empty-area gesture.
                modifier = Modifier.fillMaxSize(),
            ) {
                PlayerControlsOverlay(
                    title = loadState.title,
                    subtitle = loadState.subtitle,
                    isLive = loadState.isLive,
                    isPlaying = playbackState.isPlaying,
                    isBuffering = playbackState.isBuffering,
                    position = playbackState.positionMillis,
                    duration = playbackState.durationMillis,
                    isCasting = playbackState.isCasting,
                    castDeviceName = playbackState.castDeviceName,
                    hasNextEpisode = loadState.nextEpisode != null,
                    hasEpisodeList = loadState.contentType == ContentType.SERIES && loadState.episodes.size > 1,
                    hasAudioMenu = playbackState.availableAudioTracks.size > 1 ||
                        playbackState.availableSubtitleTracks.isNotEmpty(),
                    currentProgramLabel = loadState.currentProgramLabel,
                    programProgress = loadState.programProgress,
                    seekSeconds = seekSeconds,
                    seekTick = seekBump,
                    seekTickForward = seekForward,
                    onBack = onBack,
                    onPlayPause = { viewModel.playerManager.togglePlayPause() },
                    onSeekForward = { viewModel.playerManager.seekForward() },
                    onSeekBackward = { viewModel.playerManager.seekBackward() },
                    onSeekTo = { viewModel.playerManager.seekTo(it) },
                    scrubThumbnail = scrubThumbnail,
                    onScrub = { viewModel.requestScrubThumbnail(it) },
                    onScrubEnd = { viewModel.clearScrubThumbnail() },
                    onScrubbingChange = { isScrubbing = it },
                    onOpenSettings = { showSettingsSheet = true },
                    brightnessValue = brightnessValue,
                    onBrightnessDrag = { delta -> brightnessValue = viewModel.adjustBrightness(activity, delta) },
                    showRemainingTime = showRemainingTime,
                    onToggleTimeDisplay = { showRemainingTime = !showRemainingTime },
                    onNextEpisode = { viewModel.playNextEpisode() },
                    onShowEpisodes = { showEpisodeList = true },
                    onPreviousChannel = { viewModel.previousChannel() },
                    onNextChannel = { viewModel.nextChannel() },
                    onShowChannelList = { showChannelList = true },
                    onShowEpg = {
                        showChannelEpg = true
                        viewModel.loadChannelEpg()
                    },
                    onToggleFavorite = { viewModel.toggleFavoriteChannel() },
                    isFavorite = loadState.isFavorite,
                    onLock = { isLocked = true },
                    cinematicModeEnabled = cinematicModeEnabled,
                    onToggleCinematicMode = {
                        val next = !cinematicModeEnabled
                        viewModel.setCinemaMode(next)
                        // Explicit confirmation — the button is a small icon and
                        // the glow itself is subtle, so a tap with no feedback
                        // read as "nothing happened / it won't turn off".
                        toastLabel = if (next) "Modo cinema ligado" else "Modo cinema desligado"
                        // The ambient glow can only paint the letterbox space
                        // that FIT leaves, so turning Cinema on snaps back to
                        // FIT — otherwise a pinch-zoomed video has nowhere for
                        // it to show and the toggle looks dead.
                        if (next) viewModel.setResizeMode(
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        )
                    },
                    onOpenAudio = { showAudioSubsSheet = true },
                    onSkipIntro = { viewModel.playerManager.skipIntro() },
                )
            }
        }

        if (showChannelList) {
            LiveChannelQuickList(
                channels = loadState.liveChannels,
                currentChannelId = loadState.contentId,
                onSelect = { viewModel.switchChannel(it); showChannelList = false },
                onDismiss = { showChannelList = false },
            )
        }

        if (showEpisodeList) {
            EpisodePickerSheet(
                episodes = loadState.episodes,
                currentEpisodeId = loadState.currentEpisodeId,
                onSelect = { viewModel.switchToEpisode(it); showEpisodeList = false },
                onDismiss = { showEpisodeList = false },
            )
        }

        if (showChannelEpg) {
            val channelEpg by viewModel.channelEpg.collectAsState()
            ChannelEpgSheet(
                channelName = loadState.title,
                programs = channelEpg,
                onDismiss = { showChannelEpg = false },
            )
        }

        if (showAudioSubsSheet) {
            AudioAndSubtitlesSheet(
                audioTracks = playbackState.availableAudioTracks,
                onSelectAudio = { viewModel.selectAudioTrack(it) },
                subtitleTracks = playbackState.availableSubtitleTracks,
                subtitlesEnabled = playbackState.subtitlesEnabled,
                onSelectSubtitle = { viewModel.selectSubtitleTrack(it) },
                onDisableSubtitles = { viewModel.playerManager.disableSubtitles() },
                onDismiss = { showAudioSubsSheet = false },
            )
        }
        if (showSpeedSheet) {
            PlaybackSpeedSheet(
                currentSpeed = playbackState.playbackSpeed,
                onSelect = { viewModel.playerManager.setPlaybackSpeed(it) },
                onDismiss = { showSpeedSheet = false },
            )
        }
        if (showSettingsSheet) {
            PlayerSettingsSheet(
                speedLabel = "${playbackState.playbackSpeed}x",
                resizeLabel = resizeModeLabel(loadState.resizeMode),
                onOpenSpeed = { showSettingsSheet = false; showSpeedSheet = true },
                onCycleResize = { viewModel.cycleResizeMode() },
                onDismiss = { showSettingsSheet = false },
                hazeState = playerHaze,
            )
        }

        playbackState.errorMessage?.let { error ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AuroraColors.Error, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(error, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    com.auroraplay.iptv.presentation.components.GlassButton(text = "Tentar novamente", onClick = { viewModel.retry() })
                }
            }
        }
    }
}

/**
 * The frame, heavily blurred and oversized, painted into the real letterbox
 * bars that FIT leaves — a soft, out-of-focus continuation of the scene, the
 * way YouTube's ambient mode works. Two layers: [prev] stays fully opaque
 * while [cur] fades in over it ([curAlpha]), so there's no opacity dip and
 * therefore no flicker. Bars that don't exist aren't drawn.
 */
@Composable
private fun CinematicBarsOverlay(
    prev: android.graphics.Bitmap?,
    cur: android.graphics.Bitmap,
    curAlpha: Float,
    videoWidth: Int,
    videoHeight: Int,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val videoAspect = (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(0.2f, 5f)
        val renderedW = minOf(maxWidth, maxHeight * videoAspect)
        val renderedH = minOf(maxHeight, maxWidth / videoAspect)
        val barW = ((maxWidth - renderedW) / 2f).coerceAtLeast(0.dp)
        val barH = ((maxHeight - renderedH) / 2f).coerceAtLeast(0.dp)

        if (barH > 2.dp) {
            CinematicBarImage(prev, cur, curAlpha, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(barH), Alignment.TopCenter)
            CinematicBarImage(prev, cur, curAlpha, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(barH), Alignment.BottomCenter)
        }
        if (barW > 2.dp) {
            CinematicBarImage(prev, cur, curAlpha, Modifier.align(Alignment.CenterStart).fillMaxHeight().width(barW), Alignment.CenterStart)
            CinematicBarImage(prev, cur, curAlpha, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(barW), Alignment.CenterEnd)
        }
    }
}

@Composable
private fun CinematicBarImage(
    prev: android.graphics.Bitmap?,
    cur: android.graphics.Bitmap,
    curAlpha: Float,
    modifier: Modifier,
    align: Alignment,
) {
    Box(modifier.clipToBounds()) {
        prev?.let { CinematicLayer(it, align, alpha = 1f) }
        CinematicLayer(cur, align, alpha = curAlpha)
        // Keep the video the focus.
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.25f)))
    }
}

@Composable
private fun CinematicLayer(bitmap: android.graphics.Bitmap, align: Alignment, alpha: Float) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = align,
        // The settled layer is fully opaque and the incoming one fades 0->1
        // on top of it: a true linear crossfade with constant total
        // brightness. The old 0.9 multiplier left the settled layer
        // translucent, so the two stacked layers summed *brighter* mid-fade
        // and snapped back each cycle — that was the flicker. Darkening is
        // handled by the 0.25 black scrim in CinematicBarImage.
        alpha = alpha.coerceIn(0f, 1f),
        modifier = Modifier
            .fillMaxSize()
            .scale(1.35f)                       // hide the blurred edges
            .blur(44.dp, BlurredEdgeTreatment.Unbounded),
    )
}

/** Vertical slider used for brightness/volume inside the controls overlay —
 * a visible, always-in-the-same-place control instead of an invisible
 * drag-anywhere gesture that was easy to trigger by accident.
 *
 * Sized as a real, grabbable control (not the thin sliver it used to be): a
 * wide translucent pill, an 8dp track, and a knob riding the fill line so the
 * current level reads at a glance and there is an obvious thing to drag. */
@Composable
private fun VerticalMiniSlider(
    modifier: Modifier = Modifier,
    value: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onDrag: (delta: Float) -> Unit,
    width: androidx.compose.ui.unit.Dp = 52.dp,
    height: androidx.compose.ui.unit.Dp = 200.dp,
) {
    val pct = value.coerceIn(0f, 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(width)
            .height(height)
            // No capsule/border behind the bar — just the icon, track and
            // knob. A soft drop shadow keeps it legible over bright video.
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(-dragAmount / size.height.toFloat())
                }
            }
            .padding(vertical = 10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(24.dp)
                .shadow(6.dp, CircleShape),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .width(12.dp)
                .shadow(6.dp, RoundedCornerShape(100.dp))
                .clip(RoundedCornerShape(100.dp))
                .background(Color.White.copy(alpha = 0.30f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(pct)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color.White),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    Modifier
                        .offset(y = (-9).dp)
                        .size(18.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
private fun PlayerControlsOverlay(
    title: String,
    subtitle: String?,
    isLive: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    position: Long,
    duration: Long,
    isCasting: Boolean,
    castDeviceName: String?,
    hasNextEpisode: Boolean,
    hasAudioMenu: Boolean,
    currentProgramLabel: String?,
    programProgress: Float?,
    isFavorite: Boolean,
    seekSeconds: Int,
    seekTick: Int,
    seekTickForward: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    scrubThumbnail: android.graphics.Bitmap?,
    onScrub: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    brightnessValue: Float,
    onBrightnessDrag: (delta: Float) -> Unit,
    showRemainingTime: Boolean,
    onToggleTimeDisplay: () -> Unit,
    onNextEpisode: () -> Unit,
    onPreviousChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onShowChannelList: () -> Unit,
    hasEpisodeList: Boolean = false,
    onShowEpisodes: () -> Unit = {},
    onShowEpg: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLock: () -> Unit,
    cinematicModeEnabled: Boolean,
    onToggleCinematicMode: () -> Unit,
    onOpenAudio: () -> Unit,
    onSkipIntro: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.85f))
                )
            )
    ) {
        // Top bar and bottom controls flow in a Column so they pin to the top
        // and bottom edges (with the notch + 3-button-nav insets applied).
        // The transport cluster and the brightness slider are drawn *outside*
        // that Column, aligned straight to this full-screen Box — so −10 ·
        // Play · +10 land on the exact centre of the screen on every aspect
        // ratio, the same point the buffering spinner uses, instead of the
        // midpoint between an unequal top bar and bottom bar.
        Column(Modifier.fillMaxSize()) {
            // ---- Top bar: back + title block (left), skip-intro pill + ⋮ (right) ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp, end = 8.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isCasting && castDeviceName != null) {
                        Text(
                            "Transmitindo em $castDeviceName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // An intro is only ever in the first few minutes, so the pill
                // has no reason to still be offered an hour into the film.
                if (!isLive && position < SKIP_INTRO_WINDOW_MILLIS) {
                    ControlPill(text = "Pular introdução", icon = Icons.Default.FastForward, onClick = onSkipIntro)
                    Spacer(Modifier.width(4.dp))
                }
                // Three-dots: secondary settings (speed, aspect ratio). Sized
                // for touch but visually quieter than the transport controls.
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Mais opções", tint = Color.White.copy(alpha = 0.9f))
                }
            }

            // Flex fills everything the top bar and the bottom controls don't;
            // the transport cluster is a separate centred layer (below), not
            // wedged in here, so it can't be pushed off-centre by an unequal
            // top/bottom.
            Spacer(Modifier.weight(1f))

            // ---- Bottom: thin timeline + time, live badge, then a compact,
            // centred row of secondary actions. Slightly inset from the edges
            // so nothing kisses the border. ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Kept well clear of the screen edges so the scrub area
                    // never collides with Android's edge gesture zones.
                    .padding(horizontal = 28.dp, vertical = 8.dp)
                    .padding(bottom = 4.dp),
            ) {
                if (!isLive && duration > 0) {
                    var scrubFrac by remember { mutableStateOf<Float?>(null) }
                    val curFrac = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    val shownFrac = (scrubFrac ?: curFrac).coerceIn(0f, 1f)
                    val shownMillis = (shownFrac * duration).toLong()

                    // Scrub preview: a card that floats above the bar and
                    // tracks the finger (clamped 10–90% so it never leaves the
                    // screen), showing the target time and — when the on-device
                    // frame extractor can produce one for this stream — a
                    // thumbnail of exactly where playback will land. The card
                    // now shows for the whole scrub even before/without a
                    // frame, so there's always a clear read of the destination.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = scrubFrac != null,
                        enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.9f),
                        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.9f),
                    ) {
                        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(shownFrac.coerceIn(0.10f, 0.90f)),
                                horizontalAlignment = Alignment.End,
                            ) {
                                // Only shown when a real frame is available —
                                // an always-empty preview box (placeholder icon,
                                // no picture) is just noise, so with on-device
                                // extraction off this collapses to the time label.
                                scrubThumbnail?.let { frame ->
                                    Box(
                                        modifier = Modifier
                                            .width(140.dp)
                                            .aspectRatio(16f / 9f)
                                            .shadow(6.dp, RoundedCornerShape(10.dp))
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.Black),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Image(
                                            bitmap = frame.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                                Text(
                                    (shownMillis / 1000).toTimeLabel(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Mirror of the fixed time slot on the right, so the
                        // scrub track itself is centred and symmetric.
                        Spacer(Modifier.width(64.dp))
                        ThinSeekBar(
                            fraction = shownFrac,
                            onScrubStart = { onScrubbingChange(true) },
                            onScrub = { f ->
                                scrubFrac = f
                                onScrub((f * duration).toLong())
                            },
                            onScrubEnd = { f ->
                                onSeekTo((f * duration).toLong())
                                scrubFrac = null
                                onScrubEnd()
                                onScrubbingChange(false)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        val remainingMillis = (duration - shownMillis).coerceAtLeast(0)
                        val timeLabel = if (showRemainingTime) {
                            "-${(remainingMillis / 1000).toTimeLabel()}"
                        } else {
                            (shownMillis / 1000).toTimeLabel()
                        }
                        Text(
                            timeLabel,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier
                                // A fixed end slot keeps the usable timeline
                                // width stable and symmetric as -M:SS grows
                                // into -H:MM:SS during long films.
                                .width(64.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClickLabel = "Alternar entre tempo restante e decorrido", onClick = onToggleTimeDisplay)
                                .padding(horizontal = 2.dp, vertical = 4.dp),
                        )
                    }
                } else if (isLive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(AuroraColors.Error, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("AO VIVO", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        currentProgramLabel?.let {
                            Spacer(Modifier.width(12.dp))
                            Text(it, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    programProgress?.let { progress ->
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                // Secondary actions stay in one wrap-content group centered
                // against the actual player width. Equal slots keep the group
                // symmetric on every landscape aspect ratio.
                // Landscape alignment rule:
                // - odd number of actions: the middle action's visual centre is
                //   exactly the centre of the player;
                // - even number: the gap between the two middle actions is the
                //   exact centre of the player.
                // Keeping the group wrap-content and centring it against the
                // *full player Box* (not an inset/padded content column) makes
                // this invariant hold even when Android reports asymmetric
                // landscape navigation/cutout insets.
                val actionCount =
                    (if (isLive) 3 else 0) +
                    (if (hasAudioMenu) 1 else 0) +
                    1 + // Bloquear
                    1 + // Cinema
                    (if (hasEpisodeList) 1 else 0) +
                    (if (hasNextEpisode) 1 else 0)
                val slotWidth = if (actionCount >= 5) 66.dp else 72.dp
                val slot = Modifier.width(slotWidth)
                Spacer(Modifier.height(7.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.28f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        if (isLive) {
                            PlayerBottomAction(
                                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                label = "Favoritar",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                                onClick = onToggleFavorite,
                                modifier = slot,
                            )
                            PlayerBottomAction(Icons.AutoMirrored.Filled.ViewList, "Canais", onClick = onShowChannelList, modifier = slot)
                            PlayerBottomAction(Icons.Default.CalendarMonth, "Programação", onClick = onShowEpg, modifier = slot)
                        }
                        if (hasAudioMenu) {
                            PlayerBottomAction(Icons.Default.Audiotrack, "Áudio", onClick = onOpenAudio, modifier = slot)
                        }
                        PlayerBottomAction(Icons.Default.Lock, "Bloquear", onClick = onLock, modifier = slot)
                        PlayerBottomAction(
                            icon = Icons.Default.Theaters,
                            label = if (cinematicModeEnabled) "Cinema ✓" else "Cinema",
                            tint = if (cinematicModeEnabled) Color.White else Color.White.copy(alpha = 0.92f),
                            active = cinematicModeEnabled,
                            onClick = onToggleCinematicMode,
                            modifier = slot,
                        )
                        if (hasEpisodeList) {
                            PlayerBottomAction(Icons.AutoMirrored.Filled.PlaylistPlay, "Episódios", onClick = onShowEpisodes, modifier = slot)
                        }
                        if (hasNextEpisode) {
                            PlayerBottomAction(Icons.Default.SkipNext, "Próximo ep.", onClick = onNextEpisode, modifier = slot)
                        }
                    }
                }
            }
        }

        // ---- Brightness slider: vertically centred on the left edge.
        //      displayCutoutPadding() keeps it clear of a landscape edge
        //      notch; it isn't meant to be pixel-aligned with anything. ----
        VerticalMiniSlider(
            value = brightnessValue,
            icon = Icons.Default.BrightnessHigh,
            onDrag = onBrightnessDrag,
            width = 56.dp,
            height = 176.dp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .displayCutoutPadding()
                .padding(start = 18.dp),
        )

        // ---- Transport cluster: aligned to this full-screen Box, so it sits
        //      on the true centre of the screen on any aspect ratio and lines
        //      up exactly with the buffering spinner (also centred here). ----
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .width(246.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Equal 84dp slots make the three visual centres mathematically
            // symmetric around the exact screen centre. The Play button is not
            // allowed to change the spacing when its 64dp target is measured.
            Box(Modifier.width(82.dp), contentAlignment = Alignment.Center) {
                if (isLive) {
                    TransportIconButton(Icons.Default.SkipPrevious, "Canal anterior", nudgeForward = false, onClick = onPreviousChannel)
                } else {
                    SeekButton(forward = false, seconds = seekSeconds, onClick = onSeekBackward, externalSpinTick = seekTick, externalSpinForward = seekTickForward)
                }
            }
            Box(Modifier.width(82.dp), contentAlignment = Alignment.Center) {
                PlayPauseButton(isPlaying = isPlaying, isBuffering = isBuffering, onClick = onPlayPause)
            }
            Box(Modifier.width(82.dp), contentAlignment = Alignment.Center) {
                if (isLive) {
                    TransportIconButton(Icons.Default.SkipNext, "Próximo canal", nudgeForward = true, onClick = onNextChannel)
                } else {
                    SeekButton(forward = true, seconds = seekSeconds, onClick = onSeekForward, externalSpinTick = seekTick, externalSpinForward = seekTickForward)
                }
            }
        }
    }
}

/** Plain white transport icon (prev/next channel) with a comfortable touch
 * target but a compact glyph. On tap the glyph nudges in its travel
 * direction and springs back — a channel-change echo of the seek spin. */
@Composable
private fun TransportIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    nudgeForward: Boolean,
    onClick: () -> Unit,
) {
    val nudge = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    IconButton(
        onClick = {
            onClick()
            scope.launch {
                nudge.snapTo(0f)
                nudge.animateTo(if (nudgeForward) 1f else -1f, tween(110, easing = EaseOutCubic))
                nudge.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
        },
        modifier = Modifier.size(60.dp),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier
                .size(34.dp)
                .offset { IntOffset((nudge.value * 7).dp.roundToPx(), 0) },
        )
    }
}

/** −10 / +10: 60dp touch target, 36dp glyph. One control, nothing else — on
 * tap the single glyph makes one full turn in the seek direction (then resets
 * instantly, since 360° ≡ 0°, so it never unwinds backwards), exactly like the
 * reference player. No extra disc, no side label: just the one icon.
 *
 * [externalSpinTick] lets a double-tap on the video drive the *same* spin on
 * whichever of the two buttons matches the tap direction — so when the
 * controls are up, a double-tap animates one seek button instead of drawing a
 * second, separate ripple icon next to it. */
@Composable
private fun SeekButton(
    forward: Boolean,
    seconds: Int,
    onClick: () -> Unit,
    externalSpinTick: Int = 0,
    externalSpinForward: Boolean = false,
) {
    val glyph = when {
        forward && seconds == 5 -> Icons.Default.Forward5
        forward -> Icons.Default.Forward10
        seconds == 5 -> Icons.Default.Replay5
        else -> Icons.Default.Replay10
    }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    suspend fun runSpin() {
        spin.snapTo(0f)
        spin.animateTo(if (forward) 1f else -1f, tween(440, easing = EaseOutCubic))
        spin.snapTo(0f)
    }

    // Spin only on a *real* change of the tick while this button is composed,
    // and only for the button whose direction matches the double-tap. Seeding
    // lastTick with the current value means entering composition (controls
    // fading in) never triggers a spurious spin from an earlier double-tap.
    var lastTick by remember { mutableIntStateOf(externalSpinTick) }
    LaunchedEffect(externalSpinTick) {
        if (externalSpinTick != lastTick) {
            lastTick = externalSpinTick
            if (externalSpinForward == forward) runSpin()
        }
    }

    IconButton(
        onClick = {
            onClick()
            scope.launch { runSpin() }
        },
        modifier = Modifier.size(60.dp),
    ) {
        Icon(
            glyph,
            contentDescription = if (forward) "Avançar $seconds segundos" else "Retroceder $seconds segundos",
            tint = Color.White,
            modifier = Modifier
                .size(36.dp)
                .rotate(spin.value * 360f),
        )
    }
}

/** Central play/pause: 64dp disc, quiet fill, with a soft crossfade + scale
 * between the two icons instead of a hard swap, and a quick bounce-in on
 * press like the reference. While buffering, a progress ring sits *inside*
 * the disc — so the "loading" indicator is always dead-concentric with the
 * play button instead of being a separate centred element that can drift a
 * few pixels off on some aspect ratios. */
@Composable
private fun PlayPauseButton(isPlaying: Boolean, isBuffering: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "playPausePress",
    )
    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .size(64.dp)
            .scale(pressScale)
            .background(Color.White.copy(alpha = 0.16f), CircleShape),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isBuffering) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(58.dp),
                )
            }
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.7f)) togetherWith
                        (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.7f))
                },
                label = "playPauseIcon",
            ) { playing ->
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pausar" else "Reproduzir",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }
        }
    }
}

/** Slim, elegant timeline — a precise track, comfortably large round thumb, purple
 * progress. Hand-rolled so the track really is thin (Material's Slider has a
 * fixed 4dp track) and so the whole 24dp band stays a comfortable touch area. */
@Composable
private fun ThinSeekBar(
    fraction: Float,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var lastFrac by remember { mutableFloatStateOf(fraction) }
    val shown = fraction.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        dragging = true
                        lastFrac = (off.x / widthPx).coerceIn(0f, 1f)
                        onScrubStart()
                        onScrub(lastFrac)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        lastFrac = (change.position.x / widthPx).coerceIn(0f, 1f)
                        onScrub(lastFrac)
                    },
                    onDragEnd = { dragging = false; onScrubEnd(lastFrac) },
                    onDragCancel = { dragging = false; onScrubEnd(lastFrac) },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    val f = (off.x / widthPx).coerceIn(0f, 1f)
                    onScrubStart(); onScrub(f); onScrubEnd(f)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackH = 5.dp
        val knob by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (dragging) 28.dp else 20.dp,
            animationSpec = tween(140),
            label = "seekKnob",
        )
        Box(
            Modifier.fillMaxWidth().height(trackH).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f))
        )
        Box(
            Modifier.fillMaxWidth(shown).height(trackH).clip(CircleShape).background(primary),
            contentAlignment = Alignment.CenterEnd,
        ) {
            // Precise position dot — a clean solid white circle (no ring),
            // with a soft shadow for contrast over bright frames and a faint
            // halo while dragging. Offset by half its size so its centre sits
            // exactly on the end of the filled track.
            Box(
                modifier = Modifier
                    .size(knob)
                    .offset { IntOffset(knob.roundToPx() / 2, 0) }
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (dragging) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}


/** Double-tap seek feedback shown when the controls are hidden — the same
 * motion as [SeekButton]: one plain white glyph turning once, then fading.
 * [onFinished] fires once the fade-out is done so the caller can drop it. */
@Composable
private fun SeekRipple(
    forward: Boolean,
    seconds: Int,
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(true) }
    val spin = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        spin.animateTo(if (forward) 1f else -1f, tween(320, easing = EaseOutCubic))
        visible = false
        delay(240)          // let the fade-out play before the parent removes us
        onFinished()
    }
    val glyph = when {
        forward && seconds == 5 -> Icons.Default.Forward5
        forward -> Icons.Default.Forward10
        seconds == 5 -> Icons.Default.Replay5
        else -> Icons.Default.Replay10
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(90)),
        exit = fadeOut(tween(200)),
        modifier = modifier,
    ) {
        Icon(
            glyph,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(40.dp).rotate(spin.value * 360f),
        )
    }
}

private fun resizeModeLabel(mode: Int): String = when (mode) {
    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Preencher"
    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Esticar"
    else -> "Ajustar"
}

/** Floating ⋮ settings panel — anchored top-right, fades + scales in from
 * that corner, closes on an outside tap. Holds the controls pulled out of the
 * bottom bar (speed, aspect ratio) with icon + name + current value. */
@Composable
private fun PlayerSettingsSheet(
    speedLabel: String,
    resizeLabel: String,
    onOpenSpeed: () -> Unit,
    onCycleResize: () -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null,
) {
    var open by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { open = true }
    fun close() { open = false; closing = true }
    // Let the exit animation play before the caller drops us — but only once a
    // close was actually requested, never on the initial (open == false) frame.
    LaunchedEffect(closing) { if (closing) { delay(180.milliseconds); onDismiss() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Barely-there dim so the panel reads as floating without blacking
            // out the video underneath.
            .background(Color.Black.copy(alpha = if (open) 0.12f else 0f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { close() },
            ),
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(150)) + scaleIn(tween(170), initialScale = 0.9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .displayCutoutPadding()
                // Sits BELOW the top bar so it never covers the ⋮ that opens
                // and closes it (56dp top bar + a small gap).
                .padding(top = 60.dp, end = 10.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(200.dp)
                    // Translucent "glass" — the video shows through. Follows the
                    // FrostGlass setting: real backdrop blur of the video on
                    // API 31+, flat black wash when the toggle is off.
                    .frostSurface(
                        shape = RoundedCornerShape(16.dp),
                        flat = Color.Black.copy(alpha = 0.62f),
                        tint = AuroraColors.SurfaceDark,
                        haze = hazeState,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(vertical = 4.dp),
            ) {
                PlayerSettingRow(Icons.Default.Speed, "Velocidade", speedLabel) { close(); onOpenSpeed() }
                PlayerSettingRow(Icons.Default.AspectRatio, "Proporção", resizeLabel) { onCycleResize() }
            }
        }
    }
}

@Composable
private fun PlayerSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = AuroraColors.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, color = AuroraColors.TextPrimary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Icon + label pair used for every action in the bottom row — matches the
 * reference layout (Speed / Lock / Episodes / Audio & Subtitles / Next Ep.)
 * instead of unlabelled icons alone, which were harder to identify at a
 * glance. */
@Composable
private fun PlayerBottomAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    active: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            // A lit fill makes an on/off action unmistakable — a tint change
            // alone on a 20dp glyph was too easy to miss (read as "the button
            // did nothing").
            .then(
                if (active) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private const val SKIP_INTRO_WINDOW_MILLIS = 5 * 60_000L

@Composable
private fun ControlPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    text: String = "Pular introdução",
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun LiveChannelQuickList(
    channels: List<com.auroraplay.iptv.domain.model.Channel>,
    currentChannelId: String,
    onSelect: (com.auroraplay.iptv.domain.model.Channel) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.42f)
                .fillMaxHeight()
                .background(AuroraColors.BackgroundElevated)
                .padding(top = 16.dp)
                // Swallow taps inside the panel so they don't bubble to the
                // scrim's dismiss handler.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
        ) {
            Text(
                "Canais",
                style = MaterialTheme.typography.titleLarge,
                color = AuroraColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            var query by remember { mutableStateOf("") }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AuroraColors.SurfaceHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = AuroraColors.TextTertiary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Buscar canal", color = AuroraColors.TextTertiary, style = MaterialTheme.typography.bodyMedium)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = AuroraColors.TextPrimary, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Limpar busca",
                        tint = AuroraColors.TextTertiary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { query = "" },
                    )
                }
            }

            val visibleChannels = if (query.isBlank()) {
                channels
            } else {
                channels.filter { it.name.contains(query.trim(), ignoreCase = true) }
            }
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(visibleChannels, key = { it.id }) { channel ->
                    com.auroraplay.iptv.presentation.components.ChannelCard(
                        name = channel.name,
                        logoUrl = channel.logoUrl,
                        currentProgram = channel.currentProgram?.title,
                        selected = channel.id == currentChannelId,
                        onClick = { onSelect(channel) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
    }
}

/** Right-side drawer to jump to any episode of the current series without
 * leaving the player — grouped by season, current episode highlighted. */
@Composable
private fun EpisodePickerSheet(
    episodes: List<com.auroraplay.iptv.domain.model.Episode>,
    currentEpisodeId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val currentIndex = episodes.indexOfFirst { it.id == currentEpisodeId }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) runCatching { listState.scrollToItem(currentIndex) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.46f)
                .fillMaxHeight()
                .background(AuroraColors.BackgroundElevated)
                .displayCutoutPadding()
                .navigationBarsPadding()
                .padding(top = 16.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            Text(
                "Episódios",
                style = MaterialTheme.typography.titleLarge,
                color = AuroraColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                var lastSeason = Int.MIN_VALUE
                episodes.forEach { ep ->
                    if (ep.seasonNumber != lastSeason) {
                        lastSeason = ep.seasonNumber
                        item(key = "s${ep.seasonNumber}") {
                            Text(
                                "Temporada ${ep.seasonNumber}",
                                style = MaterialTheme.typography.labelMedium,
                                color = AuroraColors.TextTertiary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
                            )
                        }
                    }
                    item(key = ep.id) {
                        val active = ep.id == currentEpisodeId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    else Color.White.copy(alpha = 0.04f)
                                )
                                .clickable { onSelect(ep.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                "E${ep.episodeNumber}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) MaterialTheme.colorScheme.primary else AuroraColors.TextSecondary,
                                modifier = Modifier.width(34.dp),
                            )
                            Text(
                                ep.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (active) AuroraColors.TextPrimary else AuroraColors.TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (active) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Right-side drawer with the full-day schedule for the channel that's
 * playing — opened by the "Programação" action in the live controls. */
@Composable
private fun ChannelEpgSheet(
    channelName: String,
    programs: List<com.auroraplay.iptv.domain.model.EpgProgram>,
    onDismiss: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val timeFmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.forLanguageTag("pt-BR")) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.46f)
                .fillMaxHeight()
                .background(AuroraColors.BackgroundElevated)
                .padding(top = 16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Text(
                "Programação",
                style = MaterialTheme.typography.titleLarge,
                color = AuroraColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                channelName,
                style = MaterialTheme.typography.bodySmall,
                color = AuroraColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            Spacer(Modifier.height(8.dp))

            if (programs.isEmpty()) {
                Text(
                    "Sem guia de programação para este canal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuroraColors.TextTertiary,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    items(programs, key = { it.id }) { p ->
                        val isNow = now in p.startMillis until p.endMillis
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        ) {
                            Text(
                                timeFmt.format(java.util.Date(p.startMillis)),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isNow) MaterialTheme.colorScheme.primary else AuroraColors.TextSecondary,
                                modifier = Modifier.width(48.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    p.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isNow) AuroraColors.TextPrimary else AuroraColors.TextSecondary,
                                    fontWeight = if (isNow) androidx.compose.ui.text.font.FontWeight.Bold else null,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (p.description.isNotBlank()) {
                                    Text(
                                        p.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AuroraColors.TextTertiary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (isNow) {
                                Text(
                                    "AGORA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        HorizontalDivider(color = AuroraColors.Divider)
                    }
                }
            }
        }
    }
}
