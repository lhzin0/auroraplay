package com.auroraplay.iptv.data.database.entity

import androidx.room.Entity

// Composite identity (audit #3): an Xtream stream id is only unique within a
// (provider, kind), so a channel and a movie can share the numeric id, and two
// playlists can reuse the same id for different titles. The key is
// connectionId + contentId + type (+ profileId for the user dimension).
@Entity(tableName = "watch_progress", primaryKeys = ["connectionId", "contentId", "type", "profileId"])
data class WatchProgressEntity(
    // Default "" only so an older backup JSON (no connectionId) still
    // deserializes; the schema column stays TEXT NOT NULL.
    val connectionId: String = "",
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
