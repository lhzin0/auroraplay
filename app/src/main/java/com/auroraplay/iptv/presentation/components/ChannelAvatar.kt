package com.auroraplay.iptv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import kotlin.math.absoluteValue

/**
 * A channel's logo, with a generated fallback for the (very common) case of a
 * provider that ships no `logoUrl`. Fetching real logos off the internet by
 * name isn't reliable — there's no clean lookup and the matching is fuzzy — so
 * the fallback is a deterministic initials badge: a two-stop gradient picked
 * from the channel name, with 1–2 uppercase letters. Looks intentional, works
 * offline, stable across launches.
 */
@Composable
fun ChannelAvatar(
    name: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    Box(modifier.clip(shape)) {
        if (logoUrl.isNullOrBlank()) {
            GeneratedBadge(name)
        } else {
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(6.dp),
                loading = { GeneratedBadge(name) },
                error = { GeneratedBadge(name) },
            )
        }
    }
}

@Composable
private fun GeneratedBadge(name: String) {
    val (top, bottom) = remember(name) { badgeColors(name) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = remember(name) { initialsOf(name) },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

private val BADGE_PALETTE = listOf(
    Color(0xFF6D5DF6) to Color(0xFF4B3BD6),
    Color(0xFF2FB6C4) to Color(0xFF1D7F97),
    Color(0xFF2ED47A) to Color(0xFF1E9E63),
    Color(0xFFFFA53D) to Color(0xFFE0761F),
    Color(0xFFFF5C8A) to Color(0xFFD63B77),
    Color(0xFF5B8DEF) to Color(0xFF3A5FCC),
    Color(0xFFB06AF2) to Color(0xFF8A3FD0),
    Color(0xFFEF5350) to Color(0xFFC62828),
)

private fun badgeColors(name: String): Pair<Color, Color> =
    BADGE_PALETTE[name.trim().lowercase().hashCode().absoluteValue % BADGE_PALETTE.size]

/** 1–2 letters: first char of the first two "wordy" tokens, else the first two
 * alphanumerics of the name, else "?". Skips leading channel-number noise like
 * "01" when a real word follows. */
private fun initialsOf(raw: String): String {
    val tokens = raw.trim()
        .split(Regex("[\\s._/|-]+"))
        .filter { it.isNotBlank() }
    val wordy = tokens.filter { it.any(Char::isLetter) }
    val pick = (wordy.ifEmpty { tokens }).take(2)
    val letters = pick.mapNotNull { t -> t.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() }
    return when {
        letters.isNotEmpty() -> letters.joinToString("")
        else -> raw.filter { it.isLetterOrDigit() }.take(2).uppercase().ifBlank { "?" }
    }
}
