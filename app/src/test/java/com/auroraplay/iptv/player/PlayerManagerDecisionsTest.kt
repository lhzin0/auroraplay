package com.auroraplay.iptv.player

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [shouldReuseBufferedStream] — the rule that keeps promoting the Live
 * preview to full screen (and a stale play() from a disposed preview) from
 * re-preparing the shared ExoPlayer and dropping its buffer.
 */
class PlayerManagerDecisionsTest {

    private val url = "http://host/live/1.m3u8"

    @Test
    fun `same url, still playing - reuse the buffered stream`() {
        assertTrue(
            shouldReuseBufferedStream(
                lastRequestedUrl = url,
                requestedUrl = url,
                hasMediaItem = true,
                playbackState = Player.STATE_READY,
            ),
        )
    }

    @Test
    fun `same url but still buffering also counts as reuse`() {
        assertTrue(
            shouldReuseBufferedStream(url, url, hasMediaItem = true, playbackState = Player.STATE_BUFFERING),
        )
    }

    @Test
    fun `different url - do not reuse`() {
        assertFalse(
            shouldReuseBufferedStream(
                lastRequestedUrl = "http://host/live/2.m3u8",
                requestedUrl = url,
                hasMediaItem = true,
                playbackState = Player.STATE_READY,
            ),
        )
    }

    @Test
    fun `first ever play - nothing requested yet`() {
        assertFalse(
            shouldReuseBufferedStream(null, url, hasMediaItem = false, playbackState = Player.STATE_IDLE),
        )
    }

    @Test
    fun `same url but the player was stopped - re-prepare`() {
        assertFalse(
            shouldReuseBufferedStream(url, url, hasMediaItem = false, playbackState = Player.STATE_IDLE),
        )
    }

    @Test
    fun `same url but playback ended - re-prepare`() {
        assertFalse(
            shouldReuseBufferedStream(url, url, hasMediaItem = true, playbackState = Player.STATE_ENDED),
        )
    }
}
