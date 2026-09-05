@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.auroraplay.iptv.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.auroraplay.iptv.core.theme.AuroraColors
import kotlin.math.absoluteValue

/** One entry in the hero carousel. */
data class HeroEntry(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String?,
    val backdropUrl: String?,
    val tags: List<String>,
    val isFavorite: Boolean,
)

/**
 * Hero carousel: a tall poster card per page, with the neighbouring cards
 * peeking at the edges and a colored aura bleeding out behind the active one.
 *
 * The aura is tinted by the artwork's own vibrant color (see
 * rememberArtworkAccent), so each title lights the screen with its own palette
 * instead of a fixed brand glow.
 */
@Composable
fun HeroCarousel(
    entries: List<HeroEntry>,
    onWatch: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
) {
    // The carousel enforces its own cap here rather than trusting every
    // caller to pre-trim the list — five is the most a person can swipe
    // through and still recognise as "a curated set" rather than an
    // open-ended feed.
    val heroEntries = entries.take(5)
    if (heroEntries.isEmpty()) return
    val count = heroEntries.size
    // Endless carousel: with 2+ entries the pager runs over a huge virtual
    // range and every index maps back with `% count`, so swiping past the last
    // card wraps straight to the first (and back), with no dead end.
    val loop = count > 1
    val startPage = if (loop) (Int.MAX_VALUE / 2) - (Int.MAX_VALUE / 2 % count) else 0
    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { if (loop) Int.MAX_VALUE else 1 },
    )
    fun entryAt(page: Int) = heroEntries[if (loop) page.mod(count) else 0]

    val current = entryAt(pagerState.currentPage)
    val accent by rememberArtworkAccent(
        imageUrl = current.posterUrl ?: current.backdropUrl,
        fallback = MaterialTheme.colorScheme.primary,
    )
    val animatedAccent by animateColorLike(accent)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.TopCenter) {
            // Aura fills this Box, which starts at the very top of the screen —
            // the inset lives on the pager instead, so the glow spills up
            // behind the status bar rather than stopping below it.
            HeroAura(color = animatedAccent)

            HorizontalPager(
                state = pagerState,
                // Portrait keeps the curated card treatment. Landscape uses
                // the entire width as an inline cinematic stage, so there is
                // no need to open a separate full-screen player just to see
                // the preview.
                contentPadding = if (isLandscape) PaddingValues(0.dp) else PaddingValues(horizontal = 26.dp),
                pageSpacing = if (isLandscape) 0.dp else 12.dp,
                modifier = Modifier
                    .then(if (isLandscape) Modifier else Modifier.statusBarsPadding().padding(top = 8.dp)),
            ) { page ->
                val entry = entryAt(page)

                HeroSlide(
                    entry = entry,
                    isLandscape = isLandscape,
                    pagerState = pagerState,
                    page = page,
                    // In landscape the active slide owns the full stage. In
                    // portrait neighbouring cards retain the depth effect.
                    onWatch = { onWatch(entry.id) },
                    onToggleFavorite = { onToggleFavorite(entry.id) },
                    onDetails = { onDetails(entry.id) },
                )
            }
        }

        if (loop) {
            Spacer(Modifier.height(Spacing.md))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                val activeIndex = pagerState.currentPage.mod(count)
                repeat(count) { index ->
                    val active = index == activeIndex
                    val width by animateFloatAsState(
                        targetValue = if (active) 20f else 6f,
                        animationSpec = tween(200),
                        label = "dotWidth",
                    )
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(width.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) animatedAccent
                                else AuroraColors.TextTertiary.copy(alpha = 0.35f)
                            )
                    )
                }
            }
        }
    }
}

/**
 * The glow itself. On Android 12+ this is a blurred color field; below that
 * `Modifier.blur` is a no-op (it needs RenderEffect), so a radial gradient
 * stands in — without the fallback older devices would show a hard-edged
 * rectangle of color instead of a glow.
 */
