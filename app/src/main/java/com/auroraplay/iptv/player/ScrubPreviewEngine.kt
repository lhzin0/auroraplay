package com.auroraplay.iptv.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.TreeMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Timeline scrub-preview source.
 *
 *  - **One** persistent, headless [ExoPlayer] per video, `prepare()`d once —
 *    never one-per-scrub, never `prepare()` per thumbnail.
 *  - The visible scrub position / time label never wait on this class.
 *  - During a drag the caller pushes only the *latest* target ([requestAt]);
 *    older targets are dropped, no queue, no Mutex.
 *  - Frames land in a position-keyed cache; [nearest] returns the closest one
 *    instantly, so the card always shows *something* and only sharpens as the
 *    decoder catches up. Background pre-generation fills a coarse grid.
 *
 * **How frames are read.** The decoder renders into a [SurfaceTexture]; a tiny
 * off-screen EGL/GLES2 context samples that external texture into a WxH FBO and
 * `glReadPixels` copies it into a buffer *we* allocated. This is the same
 * GPU-side read-back `TextureView.getBitmap` uses, and the only path that works
 * on hardware decoders whose `ImageReader` planes are GPU-only / null-backed
 * (Exynos): reading those by hand SIGSEGVs or JNI-aborts.
 */
@Singleton
@OptIn(UnstableApi::class)
class ScrubPreviewEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "ScrubPreview"
        const val W = 320
        const val H = 180
        const val MAX_CACHE = 64
        const val CACHE_TOLERANCE_MS = 1_500L
        const val GRAB_TIMEOUT_MS = 3_000L
        const val MIN_CONTENT_SCORE = 8
    }

    /** Always attempt it; a per-video watchdog disables it if nothing renders. */
    val available: Boolean = true

    private val _cacheVersion = MutableStateFlow(0)
    val cacheVersion: StateFlow<Int> = _cacheVersion

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var player: ExoPlayer? = null
    private var gl: GlReader? = null

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

    @Volatile private var deadForThisVideo = false
    private var consecutiveMisses = 0
    private var everProducedFrame = false

    /** Completed by the SurfaceTexture's frame-available callback. */
    @Volatile private var frameSignal: CompletableDeferred<Unit>? = null

    // --- public API -----------------------------------------------------

    fun open(url: String) {
        if (url.isBlank()) return
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
            }.onFailure { Log.w(TAG, "open failed") }
        }
        ensureWorker()
    }

    fun requestAt(positionMs: Long) {
        lastRequestedPos = positionMs.coerceAtLeast(0L)
        priorityTarget = lastRequestedPos
        idle = false
        ensureWorker()
    }

    fun setIdle() {
        priorityTarget = null
        idle = true
    }

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

    fun close() {
        worker?.cancel(); worker = null
        handler?.post {
            runCatching { player?.stop() }
            runCatching { player?.clearMediaItems() }
        }
        currentUrl = null
        priorityTarget = null
        idle = true
        clearCache()
    }

    fun release() {
        worker?.cancel(); worker = null
        handler?.post {
            runCatching { player?.release() }
            runCatching { gl?.release() }
            player = null
            gl = null
        }
        thread?.quitSafely()
        thread = null
        handler = null
        currentUrl = null
        clearCache()
    }

    // --- worker -------------------------------------------------------

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
                } else {
                    consecutiveMisses++
                    if (!everProducedFrame && consecutiveMisses >= 6) {
                        Log.w(TAG, "preview decoder produced nothing for this video — disabling")
                        deadForThisVideo = true
                    }
                    delay(120)
                }
            }
        }
    }

    private fun nextTarget(): Long? {
        priorityTarget?.let { p -> if (!hasNear(p)) return p }
        if (idle) return null
        if (grid.isEmpty() && durationMs > 0L) buildGrid()
        return grid.filter { !hasNear(it) }.minByOrNull { abs(it - lastRequestedPos) }
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

    // --- frame grab -------------------------------------------------

    private suspend fun grabFrame(positionMs: Long): Bitmap? {
        val h = handler ?: return null
        val p = player ?: return null
        val g = gl ?: return null

        if (durationMs <= 0L) {
            val d = runCatching { awaitOnHandler(h) { player?.duration ?: 0L } }.getOrDefault(0L)
            if (d > 0L) durationMs = d
        }

        val signal = CompletableDeferred<Unit>()
        frameSignal = signal
        runCatching {
            awaitOnHandler(h) {
                g.drainPendingFrames()
                p.setVideoSurface(g.surface)
                p.setSeekParameters(SeekParameters.PREVIOUS_SYNC)
                if (p.playbackState == Player.STATE_IDLE) p.prepare()
                p.playWhenReady = false
                p.seekTo(positionMs.coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE))
            }
        }
        // A paused seek with a surface attached should render the target frame.
        // If it doesn't within ~700ms, nudge playback briefly to force one out.
        h.postDelayed({ if (frameSignal != null) runCatching { p.play() } }, 700L)
        h.postDelayed({ runCatching { p.pause() } }, 1_150L)

        var best: Bitmap? = null
        withTimeoutOrNull(GRAB_TIMEOUT_MS) {
            // Take up to 4 frames; the first after a seek is often the flush
            // frame, so keep going for a bit and return the last good one.
            for (i in 0 until 4) {
                (frameSignal ?: break).await()
                frameSignal = CompletableDeferred()
                val bmp = runCatching { awaitOnHandler(h) { g.renderCurrentFrame() } }.getOrNull() ?: continue
                if (brightnessScore(bmp) >= MIN_CONTENT_SCORE) {
                    best = bmp
                    if (i >= 1) break   // a non-first non-black frame is good enough
                }
            }
        }

        frameSignal = null
        h.post { runCatching { p.playWhenReady = false } }
        return best
    }

    /** `min(mean luma, luma spread)` — rejects the black post-seek flush and
     * flat-colour fills without discarding a genuinely dark scene. */
    private fun brightnessScore(bmp: Bitmap): Int {
        var sum = 0; var min = 255; var max = 0
        for (gy in 0 until 6) {
            val y = (bmp.height * (gy * 2 + 1)) / 12
            for (gx in 0 until 10) {
                val x = (bmp.width * (gx * 2 + 1)) / 20
                val c = bmp.getPixel(x, y)
                val luma = ((c ushr 16 and 0xFF) * 30 + (c ushr 8 and 0xFF) * 59 + (c and 0xFF) * 11) / 100
                sum += luma
                if (luma < min) min = luma
                if (luma > max) max = luma
            }
        }
        return minOf(sum / 60, max - min)
    }

    // --- lifecycle ------------------------------------------------

    @Synchronized
    private fun ensureStarted() {
        if (player != null && gl != null) return
        val t = HandlerThread("aurora-scrub").apply {
            start()
            setUncaughtExceptionHandler { _, e -> Log.w(TAG, "worker thread error") }
        }
        val h = Handler(t.looper)
        val done = java.util.concurrent.CountDownLatch(1)
        h.post {
            try {
                val reader = GlReader(W, H)
                reader.setup()
                reader.surfaceTexture.setOnFrameAvailableListener({ frameSignal?.complete(Unit) }, h)

                val http = DefaultHttpDataSource.Factory()
                    .setUserAgent("AuroraPlay/1.0 (Android)")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(15_000)
                val p = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
                    .setSeekParameters(SeekParameters.PREVIOUS_SYNC)
                    .build()
                    .apply {
                        playWhenReady = false
                        volume = 0f
                        setVideoSurface(reader.surface)
                        addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(state: Int) {
                                if (state == Player.STATE_READY && durationMs <= 0L) {
                                    val d = runCatching { duration }.getOrDefault(0L)
                                    if (d > 0L) durationMs = d
                                }
                            }
                            override fun onPlayerError(error: PlaybackException) {
                                Log.w(TAG, "preview player error")
                            }
                        })
                    }
                gl = reader
                player = p
            } catch (e: Throwable) {
                Log.w(TAG, "scrub engine init failed; preview disabled")
                runCatching { player?.release() }
                runCatching { gl?.release() }
                player = null
                gl = null
            } finally {
                done.countDown()
            }
        }
        done.await(4, java.util.concurrent.TimeUnit.SECONDS)
        thread = t
        handler = h
    }

    private suspend fun <T> awaitOnHandler(h: Handler, block: () -> T): T {
        val d = CompletableDeferred<T>()
        h.post { runCatching { d.complete(block()) }.onFailure { d.completeExceptionally(it) } }
        return d.await()
    }

    // -----------------------------------------------------------------
    //  Off-screen EGL/GLES2 read-back. All methods run on the engine's
    //  HandlerThread, where the EGL context is current.
    // -----------------------------------------------------------------
    private class GlReader(private val w: Int, private val h: Int) {
        lateinit var surfaceTexture: SurfaceTexture; private set
        lateinit var surface: Surface; private set

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var oesTex = 0
        private var fbo = 0
        private var fboTex = 0
        private var program = 0
        private var aPos = 0
        private var aTex = 0
        private var uStMatrix = 0
        private val stMatrix = FloatArray(16)
        private val pixels: ByteBuffer =
            ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())

        // Full-screen triangle strip. Tex-Y is flipped so glReadPixels
        // (bottom-up) yields a top-down bitmap.
        private val quadPos: FloatBuffer = floatBuf(
            -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f,
        )
        private val quadTex: FloatBuffer = floatBuf(
            0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f,
        )

        fun setup() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val ver = IntArray(2)
            check(EGL14.eglInitialize(eglDisplay, ver, 0, ver, 1)) { "eglInitialize" }
            val cfgAttrs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE,
            )
            val cfgs = arrayOfNulls<EGLConfig>(1)
            val n = IntArray(1)
            check(EGL14.eglChooseConfig(eglDisplay, cfgAttrs, 0, cfgs, 0, 1, n, 0) && n[0] > 0) { "eglChooseConfig" }
            eglContext = EGL14.eglCreateContext(
                eglDisplay, cfgs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            eglSurface = EGL14.eglCreatePbufferSurface(
                eglDisplay, cfgs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
            )
            check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) { "eglMakeCurrent" }

            val t = IntArray(1)
            GLES20.glGenTextures(1, t, 0)
            oesTex = t[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            surfaceTexture = SurfaceTexture(oesTex).apply { setDefaultBufferSize(1280, 720) }
            surface = Surface(surfaceTexture)

            // FBO + colour texture we read back from.
            GLES20.glGenTextures(1, t, 0)
            fboTex = t[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glGenFramebuffers(1, t, 0)
            fbo = t[0]
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex, 0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

            program = buildProgram()
            aPos = GLES20.glGetAttribLocation(program, "aPos")
            aTex = GLES20.glGetAttribLocation(program, "aTex")
            uStMatrix = GLES20.glGetUniformLocation(program, "uSTMatrix")
        }

        /** Consume + discard whatever is already queued on the SurfaceTexture. */
        fun drainPendingFrames() {
            repeat(4) { runCatching { surfaceTexture.updateTexImage() }.getOrNull() ?: return }
        }

        /** Pull the newest frame off the SurfaceTexture, draw it into the FBO,
         *  read it back into an ARGB_8888 bitmap. */
        fun renderCurrentFrame(): Bitmap? = runCatching {
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(stMatrix)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            GLES20.glViewport(0, 0, w, h)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
            GLES20.glUniformMatrix4fv(uStMatrix, 1, false, stMatrix, 0)

            quadPos.position(0)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quadPos)
            GLES20.glEnableVertexAttribArray(aPos)
            quadTex.position(0)
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, quadTex)
            GLES20.glEnableVertexAttribArray(aTex)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(aPos)
            GLES20.glDisableVertexAttribArray(aTex)
            GLES20.glFinish()

            pixels.rewind()
            GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            pixels.rewind()
            bmp.copyPixelsFromBuffer(pixels)
            bmp
        }.getOrNull()

        fun release() {
            runCatching { surface.release() }
            runCatching { surfaceTexture.release() }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }

        private fun buildProgram(): Int {
            val vs = compile(
                GLES20.GL_VERTEX_SHADER,
                """
                attribute vec2 aPos;
                attribute vec2 aTex;
                uniform mat4 uSTMatrix;
                varying vec2 vTex;
                void main() {
                    gl_Position = vec4(aPos, 0.0, 1.0);
                    vTex = (uSTMatrix * vec4(aTex, 0.0, 1.0)).xy;
                }
                """.trimIndent(),
            )
            val fs = compile(
                GLES20.GL_FRAGMENT_SHADER,
                """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTex;
                uniform samplerExternalOES sTex;
                void main() { gl_FragColor = texture2D(sTex, vTex); }
                """.trimIndent(),
            )
            val prog = GLES20.glCreateProgram()
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
            val status = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { "link: " + GLES20.glGetProgramInfoLog(prog) }
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
            return prog
        }

        private fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val status = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { "compile: " + GLES20.glGetShaderInfoLog(s) }
            return s
        }

        private fun floatBuf(vararg values: Float): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply { put(values); position(0) }
    }
}
