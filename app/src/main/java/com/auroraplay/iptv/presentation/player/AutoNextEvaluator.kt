package com.auroraplay.iptv.presentation.player

/**
 * Pure decision for the "próximo episódio automático" behaviour. Xtream gives
 * no chapter/credits markers, so "the credits are rolling" is approximated as
 * "the last [CREDITS_WINDOW_MS] of the episode": a countdown shows through
 * that window and the jump fires at the end of it (or the instant playback
 * reaches STATE_ENDED).
 *
 * Kept separate from [PlayerViewModel] so the timing rules — especially the
 * guard that stops a mid-swap STATE_ENDED from the *previous* episode
 * triggering a second advance — are unit-tested directly.
 */
internal object AutoNextEvaluator {

    /** How long before the end the countdown appears. */
    const val CREDITS_WINDOW_MS = 40_000L

    /** Within this much of the end (or once ended) the jump fires now. */
    const val FIRE_WINDOW_MS = 1_200L

    sealed interface Action {
        /** Fire the jump to the next episode now. */
        data object Advance : Action

        /** Show/refresh the countdown with this many seconds left. */
        data class Countdown(val seconds: Int) : Action

        /** No auto-advance state should be showing. */
        data object Clear : Action
    }

    /**
     * @param playerOnCurrentStream whether the player is actually prepared with
     *   the episode the screen currently shows. False during the brief window
     *   where `load()` has swapped the screen's stream URL but the player is
     *   still finishing the previous episode — advancing then skips an episode.
     * @param hasPlaybackEnded the player reached STATE_ENDED.
     * @param durationMs 0 when unknown.
     * @param positionMs current playback position.
     */
    fun evaluate(
        playerOnCurrentStream: Boolean,
        hasPlaybackEnded: Boolean,
        durationMs: Long,
        positionMs: Long,
    ): Action {
        if (!playerOnCurrentStream) return Action.Clear
        if (durationMs <= 0L && !hasPlaybackEnded) return Action.Clear

        val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
        return when {
            hasPlaybackEnded || remainingMs <= FIRE_WINDOW_MS -> Action.Advance
            remainingMs <= CREDITS_WINDOW_MS ->
                Action.Countdown(((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(1))
            else -> Action.Clear
        }
    }
}