@Composable
private fun BoxScope.HeroAura(color: Color) {
    val canBlur = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

    if (canBlur) {
        // Wide, diffuse wash: carries the color out to the screen edges and
        // up past the status bar.
        Box(
            Modifier
                .matchParentSize()
                .padding(horizontal = 40.dp, vertical = 44.dp)
                // Unbounded is essential: the default (Rectangle) clips the
                // blur to the layout bounds, so the glow never escapes the box
                // and reads as almost nothing.
                .blur(80.dp, BlurredEdgeTreatment.Unbounded)
                .background(color.copy(alpha = 1f), RoundedCornerShape(56.dp))
        )
        // Tighter core hugging the card, which is what makes the edge of the
        // poster look lit rather than merely sitting on a colored field.
        Box(
            Modifier
                .matchParentSize()
                .padding(horizontal = 46.dp, vertical = 66.dp)
                .blur(34.dp, BlurredEdgeTreatment.Unbounded)
                .background(color.copy(alpha = 0.75f), RoundedCornerShape(36.dp))
        )
    } else {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.7f), Color.Transparent),
                    )
                )
        )
    }
}

@Composable
private fun HeroSlide(
    entry: HeroEntry,
    isLandscape: Boolean,
    pagerState: androidx.compose.foundation.pager.PagerState,
    page: Int,
    onWatch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDetails: () -> Unit,
) {
    val artwork = if (isLandscape) entry.backdropUrl ?: entry.posterUrl else entry.posterUrl ?: entry.backdropUrl

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                if (!isLandscape) {
                    val pageOffset = (
                        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    ).absoluteValue.coerceIn(0f, 1f)
                    val visibleFraction = 1f - pageOffset
                    val scale = interpolateToOne(0.90f, visibleFraction)
                    scaleX = scale
                    scaleY = scale
                    alpha = interpolateToOne(0.45f, visibleFraction)
                }
            }
            .aspectRatio(if (isLandscape) 16f / 7f else 0.68f)
            .clip(if (isLandscape) RoundedCornerShape(0.dp) else RoundedCornerShape(20.dp))
            .background(AuroraColors.SurfaceHigh),
    ) {
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Scrim behind the copy: the poster's lower third is unpredictable, so
        // the text needs its own contrast rather than relying on the artwork.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        0.72f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.92f),
                    )
                )
        )

        // Info affordance moved out of the button row and up to the top-right
        // corner (where Prime Video / Disney+ put it), so the row below is
        // just the two pills sharing the width evenly and never has to clip a
        // label to fit a third control. (The old muted-preview toggle that
        // used to sit bottom-left was removed — it did nothing and overlapped
        // the "Assistir" button.)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(36.dp)
                .tvFocusable(shape = CircleShape, accent = Color.White)
                .clickable(onClick = onDetails),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                Icons.Outlined.Info,
                contentDescription = "Detalhes",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(
            horizontalAlignment = if (isLandscape) Alignment.CenterHorizontally else Alignment.Start,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(if (isLandscape) Spacing.xl else Spacing.lg),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.tags.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entry.tags.filter { it.isNotBlank() }.joinToString("  •  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(Spacing.md))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (isLandscape) Modifier.widthIn(min = 320.dp, max = 540.dp) else Modifier.fillMaxWidth(),
            ) {
                AppButton(
                    text = "Assistir",
                    onClick = onWatch,
                    icon = Icons.Default.PlayArrow,
                    modifier = if (isLandscape) Modifier.widthIn(min = 150.dp, max = 260.dp) else Modifier.weight(1f),
                )
                GlassButton(
                    text = if (entry.isFavorite) "Na lista" else "Minha lista",
                    onClick = onToggleFavorite,
                    icon = if (entry.isFavorite) Icons.Default.Check else Icons.Default.Add,
                    selected = entry.isFavorite,
                    modifier = if (isLandscape) Modifier.widthIn(min = 150.dp, max = 260.dp) else Modifier.weight(1f),
                )
            }
        }
    }
}

private fun interpolateToOne(start: Float, fraction: Float): Float =
    start + (1f - start) * fraction

/** Smooths the aura color change so swiping doesn't snap between palettes. */
@Composable
private fun animateColorLike(target: Color): State<Color> =
    androidx.compose.animation.animateColorAsState(
        targetValue = target,
        animationSpec = tween(420),
        label = "auraColor",
    )
