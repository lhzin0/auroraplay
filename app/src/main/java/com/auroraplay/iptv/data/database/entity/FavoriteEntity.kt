package com.auroraplay.iptv.data.database.entity

import androidx.room.Entity

// `type` is part of the identity: an Xtream stream id is only unique within a
// (provider, kind), so a channel and a movie can share the numeric id. Without
// `type` in the key, favouriting one flipped the other (audit #3).
@Entity(tableName = "favorites", primaryKeys = ["contentId", "type", "profileId"])
data class FavoriteEntity(
    val contentId: String,
    val type: String,
    val profileId: String,
    val addedAtMillis: Long,
)
