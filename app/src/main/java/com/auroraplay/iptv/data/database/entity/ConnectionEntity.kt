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
    /** "XTREAM" (default) or "M3U". For M3U, [serverUrl] holds the playlist
     * URL itself and [username]/password are unused (kept blank). */
    val sourceType: String = "XTREAM",
    /** Optional XMLTV guide URL, importable for either [sourceType] — some
     * Xtream providers ship no usable EPG of their own, and M3U has no EPG
     * API at all. Programs land in `epg_programs`, matched to a channel by
     * [ChannelEntity.epgChannelId]. */
    val xmltvUrl: String? = null,
)
