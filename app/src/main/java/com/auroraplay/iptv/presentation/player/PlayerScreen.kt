package com.auroraplay.iptv.presentation.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    // System file picker for a local .srt — Xtream never offers subtitles of
    // its own, only whatever's muxed into the stream.
    val subtitlePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // OpenDocument already grants a read permission that outlives this
        // Activity instance's own lifecycle — plenty for "load it and watch
        // now"; there is no "remember my last subtitle" feature to persist
        // it further for.
        if (uri != null) viewModel.loadExternalSubtitle(uri)
    }
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
                    // Always offered locally now, even with a single embedded
                    // audio track and no embedded subtitles — it's also the
                    // only way to reach "Carregar legenda (.srt)". Still
                    // hidden while casting: nothing behind it applies there.
                    hasAudioMenu = playbackState.availableAudioTracks.size > 1 ||
                        playbackState.availableSubtitleTracks.isNotEmpty() ||
                        !playbackState.isCasting,
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
                // Not offered while casting: the file lives on this device and
                // the Cast receiver has no way to read a local content:// URI.
                onLoadSubtitleFile = if (playbackState.isCasting) null else {
                    { subtitlePickerLauncher.launch(arrayOf("*/*")) }
                },
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
