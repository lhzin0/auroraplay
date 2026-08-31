package com.auroraplay.iptv.presentation.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Inline trailer — YouTube's own `/embed/` page in a WebView, nothing more.
 *
 * The previous build wrapped the video in hand-written HTML + the JS IFrame
 * Player API bridge. Inside an Android WebView that handshake never worked
 * reliably: `loadDataWithBaseURL` gives the page an opaque origin, so
 * YouTube's `postMessage` replies were dropped and the frame just sat black.
 *
 * The bare embed page, loaded with `loadUrl`, renders its own poster and
 * controls and plays inline on every device. `autoplay=1&mute=1` gives the
 * Netflix-style muted auto-start; YouTube's built-in controls handle
 * unmute / pause / seek. If the title has embedding disabled, YouTube's page
 * shows its own "Watch on YouTube" — and the pill below always offers the
 * full app/site too.
 *
 * `youtubeVideoId` is a TMDB-verified id, never an Xtream/stream URL.
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
    fun openOnYouTube() = context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$youtubeVideoId")),
    )

    var webView by remember(youtubeVideoId) { mutableStateOf<WebView?>(null) }
    var loaded by rememberSaveable(youtubeVideoId) { mutableStateOf(false) }
    var failed by rememberSaveable(youtubeVideoId) { mutableStateOf(false) }

    // Pause playback when this page is swiped away; nudge it back when it
    // returns. WebView.onPause/onResume also freeze the page's timers.
    LaunchedEffect(active, webView) {
        val wv = webView ?: return@LaunchedEffect
        if (active) {
            wv.onResume()
            wv.evaluateJavascript("try{document.querySelector('video').play()}catch(e){}", null)
        } else {
            wv.evaluateJavascript("try{document.querySelector('video').pause()}catch(e){}", null)
            wv.onPause()
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
            factory = { ctx ->
                WebView(ctx).also { wv ->
                    wv.setBackgroundColor(android.graphics.Color.BLACK)
                    with(wv.settings) {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = false
                        allowContentAccess = false
                    }
                    wv.webChromeClient = WebChromeClient()
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) { loaded = true }
                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true) failed = true
                        }
                    }
                    wv.loadUrl(
                        "https://www.youtube.com/embed/$youtubeVideoId" +
                            "?autoplay=1&mute=1&playsinline=1&controls=1&rel=0&modestbranding=1&iv_load_policy=3&fs=0",
                    )
                    webView = wv
                }
            },
            onRelease = { wv ->
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.destroy()
                if (webView === wv) webView = null
            },
        )

        if (!loaded && !failed) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(28.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        }

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

        // Always-present escape hatch to the full YouTube app/site — and the
        // primary affordance if the inline embed failed for this title.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color.Black.copy(alpha = if (failed) 0.85f else 0.5f))
                .clickable { openOnYouTube() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (failed) "Abrir no YouTube" else "YouTube",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
