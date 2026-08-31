package com.auroraplay.iptv.presentation.player

import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.view.TextureView
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.auroraplay.iptv.core.theme.AuroraColors
import com.auroraplay.iptv.player.PlayerManager

/**
 * Reusable, chrome-less video surface. Used both as the Live TV mini-preview
 * and embedded at the top of the full PlayerScreen. A single shared
 * PlayerManager (ExoPlayer, plus CastPlayer when casting) is used so
 * switching between preview and full screen never re-buffers.
 *
 * When a Cast session is active, playback happens on the remote device —
 * there is no local video to render — so a "Reproduzindo em <dispositivo>"
 * placeholder is shown instead of an empty/frozen surface.
 */
@Composable
fun PlayerScreenContent(
    streamUrl: String,
    isLive: Boolean,
    modifier: Modifier = Modifier,
    startPositionMillis: Long = 0L,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    /** Set false when the caller draws its own buffering indicator (the full
     * player does, inside the play/pause button, so the two never double up
     * or sit at slightly different centres). */
    showBufferingIndicator: Boolean = true,
    playerManager: PlayerManager = hiltPlayerManager(),
) {
    LaunchedEffect(streamUrl) {
        // A live stream has no meaningful resume point — seeking to a stored
        // position on a live edge either fails or drops the viewer far behind.
        playerManager.play(streamUrl, if (isLive) 0L else startPositionMillis)
    }

    val state by playerManager.state.collectAsState()

    if (state.isCasting) {
        Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Cast, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(40.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Reproduzindo em ${state.castDeviceName ?: "dispositivo Cast"}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        return
    }

    val activePlayer by playerManager.activePlayerFlow.collectAsState()

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            val view = LayoutInflater.from(context)
                .inflate(com.auroraplay.iptv.R.layout.player_surface, null, false) as PlayerView

            view.useController = false
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            view.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            view.setResizeMode(resizeMode)
            view.setShutterBackgroundColor(AndroidColor.TRANSPARENT)
            view.setBackgroundColor(AndroidColor.TRANSPARENT)
            view.player = activePlayer

            // A TextureView is required so the Compose cinematic layer can be
            // drawn above the player surface. Make the unused letterbox pixels
            // transparent so the cinematic image remains visible in them.
            // TextureView rejects background drawables on Android, including
            // a transparent one, so only its opacity may be changed here.
            view.findTextureView()?.apply {
                isOpaque = false
            }

            view
        },
        update = { view ->
            view.player = activePlayer
            view.setResizeMode(resizeMode)
        },
    )

    if (state.isBuffering && showBufferingIndicator) {
        Box(modifier.fillMaxSize()) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}


private fun View.findTextureView(): TextureView? {
    if (this is TextureView) return this
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            findTextureViewFrom(getChildAt(index))?.let { return it }
        }
    }
    return null
}

private fun findTextureViewFrom(view: View): TextureView? {
    if (view is TextureView) return view
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            findTextureViewFrom(view.getChildAt(index))?.let { return it }
        }
    }
    return null
}

/** Small helper so PlayerManager (a Singleton) can be obtained without a full ViewModel wrapper in simple previews. */
@Composable
fun hiltPlayerManager(): PlayerManager {
    val entryPoint = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel<PlayerManagerHolderViewModel>()
    return entryPoint.playerManager
}
