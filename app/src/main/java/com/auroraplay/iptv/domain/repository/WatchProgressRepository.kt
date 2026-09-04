package com.auroraplay.iptv.domain.repository

import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.WatchProgress
import kotlinx.coroutines.flow.Flow

interface WatchProgressRepository {
    fun observeContinueWatching(connectionId: String, profileId: String): Flow<List<WatchProgress>>
    suspend fun getProgress(connectionId: String, profileId: String, contentId: String, type: ContentType): WatchProgress?
    /** [progress] carries the connectionId. */
    suspend fun saveProgress(progress: WatchProgress)
    suspend fun removeProgress(connectionId: String, profileId: String, contentId: String, type: ContentType)

    /** Full watch history (films + episodes) for the profile, across every
     * connection, newest first — the "Histórico" card. Kept until
     * [clearWatchHistory]. Connection-agnostic so it survives a playlist being
     * removed (audit #17). */
    fun observeWatchHistory(profileId: String): Flow<List<WatchProgress>>
    /** Manual, user-initiated only. */
    suspend fun clearWatchHistory(profileId: String)
    /** Remove one Histórico item — a movie or a single episode ("<seriesId>:<epId>"). */
    suspend fun deleteHistoryItem(profileId: String, contentId: String, type: ContentType)
    /** Remove a whole series (its row + every episode) from the Histórico. */
    suspend fun deleteSeriesFromHistory(profileId: String, seriesId: String)

    /** "Remover de Continuar assistindo". For [isSeries] every episode of the
     * series is dropped from the rail. Progress and history are kept. */
    suspend fun removeFromContinueWatching(connectionId: String, profileId: String, contentId: String, isSeries: Boolean)

    /** Last 10 live channels this profile opened on [connectionId], newest first. */
    fun observeChannelHistory(connectionId: String, profileId: String): Flow<List<WatchProgress>>
    /** Marks a live channel as just-watched (for the "Canais recentes" rail). */
    suspend fun recordChannelWatch(connectionId: String, profileId: String, channelId: String)
}
