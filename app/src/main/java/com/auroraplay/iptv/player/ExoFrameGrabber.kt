package com.auroraplay.iptv.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
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
 */
@OptIn(UnstableApi::class)
class ExoFrameGrabber(private val context: Context) {

    private companion object {
        const val W = 320
        const val H = 180
        const val TIMEOUT_MS = 2_200L
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
                val p = player ?: return@post
                if (preparedUrl != url) {
                    p.setMediaItem(MediaItem.fromUri(url))
                    p.prepare()
                    preparedUrl = url
                }
            }
        }
    }

    @Synchronized
    fun grab(url: String, positionMillis: Long): Bitmap? = runCatching {
        ensureStarted()
        val h = handler ?: return null
        val reader = imageReader ?: return null

        val result = arrayOfNulls<Bitmap>(1)
        val done = CountDownLatch(1)
        val playerListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = done.countDown()
        }

        h.post {
            val p = player ?: run { done.countDown(); return@post }

            // Drop any frame still sitting in the reader from a previous grab.
            while (true) (reader.acquireLatestImage() ?: break).close()

            val imageListener = ImageReader.OnImageAvailableListener { r ->
                val img = r.acquireLatestImage()
                if (img != null) {
                    if (result[0] == null) {
                        result[0] = runCatching { img.use(::toBitmap) }.getOrNull()
                        done.countDown()
                    } else {
                        img.close()
                    }
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
        }

        done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        h.post {
            runCatching { player?.playWhenReady = false }
            runCatching { player?.removeListener(playerListener) }
            runCatching { reader.setOnImageAvailableListener(null, null) }
        }
        result[0]
    }.getOrNull()

    private fun toBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * W
        val padded = Bitmap.createBitmap(W + rowPadding / pixelStride, H, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        return if (rowPadding == 0) padded else Bitmap.createBitmap(padded, 0, 0, W, H)
    }

    private fun ensureStarted() {
        if (player != null) return
        val t = HandlerThread("aurora-thumb").apply { start() }
        val h = Handler(t.looper)
        val latch = CountDownLatch(1)
        h.post {
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
            latch.countDown()
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
                runCatching { player?.release() }
                runCatching { imageReader?.close() }
                player = null
                imageReader = null
                preparedUrl = null
                latch.countDown()
            }
            latch.await(2, TimeUnit.SECONDS)
        }
        thread?.quitSafely()
        thread = null
        handler = null
    }
}
