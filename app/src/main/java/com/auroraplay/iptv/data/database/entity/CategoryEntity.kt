package com.auroraplay.iptv.data.database.entity

import androidx.room.Entity

@Entity(tableName = "categories", primaryKeys = ["id", "connectionId", "type"])
data class CategoryEntity(
    val id: String,
    val connectionId: String,
    val name: String,
    val type: String, // LIVE / MOVIE / SERIES
)
