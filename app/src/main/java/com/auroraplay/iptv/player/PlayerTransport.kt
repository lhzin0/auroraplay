package com.auroraplay.iptv.player

/**
 * Pure transport decisions for [PlayerManager], split out so the parts that
 * are easy to get subtly wrong — the live-edge resume and the seek clamps —
 * are unit-tested without a real ExoPlayer.
 */
internal object PlayerTransport {

    enum class ResumeAction {
        /** Currently playing -> pause. */
        Pause,

        /** Paused VOD -> just play from where it is. */
        Play,

        /** Paused live -> the DVR window slid while paused; snap to the live
         * edge before playing so it doesn't get stuck buffering segments the
         * server already dropped. */
        SeekToLiveEdgeThenPlay,
    }

    fun resumeAction(isPlaying: Boolean, isLive: Boolean): ResumeAction = when {
        isPlaying -> ResumeAction.Pause
        isLive -> ResumeAction.SeekToLiveEdgeThenPlay
        else -> ResumeAction.Play
    }

    /** Seek target for a forward jump, clamped to the end when the duration is
     * known. A non-positive [durationMs] (live / not yet known) is left
     * unclamped so live can still seek toward its edge. */
    fun seekForwardTarget(currentMs: Long, deltaMs: Long, durationMs: Long): Long {
        val ceiling = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        return (currentMs + deltaMs).coerceAtMost(ceiling)
    }

    /** Seek target for a backward jump, never past the start. */
    fun seekBackwardTarget(currentMs: Long, deltaMs: Long): Long =
        (currentMs - deltaMs).coerceAtLeast(0L)
}
