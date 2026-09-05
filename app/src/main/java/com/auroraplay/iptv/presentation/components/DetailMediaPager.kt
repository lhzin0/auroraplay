package com.auroraplay.iptv.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Single media stage used by movie/series details.
 * Page 0 is the artwork and page 1 is the YouTube trailer. When a trailer is
 * available, it remains an explicitly selected second page. Artwork always
 * opens first, so no video/trailer is shown until the user swipes to it.
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
            initialPage = 0,
            pageCount = { pageCount },
        )
        val pagerScope = rememberCoroutineScope()

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
                        // Just the stamped title logo on the hero; the meta
                        // line ("2026 • Ação • …") stays in the info block
                        // right below, so it isn't shown twice.
                        subtitle = null,
                        showTextOverlay = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> TrailerPreview(
                        title = title,
                        youtubeVideoId = checkNotNull(trailerYoutubeId),
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
                        // A swipe on the pager itself has no D-pad equivalent
                        // on a TV remote, so these indicator arrows double as
                        // real page-turn targets — harmless as an extra tap
                        // target on touch too.
                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (pagerState.currentPage > 0) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Página anterior",
                                    tint = Color.White.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .tvFocusable(shape = CircleShape, accent = Color.White)
                                        .clickable {
                                            pagerScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                        },
                                )
                            }
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
                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (pagerState.currentPage < pageCount - 1) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Próxima página",
                                    tint = Color.White.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .tvFocusable(shape = CircleShape, accent = Color.White)
                                        .clickable {
                                            pagerScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
