package com.auroraplay.iptv.player

import com.auroraplay.iptv.player.PlayerTransport.ResumeAction
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTransportTest {

    @Test
    fun `playing always pauses, regardless of live`() {
        assertEquals(ResumeAction.Pause, PlayerTransport.resumeAction(isPlaying = true, isLive = false))
        assertEquals(ResumeAction.Pause, PlayerTransport.resumeAction(isPlaying = true, isLive = true))
    }

    @Test
    fun `paused VOD just plays`() {
        assertEquals(ResumeAction.Play, PlayerTransport.resumeAction(isPlaying = false, isLive = false))
    }

    @Test
    fun `paused live snaps to the live edge before playing`() {
        assertEquals(
            ResumeAction.SeekToLiveEdgeThenPlay,
            PlayerTransport.resumeAction(isPlaying = false, isLive = true),
        )
    }

    @Test
    fun `forward seek clamps to the end when duration is known`() {
        assertEquals(600_000L, PlayerTransport.seekForwardTarget(currentMs = 595_000L, deltaMs = 10_000L, durationMs = 600_000L))
        assertEquals(60_000L, PlayerTransport.seekForwardTarget(currentMs = 50_000L, deltaMs = 10_000L, durationMs = 600_000L))
    }

    @Test
    fun `forward seek is unclamped when duration is unknown or live`() {
        // C.TIME_UNSET is a large negative; 0 means "not known yet".
        assertEquals(70_000L, PlayerTransport.seekForwardTarget(60_000L, 10_000L, durationMs = 0L))
        assertEquals(70_000L, PlayerTransport.seekForwardTarget(60_000L, 10_000L, durationMs = -9_223_372_036_854_775_807L))
    }

    @Test
    fun `backward seek never goes past the start`() {
        assertEquals(0L, PlayerTransport.seekBackwardTarget(currentMs = 5_000L, deltaMs = 10_000L))
        assertEquals(40_000L, PlayerTransport.seekBackwardTarget(currentMs = 50_000L, deltaMs = 10_000L))
    }
}
