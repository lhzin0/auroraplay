package com.auroraplay.iptv.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.auroraplay.iptv.core.theme.LocalFrostGlass
import kotlin.math.absoluteValue

/**
 * A channel's logo, with a generated fallback for the (very common) case of a
 * provider that ships no `logoUrl`. Fetching real logos off the internet by
 * name isn't reliable — there's no clean lookup and the matching is fuzzy — so
 * the fallback is a monogram badge in the style contact / workspace apps use:
 * a two-stop diagonal gradient picked deterministically from the name, a soft
 * top-left highlight for depth, a hairline edge, and a tight bold monogram —
 * the leading channel number when the name starts with one (that's how IPTV
 * channels are identified), otherwise 1–2 letters.
 */
@Composable
fun ChannelAvatar(
    name: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
) {
    Box(modifier.clip(shape)) {
        if (logoUrl.isNullOrBlank()) {
            GeneratedBadge(name, shape)
        } else {
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(6.dp),
                loading = { GeneratedBadge(name, shape) },
                error = { GeneratedBadge(name, shape) },
            )
        }
    }
}

@Composable
private fun GeneratedBadge(name: String, shape: Shape) {
    val (top, bottom) = remember(name) { badgeColors(name) }
    val label = remember(name) { monogramOf(name) }
    // With FrostGlass on, the badge is *frosted coloured glass*: the same hue,
    // pulled back to ~80% so the dark card reads through it, with a stronger
    // top sheen. Off = the solid badge. Either way the corners are rounded.
    val frosted = LocalFrostGlass.current
    val fillAlpha = if (frosted) 0.80f else 1f
    val sheenAlpha = if (frosted) 0.28f else 0.18f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Brush.linearGradient(listOf(top.copy(alpha = fillAlpha), bottom.copy(alpha = fillAlpha))))
            // Soft light from the top-left for a little depth / glass sheen.
            .background(
                Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = sheenAlpha), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = 240f,
                )
            )
            // Hairline edge so the badge separates cleanly from a dark card.
            .border(0.75.dp, Color.White.copy(alpha = if (frosted) 0.22f else 0.14f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = if (label.length >= 3) 13.sp else 16.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            color = Color.White,
        )
    }
}

// Muted, desaturated pairs — reads as "brand colour", not neon.
private val BADGE_PALETTE = listOf(
    Color(0xFF5B6CF0) to Color(0xFF3B49C4),
    Color(0xFF2FA9B8) to Color(0xFF1C7C8C),
    Color(0xFF3FB477) to Color(0xFF248A56),
    Color(0xFFE0913C) to Color(0xFFB86C22),
    Color(0xFFD65C87) to Color(0xFFA83D66),
    Color(0xFF7C6BF0) to Color(0xFF5340C4),
    Color(0xFF4E80D6) to Color(0xFF345FB0),
    Color(0xFF8E7CC3) to Color(0xFF6A5AA0),
)

private fun badgeColors(name: String): Pair<Color, Color> =
    BADGE_PALETTE[name.trim().lowercase().hashCode().absoluteValue % BADGE_PALETTE.size]

/**
 * IPTV channels lead with a number far more often than a word ("01 FM",
 * "91 Rock", "102 FM Macapa"), and that number *is* the identity — so use it
 * when present (up to 3 digits). Otherwise the first letters of the first one
 * or two real words.
 */
private fun monogramOf(raw: String): String {
    val trimmed = raw.trim()
    Regex("^(\\d{1,3})\\s*(.*)").find(trimmed)?.let { m ->
        val num = m.groupValues[1]
        // 1–2 digit number + the next word's first letter ("91 Rock" -> "91R",
        // "3 Palavrinhas" -> "3P"); a 3-digit number stands alone ("102").
        if (num.length <= 2) {
            val letter = m.groupValues[2].firstOrNull(Char::isLetter)?.uppercaseChar()
            return if (letter != null) "$num$letter" else num
        }
        return num
    }

    val tokens = trimmed.split(Regex("[\\s._/|-]+")).filter { it.isNotBlank() }
    val words = tokens.filter { it.any(Char::isLetter) }
    return when {
        words.size >= 2 -> words.take(2).mapNotNull { it.firstOrNull(Char::isLetter)?.uppercaseChar() }.joinToString("")
        words.size == 1 -> words[0].filter(Char::isLetter).take(2).uppercase()
        else -> trimmed.filter { it.isLetterOrDigit() }.take(2).uppercase().ifBlank { "•" }
    }
}
