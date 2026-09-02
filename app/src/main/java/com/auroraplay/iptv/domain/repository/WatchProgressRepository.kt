package com.auroraplay.iptv.domain.repository

import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.WatchProgress
import kotlinx.coroutines.flow.Flow

interface WatchProgressRepository {
    fun observeContinueWatching(profileId: String): Flow<List<WatchProgress>>
    suspend fun getProgress(profileId: String, contentId: String): WatchProgress?
    suspend fun saveProgress(progress: WatchProgress)
    suspend fun removeProgress(profileId: String, contentId: String, type: ContentType)

    /** Last 10 live channels this profile opened, newest first. */
    fun observeChannelHistory(profileId: String): Flow<List<WatchProgress>>
    /** Marks a live channel as just-watched (for the "Canais recentes" rail). */
    suspend fun recordChannelWatch(profileId: String, channelId: String)
}
