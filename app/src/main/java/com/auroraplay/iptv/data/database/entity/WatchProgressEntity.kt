package com.auroraplay.iptv.data.database.entity

import androidx.room.Entity

@Entity(tableName = "watch_progress", primaryKeys = ["contentId", "profileId"])
data class WatchProgressEntity(
    val contentId: String,
    val type: String,
    val profileId: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val lastWatchedMillis: Long,
)
