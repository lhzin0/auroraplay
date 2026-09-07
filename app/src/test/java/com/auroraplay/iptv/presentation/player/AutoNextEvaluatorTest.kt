package com.auroraplay.iptv.presentation.player

import com.auroraplay.iptv.presentation.player.AutoNextEvaluator.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoNextEvaluatorTest {

    private val ep = 45 * 60_000L // a 45-minute episode

    @Test
    fun `mid-episode - nothing to show`() {
        assertEquals(
            Action.Clear,
            AutoNextEvaluator.evaluate(playerOnCurrentStream = true, hasPlaybackEnded = false, durationMs = ep, positionMs = 10 * 60_000L),
        )
    }

    @Test
    fun `inside the credits window - countdown with seconds left`() {
        val result = AutoNextEvaluator.evaluate(
            playerOnCurrentStream = true,
            hasPlaybackEnded = false,
            durationMs = ep,
            positionMs = ep - 30_000L,
        )
        assertEquals(Action.Countdown(30), result)
    }

    @Test
    fun `countdown never rounds down to zero`() {
        val result = AutoNextEvaluator.evaluate(true, hasPlaybackEnded = false, durationMs = ep, positionMs = ep - 200L)
        // 200ms left is inside FIRE_WINDOW_MS, so it advances rather than showing "0s".
        assertEquals(Action.Advance, result)
    }

    @Test
    fun `within the final moment - advance now`() {
        assertEquals(
            Action.Advance,
            AutoNextEvaluator.evaluate(true, hasPlaybackEnded = false, durationMs = ep, positionMs = ep - 1_000L),
        )
    }

    @Test
    fun `playback ended - advance even if position never reached the end`() {
        assertEquals(
            Action.Advance,
            AutoNextEvaluator.evaluate(true, hasPlaybackEnded = true, durationMs = ep, positionMs = ep - 5_000L),
        )
    }

    @Test
    fun `ended with unknown duration still advances`() {
        assertEquals(
            Action.Advance,
            AutoNextEvaluator.evaluate(true, hasPlaybackEnded = true, durationMs = 0L, positionMs = 0L),
        )
    }

    @Test
    fun `unknown duration and not ended - wait`() {
        assertEquals(
            Action.Clear,
            AutoNextEvaluator.evaluate(true, hasPlaybackEnded = false, durationMs = 0L, positionMs = 0L),
        )
    }

    // --- the skip-an-episode regression -------------------------------------

    @Test
    fun `player still on the previous episode - never advance, even at STATE_ENDED`() {
        // The screen already swapped to episode N+1, but the player hasn't been
        // pointed at it yet, so hasPlaybackEnded()/duration still describe
        // episode N. Acting here is exactly what skipped an episode.
        assertEquals(
            Action.Clear,
            AutoNextEvaluator.evaluate(
                playerOnCurrentStream = false,
                hasPlaybackEnded = true,
                durationMs = ep,
                positionMs = ep,
            ),
        )
        assertEquals(
            Action.Clear,
            AutoNextEvaluator.evaluate(
                playerOnCurrentStream = false,
                hasPlaybackEnded = false,
                durationMs = ep,
                positionMs = ep - 500L,
            ),
        )
    }
}
