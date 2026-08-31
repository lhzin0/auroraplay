package com.auroraplay.iptv.domain.repository

import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.WatchProgress
import kotlinx.coroutines.flow.Flow

interface WatchProgressRepository {
    fun observeContinueWatching(profileId: String): Flow<List<WatchProgress>>
    suspend fun getProgress(profileId: String, contentId: String): WatchProgress?
    suspend fun saveProgress(progress: WatchProgress)
    suspend fun removeProgress(profileId: String, contentId: String, type: ContentType)
}
