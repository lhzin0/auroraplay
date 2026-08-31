package com.auroraplay.iptv.presentation.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves the dominant color of a poster so the hero glow can be tinted by
 * the artwork itself. Prefers a vibrant swatch over the literal dominant
 * color: posters are often mostly dark, and the dominant sample of a dark
 * poster is near-black, which would produce no visible glow at all.
 *
 * Returns [fallback] until the image resolves, and keeps returning it if the
 * poster fails to load or yields nothing usable — the glow is decorative, so
 * it must never block or blank the card.
 */
@Composable
fun rememberArtworkAccent(
    imageUrl: String?,
    fallback: Color,
): State<Color> {
    val context = LocalContext.current
    return produceState(initialValue = fallback, imageUrl, fallback) {
        if (imageUrl.isNullOrBlank()) {
            value = fallback
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    // Palette needs to read pixels back, which a hardware
                    // bitmap doesn't allow.
                    .allowHardware(false)
                    .size(160)
                    .build()

                val drawable = (context.imageLoaderCompat().execute(request) as? SuccessResult)
                    ?.drawable as? BitmapDrawable ?: return@runCatching fallback

                val bitmap: Bitmap = drawable.bitmap
                val palette = Palette.from(bitmap).clearFilters().maximumColorCount(16).generate()

                val argb = palette.vibrantSwatch?.rgb
                    ?: palette.lightVibrantSwatch?.rgb
                    ?: palette.darkVibrantSwatch?.rgb
                    ?: palette.dominantSwatch?.rgb

                argb?.let { Color(it) } ?: fallback
            }.getOrDefault(fallback)
        }
    }
}

/** Single shared loader; building one per call would defeat Coil's caches. */
private fun android.content.Context.imageLoaderCompat(): ImageLoader =
    coil.Coil.imageLoader(this)
