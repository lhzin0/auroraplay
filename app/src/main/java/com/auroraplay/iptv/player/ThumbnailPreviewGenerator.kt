@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.auroraplay.iptv.player

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scrubbing thumbnail preview for VOD content, generated on-device.
 *
 * Uses [ExoFrameGrabber] (a headless ExoPlayer rendering into an ImageReader)
 * rather than `MediaMetadataRetriever`: MMR fails outright on the many Xtream
 * VOD streams whose bytes don't match the `.mp4` the URL claims — and worse,
 * blocks ~30s per bad URL. ExoPlayer opens whatever it can also *play*, and
 * each attempt is time-boxed.
 *
 * Best-effort throughout: any failure returns null and the seek bar just shows
 * the time label — playback is never affected.
 */
@Singleton
class ThumbnailPreviewGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // Include the stream URL in the key: the same 5-second position on two
    // different videos must never return the previous video's frame.
    private val cache = LinkedHashMap<CacheKey, Bitmap>()

    private data class CacheKey(val url: String, val bucket: Long)
    /** URLs proven un-extractable this session — never retried. */
    private val deadUrls = HashSet<String>()
    // One extraction at a time: a fast drag cancels and re-launches frameAt on
    // every move, and the shared grabber can only seek to one spot at once.
    private val gate = Mutex()
    private val grabber by lazy { ExoFrameGrabber(context) }

    /** Called when a VOD stream loads so the first scrub is instant. No-op for
     * URLs already known to be un-extractable. */
    fun prewarm(url: String) {
        if (url in deadUrls) return
        runCatching { grabber.prewarm(url) }
    }

    /** Returns a small frame near [positionMillis], or null if extraction isn't possible for this stream. */
    suspend fun frameAt(url: String, positionMillis: Long): Bitmap? = withContext(Dispatchers.IO) {
        if (url in deadUrls) return@withContext null
        val bucket = positionMillis / 3000L // 3s buckets balance responsiveness and cache size
        val key = CacheKey(url, bucket)
        cache[key]?.let { return@withContext it }

        gate.withLock {
            if (url in deadUrls) return@withLock null
            cache[key]?.let { return@withLock it }

            val frame = runCatching { grabber.grab(url, positionMillis) }.getOrNull()
            if (frame != null) {
                if (cache.size > 40) cache.remove(cache.keys.first())
                cache[key] = frame
            } else {
                deadUrls += url
            }
            frame
        }
    }

    fun release() {
        runCatching { grabber.release() }
        cache.clear()
    }
}
