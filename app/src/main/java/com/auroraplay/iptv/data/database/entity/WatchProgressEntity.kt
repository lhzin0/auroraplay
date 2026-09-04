package com.auroraplay.iptv.data.database.entity

import androidx.room.Entity

@Entity(tableName = "watch_progress", primaryKeys = ["contentId", "profileId"])
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
)
