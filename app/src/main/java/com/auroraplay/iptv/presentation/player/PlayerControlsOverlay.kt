package com.auroraplay.iptv.presentation.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.core.util.toTimeLabel
import com.auroraplay.iptv.presentation.components.tvFocusable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerControlsOverlay(
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
    /** Focus lands here the instant the controls become visible — see
     * PlayerScreen's comment on why the whole-screen root can't be the focus
     * holder itself. */
    playPauseFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
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
                IconButton(onClick = onBack, modifier = Modifier.tvFocusable(shape = CircleShape, accent = Color.White)) {
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
                IconButton(onClick = onOpenSettings, modifier = Modifier.tvFocusable(shape = CircleShape, accent = Color.White)) {
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
                                        androidx.compose.foundation.Image(
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
                                .tvFocusable(shape = RoundedCornerShape(6.dp), accent = Color.White)
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
            label = "Brilho, ${(brightnessValue.coerceIn(0f, 1f) * 100).toInt()} por cento",
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
                PlayPauseButton(isPlaying = isPlaying, isBuffering = isBuffering, onClick = onPlayPause, focusRequester = playPauseFocusRequester)
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
    /** Announced by TalkBack — this control has no visible label otherwise
     * (just an icon), so screen-reader users had no way to tell what it was
     * or hear its current level. */
    label: String,
    onDrag: (delta: Float) -> Unit,
    width: androidx.compose.ui.unit.Dp = 52.dp,
    height: androidx.compose.ui.unit.Dp = 200.dp,
) {
    val pct = value.coerceIn(0f, 1f)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(width)
            .height(height)
            .then(
                if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(100.dp))
                else Modifier
            )
            .semantics(mergeDescendants = true) {
                contentDescription = label
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(pct, 0f..1f)
                setProgress { target -> onDrag(target - pct); true }
            }
            .focusable(interactionSource = interactionSource)
            // There's no drag gesture to derive a step from with a D-pad —
            // up/down nudges by a fixed step instead.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { onDrag(SLIDER_KEY_STEP); true }
                    Key.DirectionDown -> { onDrag(-SLIDER_KEY_STEP); true }
                    else -> false
                }
            }
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
        modifier = Modifier.size(60.dp).tvFocusable(shape = CircleShape, accent = Color.White),
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
        modifier = Modifier.size(60.dp).tvFocusable(shape = CircleShape, accent = Color.White),
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
private fun PlayPauseButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onClick: () -> Unit,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "playPausePress",
    )
    val ringAlpha by androidx.compose.animation.core.animateFloatAsState(if (focused) 1f else 0f, label = "playPauseFocusRing")
    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .size(64.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(pressScale)
            .background(Color.White.copy(alpha = 0.16f), CircleShape)
            .border(2.dp, Color.White.copy(alpha = ringAlpha), CircleShape),
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
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .then(
                if (focused) Modifier.border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                else Modifier
            )
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .focusable(interactionSource = interactionSource)
            // Dragging the bar has no D-pad equivalent — left/right nudges
            // the position by a fixed step per press instead, and commits
            // immediately (mirroring a tap) since there's no "release" event
            // from a key the way there is from a finger.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val step = when (event.key) {
                    Key.DirectionLeft -> -SEEK_KEY_STEP_FRACTION
                    Key.DirectionRight -> SEEK_KEY_STEP_FRACTION
                    else -> return@onKeyEvent false
                }
                val f = (fraction + step).coerceIn(0f, 1f)
                onScrubStart(); onScrub(f); onScrubEnd(f)
                true
            }
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
fun SeekRipple(
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

fun resizeModeLabel(mode: Int): String = when (mode) {
    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Preencher"
    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Esticar"
    else -> "Ajustar"
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
            .tvFocusable(shape = RoundedCornerShape(12.dp), accent = Color.White)
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

/** D-pad left/right step on [ThinSeekBar], as a fraction of total duration —
 * there's no drag gesture to derive a step from, so this is a fixed nudge. */
private const val SEEK_KEY_STEP_FRACTION = 0.02f

/** D-pad up/down step on [VerticalMiniSlider], as a fraction of full range. */
private const val SLIDER_KEY_STEP = 0.05f

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
