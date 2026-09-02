package com.auroraplay.iptv.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Frame extractor for streams the platform's `MediaMetadataRetriever` refuses
 * to open ("Unable to instantiate an extractor") but ExoPlayer plays fine — a
 * very common case with Xtream VOD whose bytes don't match the `.mp4` the URL
 * claims.
 *
 * A single headless ExoPlayer renders into a small [ImageReader]; each request
 * seeks and reads back the decoded frame from the reader's
 * `OnImageAvailableListener`. Lives on its own Looper thread; torn down with
 * [release]. Best-effort: any failure returns null.
 *
 * **Crash containment.** Everything this class does runs on the private
 * `aurora-thumb` Looper thread — building an ExoPlayer, creating an
 * [ImageReader], seeking, decoding a frame. Any of those can throw on an odd
 * device/codec, and the app installs no default uncaught-exception handler, so
 * an unhandled throw here would kill the whole process (observed: tapping the
 * player's "Cinema" button, the first thing that ever starts this grabber on a
 * live channel). Preview/cinematic frames are strictly best-effort, so every
 * worker block is wrapped and the thread also carries its own
 * uncaught-exception handler as a last resort — a failure just yields no
 * frame.
 */
@OptIn(UnstableApi::class)
class ExoFrameGrabber(private val context: Context) {

    private companion object {
        const val W = 320
        const val H = 180
        const val TIMEOUT_MS = 3_400L
        // Right after seekTo + playWhenReady the decoder flushes and pushes one
        // or two blank/black frames before the real seeked content lands. Ignore
        // everything for this long, then take the first frame that isn't black.
        const val SETTLE_MS = 380L
        // A frame passes when it's both bright enough on average AND not a flat
        // fill — black flush frames score ~0, a solid slate scores ~0 on spread.
        const val MIN_CONTENT_SCORE = 10
        const val TAG = "ExoFrameGrabber"
    }

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var player: ExoPlayer? = null
    private var imageReader: ImageReader? = null
    private var preparedUrl: String? = null

    /** Prepare the decoder ahead of time so the first scrub returns a frame
     * fast instead of paying prepare + first-buffer latency mid-drag. */
    @Synchronized
    fun prewarm(url: String) {
        runCatching {
            ensureStarted()
            handler?.post {
                runCatching {
                    val p = player ?: return@runCatching
                    if (preparedUrl != url) {
                        p.setMediaItem(MediaItem.fromUri(url))
                        p.prepare()
                        preparedUrl = url
                    }
                }.onFailure { Log.w(TAG, "prewarm failed", it) }
            }
        }
    }

    @Synchronized
    fun grab(url: String, positionMillis: Long): Bitmap? = runCatching {
        ensureStarted()
        val h = handler ?: return null
        val reader = imageReader ?: return null

        val result = arrayOfNulls<Bitmap>(1)
        // Best frame seen so far even if it's on the dark side — used only as a
        // last resort if every frame up to the timeout looks black.
        val fallback = arrayOfNulls<Bitmap>(1)
        var fallbackScore = -1
        val settled = java.util.concurrent.atomic.AtomicBoolean(false)
        val done = CountDownLatch(1)
        val playerListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = done.countDown()
        }

