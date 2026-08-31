package com.auroraplay.iptv.presentation.components

import android.os.Handler
import android.os.Looper
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Inline trailer player backed by a verified YouTube video id from TMDB.
 * The app's VOD stream is deliberately not accepted here: it is only used by
 * the primary "Assistir" action, so the preview cannot start a full movie.
 */
@Composable
@SuppressLint("SetJavaScriptEnabled")
fun TrailerPreview(
    title: String,
    youtubeVideoId: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var webView by remember(youtubeVideoId) { mutableStateOf<WebView?>(null) }
    var playing by rememberSaveable(youtubeVideoId) { mutableStateOf(false) }
    var muted by rememberSaveable(youtubeVideoId) { mutableStateOf(false) }
    var progress by rememberSaveable(youtubeVideoId) { mutableFloatStateOf(0f) }
    var playerReady by rememberSaveable(youtubeVideoId) { mutableStateOf(false) }
    var playerError by rememberSaveable(youtubeVideoId) { mutableStateOf<Int?>(null) }

    val bridge = remember(youtubeVideoId) {
        YoutubePlayerBridge(
            onPlaybackState = { state -> playing = state == YOUTUBE_STATE_PLAYING },
            onProgress = { position, duration ->
                progress = if (duration > 0.0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
            },
            onReady = {
                playerReady = true
                playerError = null
            },
            onError = { errorCode ->
                playerError = errorCode
                playing = false
            },
        )
    }

    LaunchedEffect(active, webView) {
        if (!active) {
            webView?.evaluateJavascript("pauseTrailer()", null)
            playing = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                WebView(viewContext).also { playerView ->
                    playerView.setBackgroundColor(android.graphics.Color.BLACK)
                    playerView.settings.javaScriptEnabled = true
                    playerView.settings.domStorageEnabled = true
                    // The visible Compose play button issues playVideo() after
                    // a real tap. Requiring a second, WebView-local gesture
                    // makes that command silently fail on many Android
                    // WebView versions, leaving the trailer black.
                    playerView.settings.mediaPlaybackRequiresUserGesture = false
                    playerView.settings.allowFileAccess = false
                    playerView.settings.allowContentAccess = false
                    playerView.webChromeClient = WebChromeClient()
                    playerView.webViewClient = object : WebViewClient() {
                        // The former implementation returned true for every
                        // navigation. That also cancelled YouTube's own
                        // iframe navigation before the video was created.
                        // This WebView only receives a validated video id, so
                        // allow the embedded player to handle its resources.
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = false
                    }
                    playerView.addJavascriptInterface(bridge, BRIDGE_NAME)
                    playerView.loadDataWithBaseURL(
                        YOUTUBE_BASE_URL,
                        youtubePlayerHtml(youtubeVideoId),
                        "text/html",
                        "utf-8",
                        null,
                    )
                    webView = playerView
                }
            },
            onRelease = { playerView ->
                // AndroidView has detached the view before this callback, so
                // it is safe to release the renderer here. Destroying it from
                // DisposableEffect raced while it was still attached and
                // caused the repeated Chromium errors seen in logcat.
                playerView.removeJavascriptInterface(BRIDGE_NAME)
                playerView.stopLoading()
                playerView.destroy()
                if (webView === playerView) webView = null
            },
        )

        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.05f),
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.64f),
                )
            )
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(62.dp)
                .background(Color.Black.copy(alpha = 0.58f), CircleShape)
                .clickable {
                    webView?.evaluateJavascript(if (playing) "pauseTrailer()" else "playTrailer()", null)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Pausar trailer" else "Reproduzir trailer",
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }

        if (!playerReady && playerError == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        }

        if (playerError != null) {
            Text(
                text = "Abrir trailer no YouTube",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                    .clickable {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.youtube.com/watch?v=$youtubeVideoId"),
                            ),
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.72f), CircleShape)
                .clickable {
                    muted = !muted
                    webView?.evaluateJavascript("setMuted($muted)", null)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (muted) "Ativar som" else "Desativar som",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 9.dp)
                .clip(RoundedCornerShape(10.dp)),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.30f),
        )

        Text(
            "TRAILER",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

private class YoutubePlayerBridge(
    private val onPlaybackState: (Int) -> Unit,
    private val onProgress: (Double, Double) -> Unit,
    private val onReady: () -> Unit,
    private val onError: (Int) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onStateChanged(state: Int) {
        mainHandler.post { onPlaybackState(state) }
    }

    @JavascriptInterface
    fun onProgressChanged(position: Double, duration: Double) {
        mainHandler.post { onProgress(position, duration) }
    }

    @JavascriptInterface
    fun onReady() {
        mainHandler.post(onReady)
    }

    @JavascriptInterface
    fun onError(code: Int) {
        mainHandler.post { onError(code) }
    }
}

private fun youtubePlayerHtml(videoId: String): String = """
    <!DOCTYPE html>
    <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>
          html, body { margin: 0; width: 100%; height: 100%; overflow: hidden; background: #000; }
          iframe { width: 100%; height: 100%; border: 0; }
        </style>
      </head>
      <body>
        <iframe
          id="aurora-youtube-frame"
          title="Trailer"
          src="https://www.youtube.com/embed/$videoId?autoplay=0&controls=1&enablejsapi=1&playsinline=1&rel=0&fs=0&origin=https%3A%2F%2Fwww.youtube.com"
          allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
          allowfullscreen>
        </iframe>
        <script>
          var trailerFrame = document.getElementById('aurora-youtube-frame');
          var playRequested = false;
          function sendCommand(name, args) {
            if (!trailerFrame || !trailerFrame.contentWindow) return;
            trailerFrame.contentWindow.postMessage(JSON.stringify({
              event: 'command',
              func: name,
              args: args || []
            }), '*');
          }
          function playTrailer() {
            playRequested = true;
            sendCommand('playVideo');
          }
          function pauseTrailer() { sendCommand('pauseVideo'); }
          function setMuted(muted) {
            sendCommand(muted ? 'mute' : 'unMute');
          }
          trailerFrame.onload = function() {
            $BRIDGE_NAME.onReady();
            sendCommand('addEventListener', ['onStateChange']);
            sendCommand('addEventListener', ['onError']);
            if (playRequested) sendCommand('playVideo');
          };
          window.addEventListener('message', function(event) {
            if (event.origin.indexOf('youtube.com') === -1) return;
            var data = event.data;
            if (typeof data === 'string') {
              try { data = JSON.parse(data); } catch (ignored) { return; }
            }
            if (!data) return;
            if (data.event === 'onStateChange') $BRIDGE_NAME.onStateChanged(data.info);
            if (data.event === 'onError') $BRIDGE_NAME.onError(data.info);
            if (data.event === 'infoDelivery' && data.info) {
              $BRIDGE_NAME.onProgressChanged(data.info.currentTime || 0, data.info.duration || 0);
            }
          });
        </script>
      </body>
    </html>
""".trimIndent()

private const val BRIDGE_NAME = "AuroraTrailer"
private const val YOUTUBE_BASE_URL = "https://www.youtube.com/"
private const val YOUTUBE_STATE_PLAYING = 1
