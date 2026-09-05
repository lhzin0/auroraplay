package com.auroraplay.iptv.data.database.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * One programme entry imported from a connection's XMLTV guide (see
 * [ConnectionEntity.xmltvUrl]). Rows are keyed by [epgChannelId] — the raw
 * `channel` attribute from the XMLTV file — not by [ChannelEntity.id]:
 * matching to an actual channel happens at read time by comparing against
 * [ChannelEntity.epgChannelId], the same field Xtream's own EPG id lands in.
 */
@Entity(
    tableName = "epg_programs",
    primaryKeys = ["id"],
    indices = [Index(value = ["connectionId", "epgChannelId", "startMillis"])],
)
data class EpgProgramEntity(
    /** `"$connectionId:$epgChannelId:$startMillis"` — stable and unique per
     * (connection, channel, slot) without needing the provider to supply one. */
    val id: String,
    val connectionId: String,
    val epgChannelId: String,
    val title: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
)
