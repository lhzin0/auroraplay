package com.auroraplay.iptv.data.database.entity

import androidx.room.Entity
import androidx.room.Index

// Composite identity (audit #3): an Xtream stream id is only unique within a
// (provider, kind), so a channel and a movie can share the numeric id, and two
// playlists can reuse the same id for different titles. The key is
// connectionId + contentId + type (+ profileId for the user dimension).
// Audit #20: `observe(connectionId, profileId, type?)` is the hot query.
@Entity(
    tableName = "favorites",
    primaryKeys = ["connectionId", "contentId", "type", "profileId"],
    indices = [Index(value = ["connectionId", "profileId", "type"])],
)
data class FavoriteEntity(
    // Default "" only so an older backup JSON (no connectionId) still
    // deserializes; the schema column stays TEXT NOT NULL.
    val connectionId: String = "",
    val contentId: String,
    val type: String,
    val profileId: String,
    val addedAtMillis: Long,
)
