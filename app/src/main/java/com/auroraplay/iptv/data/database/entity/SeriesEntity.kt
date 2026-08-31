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
    val addedAtMillis: Long,
)

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
    val streamUrl: String,
)
