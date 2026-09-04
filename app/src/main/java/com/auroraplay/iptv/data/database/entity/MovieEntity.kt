package com.auroraplay.iptv.data.database.entity

import androidx.room.Entity

// Audit #4: the authenticated playback URL is never stored — it embeds the
// Xtream username + password and this table is auto-synced with 1000s of rows.
// Only the stream id (`id`) + container extension are kept; the URL is rebuilt
// on demand from the connection's credentials (see CatalogMappers.toDomain).
@Entity(tableName = "movies", primaryKeys = ["id", "connectionId"])
data class MovieEntity(
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
    val durationLabel: String?,
    val rating: Double?,
    /** Container ("mp4", "mkv", ...) needed to build the VOD playback URL;
     * null falls back to "mp4". */
    val containerExtension: String? = null,
    /** "Legendado" when the raw provider name/category said so (computed at
     * sync, before the display name is cleaned); null otherwise. */
    val audioLabel: String? = null,
    val addedAtMillis: Long,
)
