package com.auroraplay.iptv.presentation.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Reliable YouTube trailer preview.
 *
 * The YouTube player blocks/restricts embedded playback in Android WebView on
 * some devices, which left this area black or white even when the verified
 * trailer id was correct. A first-party YouTube thumbnail keeps the detail
 * page fast and visual; tapping it opens the exact verified trailer in the
 * installed YouTube app (or the browser as a fallback), where playback is
 * supported by YouTube itself.
 */
@Composable
fun TrailerPreview(
    title: String,
    youtubeVideoId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111014))
            .tvFocusable(shape = RoundedCornerShape(16.dp), accent = Color.White)
            .clickable { context.openYouTubeTrailer(youtubeVideoId) },
    ) {
        AsyncImage(
            model = "https://i.ytimg.com/vi/$youtubeVideoId/hqdefault.jpg",
            contentDescription = "Trailer de $title",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.78f),
                    )
                )
        )

        Text(
            "TRAILER OFICIAL",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.60f), RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp),
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .size(62.dp)
                .background(Color.White, CircleShape),
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Abrir trailer no YouTube",
                tint = Color.Black,
                modifier = Modifier.size(34.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Assistir no YouTube",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    "Abre o trailer oficial completo",
                    color = Color.White.copy(alpha = 0.76f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun Context.openYouTubeTrailer(youtubeVideoId: String) {
    val trailerUri = Uri.parse("https://www.youtube.com/watch?v=$youtubeVideoId&autoplay=1")
    startActivity(Intent(Intent.ACTION_VIEW, trailerUri))
}
