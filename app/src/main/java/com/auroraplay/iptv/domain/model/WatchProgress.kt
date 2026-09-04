package com.auroraplay.iptv.domain.model

data class WatchProgress(
    val connectionId: String,
    val contentId: String,
    val type: ContentType,
    val profileId: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val lastWatchedMillis: Long = System.currentTimeMillis(),
    /** Snapshot for the Histórico so it survives the title leaving the
     * catalog. For an episode this is the series name. */
    val title: String? = null,
    val posterUrl: String? = null,
) {
    val fraction: Float
        get() = if (durationMillis <= 0) 0f else (positionMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
}
