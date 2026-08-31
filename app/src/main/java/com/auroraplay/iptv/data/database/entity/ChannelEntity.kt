package com.auroraplay.iptv.data.database.entity

import androidx.room.Entity

@Entity(tableName = "channels", primaryKeys = ["id", "connectionId"])
data class ChannelEntity(
    val id: String,
    val connectionId: String,
    val name: String,
    val logoUrl: String?,
    val categoryId: String,
    val categoryName: String,
    val streamUrl: String,
    val epgChannelId: String?,
)
