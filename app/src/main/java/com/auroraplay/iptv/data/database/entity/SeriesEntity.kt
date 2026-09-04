package com.auroraplay.iptv.data.database.entity

import androidx.room.Entity

@Entity(tableName = "series", primaryKeys = ["id", "connectionId"])
data class SeriesEntity(
    val id: String,
    val connectionId: String,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val categoryId: String,
    val categoryName: String,
    val year: String?,
    val genre: String?,
    val plot: String?,
    val rating: Double?,
    /** "Legendado" when the raw provider name/category said so (computed at
     * sync, before the display name is cleaned); null otherwise. */
    val audioLabel: String? = null,
    val addedAtMillis: Long,
    /** Epoch millis of the last successful `get_series_info` episode fetch for
     * this series; 0 = never. Drives the episode-list TTL (audit #7) so an
     * already-opened series still picks up new episodes without a full sync. */
    val episodesSyncedAtMillis: Long = 0,
)

// Audit #4: the authenticated episode URL (server/series/<user>/<pass>/<id>.ext)
// is never stored. Only the episode id + container extension are kept; the URL
// is rebuilt on demand from the connection's credentials.
@androidx.room.Entity(tableName = "episodes", primaryKeys = ["id", "seriesId", "connectionId"])
data class EpisodeEntity(
    val id: String,
    val seriesId: String,
    val connectionId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val thumbnailUrl: String?,
    val durationLabel: String?,
    val plot: String?,
    /** Container ("mp4", "mkv", ...) needed to build the playback URL; null
     * falls back to "mp4". */
    val containerExtension: String? = null,
)
