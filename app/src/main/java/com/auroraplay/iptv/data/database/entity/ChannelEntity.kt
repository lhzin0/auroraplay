package com.auroraplay.iptv.data.database.entity

import androidx.room.Entity
import androidx.room.Index

// Audit #4: the authenticated playback URL is never stored — it embeds the
// Xtream username + password and this table is auto-synced with 1000s of rows.
// Only the stream id (`id`) is kept; the URL is rebuilt on demand from the
// connection's credentials (see CatalogMappers.toDomain / XtreamUrlBuilder).
// Audit #20: index aligned to `observe(connectionId, categoryId?)`.
@Entity(
    tableName = "channels",
    primaryKeys = ["id", "connectionId"],
    indices = [Index(value = ["connectionId", "categoryId"])],
)
data class ChannelEntity(
    val id: String,
    val connectionId: String,
    val name: String,
    val logoUrl: String?,
    val categoryId: String,
    val categoryName: String,
    val epgChannelId: String?,
)
