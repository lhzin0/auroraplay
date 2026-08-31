package com.auroraplay.iptv.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Single media stage used by movie/series details.
 * Page 0 is the artwork and page 1 is the YouTube trailer. When a trailer is
 * available, the player opens first, matching the streaming-detail layout.
 * The stage is intentionally inset from the system status/navigation areas so
 * neither media card nor controls can be hidden behind Android system bars.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailMediaPager(
    title: String,
    backdropUrl: String?,
    subtitle: String?,
    trailerYoutubeId: String?,
    modifier: Modifier = Modifier,
) {
    val hasTrailer = trailerYoutubeId != null
    val pageCount = if (hasTrailer) 2 else 1

    key(trailerYoutubeId) {
        val pagerState = rememberPagerState(
            initialPage = if (hasTrailer) 1 else 0,
            pageCount = { pageCount },
        )

        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                beyondViewportPageCount = 0,
                pageSpacing = 0.dp,
            ) { page ->
                when (page) {
                    0 -> DetailBanner(
                        title = title,
                        backdropUrl = backdropUrl,
                        subtitle = subtitle,
                        showTextOverlay = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> TrailerPreview(
                        title = title,
                        youtubeVideoId = checkNotNull(trailerYoutubeId),
                        active = pagerState.currentPage == 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (pageCount > 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (pagerState.currentPage > 0) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color.White.copy(alpha = 0.55f))
                        }
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .background(Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (pagerState.currentPage == 0) "●  ○" else "○  ●",
                                color = Color.White.copy(alpha = 0.82f),
                            )
                        }
                        if (pagerState.currentPage < pageCount - 1) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.55f))
                        }
                    }
                }
            }
        }
    }
}
