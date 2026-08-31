package com.auroraplay.iptv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun DetailBanner(
    title: String,
    backdropUrl: String?,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    showTextOverlay: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 8.7f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
    ) {
        backdropUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showTextOverlay) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.04f),
                        0.48f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.82f),
                    )
                )
            )
        }
    }
}
