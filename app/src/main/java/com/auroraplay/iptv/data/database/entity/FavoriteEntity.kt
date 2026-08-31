package com.auroraplay.iptv.data.database.entity

import androidx.room.Entity

@Entity(tableName = "favorites", primaryKeys = ["contentId", "profileId"])
data class FavoriteEntity(
    val contentId: String,
    val type: String,
    val profileId: String,
    val addedAtMillis: Long,
)
