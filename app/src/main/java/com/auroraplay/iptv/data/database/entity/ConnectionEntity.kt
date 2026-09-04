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
    /** Optional mirror server for the same account — tried automatically when
     * [serverUrl] is unreachable (a network failure, not a credentials
     * rejection: retrying the same bad login elsewhere won't help). */
    val backupServerUrl: String? = null,
)
