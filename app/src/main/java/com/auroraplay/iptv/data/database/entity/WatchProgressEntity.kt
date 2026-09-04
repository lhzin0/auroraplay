package com.auroraplay.iptv.data.database.entity

import androidx.room.Entity

// `type` is part of the identity: an Xtream stream id is only unique within a
// (provider, kind), so a channel and a movie can share the numeric id. Without
// `type` in the key, a LIVE row and a MOVIE row with the same id + profile
// clobbered each other (audit #3).
@Entity(tableName = "watch_progress", primaryKeys = ["contentId", "type", "profileId"])
data class WatchProgressEntity(
    val contentId: String,
    val type: String,
    val profileId: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val lastWatchedMillis: Long,
    /**
     * The user chose "Remover de Continuar assistindo". The row stays — history
     * and progress are untouched — it just no longer appears in that rail.
     * A fresh progress write (resuming the title) inserts a row with the
     * default `false` again, so resuming brings the card back.
     */
    val hiddenFromContinue: Boolean = false,
    /**
     * Display snapshot captured at play time so the Histórico survives the
     * title being removed from / not yet synced into the catalog. For an
     * episode row this is the SERIES name (episodes group under it).
     */
    val title: String? = null,
    val posterUrl: String? = null,
)
