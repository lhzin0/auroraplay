package com.auroraplay.iptv.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val serverUrl: String,
    val username: String,
    val isDefault: Boolean = false,
    val status: String = "UNKNOWN",
    val lastSyncMillis: Long? = null,
    val profileId: String? = null,
)
