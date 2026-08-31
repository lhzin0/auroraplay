package com.auroraplay.iptv.data.database.entity

import androidx.room.Entity

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
    val streamUrl: String,
    val addedAtMillis: Long,
)
