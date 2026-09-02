package com.auroraplay.iptv.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.TreeMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Timeline scrub-preview source.
 *
 * Design goals (why this replaces the old per-move `ExoFrameGrabber`):
 *
 *  - **One** persistent, headless [ExoPlayer] per video, `prepare()`d once —
 *    never one-per-scrub, never `prepare()` per thumbnail.
 *  - The visible scrub position and the time label are owned by the UI and
 *    never wait on this class.
 *  - During a drag the caller just pushes the *latest* target position
 *    ([requestAt]); older targets are discarded, there is no queue and no
 *    Mutex serialising requests.
 *  - Frames land in a position-keyed cache; [nearest] returns the closest one
 *    instantly, so the preview always shows *something* and only sharpens as
 *    the decoder catches up (Netflix / YouTube feel).
 *  - Background pre-generation fills a coarse grid across the whole video when
 *    idle, and the region around the finger is always prioritised.
 *  - Frames are read via [Bitmap.wrapHardwareBuffer] (API 29+) — no manual
 *    `ByteBuffer` stride math, which is what crashed natively on Samsung.
 *    Below API 29 the preview is simply unavailable (the bar shows the time).
 */
@Singleton
@OptIn(UnstableApi::class)
class ScrubPreviewEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "ScrubPreview"
        const val W = 256
        const val H = 144
        const val MAX_CACHE = 64
        /** A cached frame within this distance of a target counts as "done". */
        const val CACHE_TOLERANCE_MS = 1_500L
        /** Remote-MP4 range seeks can be slow; give them room. */
        const val GRAB_TIMEOUT_MS = 2_600L
        /** Grace after the seek settles before the first frame is accepted. */
        const val SETTLE_MS = 120L
        const val MIN_CONTENT_SCORE = 8
    }

    /** True where hardware-buffer frame reads are available. */
    val available: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Bumped whenever a new frame enters the cache — collectors re-query
     * [nearest] for the finger's current position. */
    private val _cacheVersion = MutableStateFlow(0)
    val cacheVersion: StateFlow<Int> = _cacheVersion

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var player: ExoPlayer? = null
    private var reader: ImageReader? = null

    private var currentUrl: String? = null
    @Volatile private var durationMs: Long = 0L
    @Volatile private var priorityTarget: Long? = null
    @Volatile private var lastRequestedPos: Long = 0L
    @Volatile private var idle: Boolean = true

    private val cache = TreeMap<Long, Bitmap>()
    private val cacheLock = Any()
    private val grid = ArrayDeque<Long>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: Job? = null

    // If the preview decoder never manages to render a frame for this video
    // (some IPTV streams / codecs just won't), stop trying — the scrub bar
    // falls back to the time label and we stop burning a decoder + a second
    // network connection for nothing.
    @Volatile private var deadForThisVideo = false
    private var consecutiveMisses = 0
    private var everProducedFrame = false

    // --- public API -------------------------------------------------------

    /** Prepare the preview decoder for [url] (once). Safe to call repeatedly. */
    fun open(url: String) {
        if (!available || url.isBlank()) return
        if (url == currentUrl && player != null) { idle = false; ensureWorker(); return }
        ensureStarted()
        val h = handler ?: return
        currentUrl = url
        durationMs = 0L
        priorityTarget = null
        idle = false
        deadForThisVideo = false
        consecutiveMisses = 0
        everProducedFrame = false
        clearCache()
        h.post {
            runCatching {
                val p = player ?: return@post
                p.setMediaItem(MediaItem.fromUri(url))
                p.prepare()
                Log.i(TAG, "open: prepared $url")
            }.onFailure { Log.w(TAG, "open failed", it) }
        }
        ensureWorker()
    }

    /** Push the latest desired preview position. Only the newest matters. */
    fun requestAt(positionMs: Long) {
        if (!available) return
        lastRequestedPos = positionMs.coerceAtLeast(0L)
        priorityTarget = lastRequestedPos
        idle = false
        ensureWorker()
    }

    /** Finger lifted: stop targeted work (grid pre-gen also parks). */
    fun setIdle() {
        priorityTarget = null
        idle = true
    }

    /** Closest cached frame to [positionMs], or null if the cache is empty. */
    fun nearest(positionMs: Long): Bitmap? = synchronized(cacheLock) {
        if (cache.isEmpty()) return null
        val lo = cache.floorEntry(positionMs)
        val hi = cache.ceilingEntry(positionMs)
        when {
            lo == null -> hi?.value
            hi == null -> lo.value
            positionMs - lo.key <= hi.key - positionMs -> lo.value
            else -> hi.value
        }
    }

    /** Tear down for the current video. */
    fun close() {
        worker?.cancel()
        worker = null
        val h = handler
        if (h != null) {
            h.post {
                runCatching { player?.stop() }
                runCatching { player?.clearMediaItems() }
            }
        }
        currentUrl = null
        priorityTarget = null
        idle = true
        clearCache()
    }

    /** Full shutdown (app / player screen leaving). */
    fun release() {
        worker?.cancel(); worker = null
        val h = handler
        if (h != null) {
            h.post {
                runCatching { player?.release() }
                runCatching { reader?.close() }
                player = null
                reader = null
            }
        }
        thread?.quitSafely()
        thread = null
        handler = null
        currentUrl = null
        clearCache()
    }

    // --- worker ----------------------------------------------------------

    private fun ensureWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (isActive) {
                if (deadForThisVideo) { delay(300); continue }
                val target = nextTarget()
                if (target == null) { delay(90); continue }
                val bmp = grabFrame(target)
                if (bmp != null) {
                    everProducedFrame = true
                    consecutiveMisses = 0
                    put(target, bmp)
                    _cacheVersion.value = _cacheVersion.value + 1
                    Log.i(TAG, "frame @${target}ms  cache=${synchronized(cacheLock) { cache.size }}")
                } else {
                    consecutiveMisses++
                    Log.i(TAG, "no frame @${target}ms (dur=$durationMs, miss=$consecutiveMisses)")
                    if (!everProducedFrame && consecutiveMisses >= 6) {
                        Log.w(TAG, "preview decoder produced nothing for this video — giving up")
                        deadForThisVideo = true
                    }
                    delay(120)
                }
            }
        }
    }

    /** The next position worth decoding: the finger first, then the nearest
     * still-missing grid slot; nothing while idle with a covered finger. */
    private fun nextTarget(): Long? {
        priorityTarget?.let { p -> if (!hasNear(p)) return p }
        if (idle) return null
        // Populate the grid lazily once the decoder knows the duration.
        if (grid.isEmpty() && durationMs > 0L) buildGrid()
        return grid
            .filter { !hasNear(it) }
            .minByOrNull { abs(it - lastRequestedPos) }
    }

    private fun buildGrid() {
        val d = durationMs
        if (d <= 0L) return
        val count = (d / 8_000L).coerceIn(12L, 64L).toInt()
        grid.clear()
        for (i in 1..count) grid.add(d * i / (count + 1))
    }

    private fun hasNear(positionMs: Long): Boolean = synchronized(cacheLock) {
        val lo = cache.floorEntry(positionMs)
        val hi = cache.ceilingEntry(positionMs)
        (lo != null && positionMs - lo.key <= CACHE_TOLERANCE_MS) ||
            (hi != null && hi.key - positionMs <= CACHE_TOLERANCE_MS)
    }

    private fun put(positionMs: Long, bmp: Bitmap) = synchronized(cacheLock) {
        cache[positionMs] = bmp
        while (cache.size > MAX_CACHE) {
            val evict = cache.keys.maxByOrNull { abs(it - lastRequestedPos) } ?: break
            cache.remove(evict)
        }
    }

    private fun clearCache() = synchronized(cacheLock) { cache.clear() }

    // --- frame grab (coroutine drives, Looper does the work) -------------

    private suspend fun grabFrame(positionMs: Long): Bitmap? {
        val h = handler ?: return null
        val p = player ?: return null
        val r = reader ?: return null
        // Learn duration as soon as it's known so the grid can populate.
        if (durationMs <= 0L) {
            val d = runCatching { awaitOnHandler(h) { player?.duration ?: 0L } }.getOrDefault(0L)
            if (d > 0L) durationMs = d
        }

        val deferred = CompletableDeferred<Bitmap?>()
        // Backstop: if only black/flush frames ever arrive, take the last one
        // rather than time out with nothing.
        var fallback: Bitmap? = null
        h.post {
            try {
                // Drop any leftover frame from a previous grab.
                while (true) (runCatching { r.acquireLatestImage() }.getOrNull() ?: break).close()
                r.setOnImageAvailableListener({ rr ->
                    val img = runCatching { rr.acquireLatestImage() }.getOrNull()
                        ?: return@setOnImageAvailableListener
                    if (deferred.isCompleted) { img.close(); return@setOnImageAvailableListener }
                    val bmp = runCatching { imageToBitmap(img) }.getOrNull()
                    img.close()
                    if (bmp == null) return@setOnImageAvailableListener
                    if (brightnessScore(bmp) >= MIN_CONTENT_SCORE) {
                        if (!deferred.isCompleted) deferred.complete(bmp)
                        runCatching { p.playWhenReady = false }
                    } else {
                        fallback = bmp
                    }
                }, h)
                p.setVideoSurface(r.surface)                        // re-assert every grab
                p.setSeekParameters(SeekParameters.PREVIOUS_SYNC)   // nearest earlier keyframe: fastest, always resolvable
                if (p.playbackState == Player.STATE_IDLE) p.prepare()
                p.seekTo(positionMs.coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE))
                p.playWhenReady = true
            } catch (t: Throwable) {
                Log.w(TAG, "grab post failed", t)
                deferred.complete(null)
            }
        }

        val result = withTimeoutOrNull(GRAB_TIMEOUT_MS) { deferred.await() } ?: fallback
        h.post {
            runCatching { p.playWhenReady = false }
            runCatching { r.setOnImageAvailableListener(null, null) }
        }
        return result
    }

    /**
     * Frame read WITHOUT the hand-rolled per-byte buffer walk that crashed
     * natively on Exynos. Preferred path: wrap the GPU buffer directly. If the
     * buffer isn't GPU-sampleable, fall back to a single bounds-checked bulk
     * `copyPixelsFromBuffer` (reads exactly W*H*4 from offset 0 — never the
     * high, unmapped offsets the old `position(y*rowStride)` walk hit).
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val hb: HardwareBuffer? = image.hardwareBuffer
                if (hb != null) {
                    hb.use { buffer ->
                        Bitmap.wrapHardwareBuffer(buffer, null)?.let { hw ->
                            return hw.copy(Bitmap.Config.ARGB_8888, false)
                        }
                    }
                }
            }
        }
        return runCatching {
            val plane = image.planes.firstOrNull() ?: return null
            val buf = plane.buffer.duplicate().apply { rewind() }
            val out = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            if (buf.remaining() < out.byteCount) return null
            out.copyPixelsFromBuffer(buf)
            out
        }.getOrNull()
    }

    /** `min(mean luma, luma spread)` — rejects the black post-seek flush and
     * flat-colour fills without discarding a genuinely dark scene. */
    private fun brightnessScore(bmp: Bitmap): Int {
        val cols = 10
        val rows = 6
        var sum = 0
        var min = 255
        var max = 0
        for (gy in 0 until rows) {
            val y = (bmp.height * (gy * 2 + 1)) / (rows * 2)
            for (gx in 0 until cols) {
                val x = (bmp.width * (gx * 2 + 1)) / (cols * 2)
                val c = bmp.getPixel(x, y)
                val luma = ((c ushr 16 and 0xFF) * 30 + (c ushr 8 and 0xFF) * 59 + (c and 0xFF) * 11) / 100
                sum += luma
                if (luma < min) min = luma
                if (luma > max) max = luma
            }
        }
        val mean = sum / (cols * rows)
        return minOf(mean, max - min)
    }

    // --- lifecycle -----------------------------------------------------

    @Synchronized
    private fun ensureStarted() {
        if (player != null && reader != null) return
        val t = HandlerThread("aurora-scrub").apply {
            start()
            setUncaughtExceptionHandler { _, e -> Log.w(TAG, "worker thread error", e) }
        }
        val h = Handler(t.looper)
        val done = java.util.concurrent.CountDownLatch(1)
        h.post {
            try {
                // No usage flag: this is the config the decoder actually
                // renders RGBA frames into. The read path tries
                // wrapHardwareBuffer first and falls back to copyPixelsFromBuffer.
                val r = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 4)
                val http = DefaultHttpDataSource.Factory()
                    .setUserAgent("AuroraPlay/1.0 (Android)")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(15_000)
                val p = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
                    .setSeekParameters(SeekParameters.CLOSEST_SYNC)
                    .build()
                    .apply {
                        playWhenReady = false
                        volume = 0f
                        setVideoSurface(r.surface)
                        addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(state: Int) {
                                if (state == Player.STATE_READY && durationMs <= 0L) {
                                    val d = runCatching { duration }.getOrDefault(0L)
                                    if (d > 0L) durationMs = d
                                }
                            }
                            override fun onPlayerError(error: PlaybackException) {
                                Log.w(TAG, "preview player error", error)
                            }
                        })
                    }
                reader = r
                player = p
            } catch (t2: Throwable) {
                Log.w(TAG, "scrub engine init failed; preview disabled this session", t2)
                runCatching { player?.release() }
                player = null
                reader = null
            } finally {
                done.countDown()
            }
        }
        done.await(3, java.util.concurrent.TimeUnit.SECONDS)
        thread = t
        handler = h
    }

    private suspend fun <T> awaitOnHandler(h: Handler, block: () -> T): T {
        val d = CompletableDeferred<T>()
        h.post { runCatching { d.complete(block()) }.onFailure { d.completeExceptionally(it) } }
        return d.await()
    }
}