        h.post {
            // A throw anywhere below (ExoPlayer/MediaCodec/ImageReader on an
            // unusual stream or codec) must not escape this worker thread.
            try {
                val p = player ?: run { done.countDown(); return@post }

                // Drop any frame still sitting in the reader from a previous grab.
                while (true) (runCatching { reader.acquireLatestImage() }.getOrNull() ?: break).close()

                val imageListener = ImageReader.OnImageAvailableListener { r ->
                    val img = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@OnImageAvailableListener
                    if (result[0] != null || !settled.get()) { img.close(); return@OnImageAvailableListener }
                    val bmp = runCatching { img.use(::toBitmap) }.getOrNull() ?: return@OnImageAvailableListener
                    val score = brightnessScore(bmp)
                    if (score >= MIN_CONTENT_SCORE) {
                        result[0] = bmp
                        done.countDown()
                    } else if (score > fallbackScore) {
                        fallbackScore = score
                        fallback[0] = bmp
                    }
                }
                reader.setOnImageAvailableListener(imageListener, h)
                p.addListener(playerListener)

                if (preparedUrl != url) {
                    p.setMediaItem(MediaItem.fromUri(url))
                    p.prepare()
                    preparedUrl = url
                }
                p.seekTo(positionMillis.coerceAtLeast(0))
                // Run briefly so the decoder pushes a post-seek frame to the surface.
                p.playWhenReady = true
                // Only start accepting frames once the post-seek flush is over.
                h.postDelayed({ settled.set(true) }, SETTLE_MS)
            } catch (e: Throwable) {
                Log.w(TAG, "grab failed", e)
                done.countDown()
            }
        }

        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (result[0] == null) result[0] = fallback[0]
        h.post {
            runCatching { player?.playWhenReady = false }
            runCatching { player?.removeListener(playerListener) }
            runCatching { reader.setOnImageAvailableListener(null, null) }
        }
        result[0]
    }.getOrNull()

    /**
     * Converts only a complete RGBA frame.  Directly copying the plane buffer
     * into a Bitmap looked faster but some hardware decoders expose padded
     * rows; treating those bytes as a continuous bitmap produced the striped,
     * corrupt scrub previews seen on device.
     */
    private fun toBitmap(image: Image): Bitmap {
        require(image.format == PixelFormat.RGBA_8888) { "Unsupported preview format: ${image.format}" }
        require(image.planes.size == 1) { "Expected a single RGBA plane" }
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height
        require(pixelStride == 4) { "Unexpected RGBA pixel stride: $pixelStride" }
        require(rowStride >= (width * pixelStride)) { "Invalid RGBA row stride: $rowStride" }

        val source = plane.buffer.duplicate()
        val requiredBytes = (height - 1) * rowStride + width * pixelStride
        require(source.capacity() >= requiredBytes) { "Incomplete RGBA frame" }

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            source.position(y * rowStride)
            val rowOffset = y * width
            for (x in 0 until width) {
                val red = source.get().toInt() and 0xFF
                val green = source.get().toInt() and 0xFF
                val blue = source.get().toInt() and 0xFF
                val alpha = source.get().toInt() and 0xFF
                pixels[rowOffset + x] =
                    (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * `min(mean luma, luma spread)` over a coarse grid. A post-seek black flush
     * frame has a mean near 0; a solid-colour fill has a spread near 0; either
     * way the score stays below [MIN_CONTENT_SCORE] and the frame is rejected in
     * favour of waiting for real picture.
     */
    private fun brightnessScore(bmp: Bitmap): Int {
        val cols = 12
        val rows = 8
        var sum = 0
        var min = 255
        var max = 0
        var n = 0
        for (gy in 0 until rows) {
            val y = (bmp.height * (gy * 2 + 1)) / (rows * 2)
            for (gx in 0 until cols) {
                val x = (bmp.width * (gx * 2 + 1)) / (cols * 2)
                val c = bmp.getPixel(x, y)
                val luma = ((c ushr 16 and 0xFF) * 30 + (c ushr 8 and 0xFF) * 59 + (c and 0xFF) * 11) / 100
                sum += luma
                if (luma < min) min = luma
                if (luma > max) max = luma
                n++
            }
        }
        val mean = if (n == 0) 0 else sum / n
        return minOf(mean, max - min)
    }

    private fun ensureStarted() {
        if (player != null) return
        val t = HandlerThread("aurora-thumb").apply {
            start()
            // Last-resort guard: even if a worker block below is somehow not
            // covered, a throw on this thread logs instead of crashing the app.
            setUncaughtExceptionHandler { _, e -> Log.w(TAG, "worker thread error", e) }
        }
        val h = Handler(t.looper)
        val latch = CountDownLatch(1)
        h.post {
            try {
                val reader = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 3)
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent("AuroraPlay/1.0 (Android)")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(15_000)
                val p = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory))
                    .setSeekParameters(SeekParameters.PREVIOUS_SYNC) // fastest — nearest earlier keyframe
                    .build()
                    .apply {
                        playWhenReady = false
                        volume = 0f
                        setVideoSurface(reader.surface)
                    }
                imageReader = reader
                player = p
            } catch (e: Throwable) {
                // Preview simply stays unavailable for this session; callers
                // see a null handler/reader and return null.
                Log.w(TAG, "grabber init failed; frame preview disabled", e)
                runCatching { player?.release() }
                player = null
                imageReader = null
            } finally {
                latch.countDown()
            }
        }
        latch.await(3, TimeUnit.SECONDS)
        thread = t
        handler = h
    }

    @Synchronized
    fun release() {
        val h = handler
        if (h != null) {
            val latch = CountDownLatch(1)
            h.post {
                try {
                    runCatching { player?.release() }
                    runCatching { imageReader?.close() }
                } finally {
                    player = null
                    imageReader = null
                    preparedUrl = null
                    latch.countDown()
                }
            }
            latch.await(2, TimeUnit.SECONDS)
        }
        thread?.quitSafely()
        thread = null
        handler = null
    }
}
