package com.auroraplay.iptv.domain.model

data class WatchProgress(
    val contentId: String,
    val type: ContentType,
    val profileId: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val lastWatchedMillis: Long = System.currentTimeMillis(),
) {
    val fraction: Float
        get() = if (durationMillis <= 0) 0f else (positionMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
}
