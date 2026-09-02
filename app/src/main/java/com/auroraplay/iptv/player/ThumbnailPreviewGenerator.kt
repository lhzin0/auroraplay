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
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ThumbnailPreviewGenerator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    // On-device scrub-frame extraction is OFF.
    //
    // [ExoFrameGrabber.toBitmap] reads the decoded frame straight out of an
    // ImageReader plane's DirectByteBuffer with manual stride arithmetic. On at
    // least some Samsung (Exynos) devices that buffer's reported capacity is
    // larger than what is actually mapped, so the read walks into unmapped
    // memory — a native SIGSEGV that no Kotlin try/catch or thread
    // UncaughtExceptionHandler can contain, taking the whole process down
    // (observed on an SM-S911B while scrubbing / on the following launch).
    //
    // The seek bar already degrades to just the time label when no frame is
    // available, so the feature is disabled wholesale until it can be rebuilt
    // on a safe path (PixelCopy / GL SurfaceTexture read-back).
    private val extractionEnabled = false

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
        if (!extractionEnabled) return
        if (url in deadUrls) return
        runCatching { grabber.prewarm(url) }
    }

    /** Returns a small frame near [positionMillis], or null if extraction isn't possible for this stream. */
    suspend fun frameAt(url: String, positionMillis: Long): Bitmap? = withContext(Dispatchers.IO) {
        if (!extractionEnabled) return@withContext null
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
        // Don't touch `grabber` if it was never used — `by lazy` would spin one
        // up just to tear it down.
        if (extractionEnabled) runCatching { grabber.release() }
        cache.clear()
    }
}
