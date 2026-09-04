package com.auroraplay.iptv.data.repository

import com.auroraplay.iptv.data.database.dao.WatchProgressDao
import com.auroraplay.iptv.data.database.entity.WatchProgressEntity
import com.auroraplay.iptv.data.mapper.toDomain
import com.auroraplay.iptv.data.mapper.toEntity
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.WatchProgress
import com.auroraplay.iptv.domain.repository.WatchProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchProgressRepositoryImpl @Inject constructor(
    private val dao: WatchProgressDao,
) : WatchProgressRepository {

    override fun observeContinueWatching(profileId: String): Flow<List<WatchProgress>> =
        dao.observeContinueWatching(profileId).map { list -> list.map { it.toDomain() } }

    override suspend fun getProgress(profileId: String, contentId: String): WatchProgress? =
        dao.get(profileId, contentId)?.toDomain()

    override suspend fun saveProgress(progress: WatchProgress) = dao.upsert(progress.toEntity())

    override suspend fun removeProgress(profileId: String, contentId: String, type: ContentType) =
        dao.delete(profileId, contentId, type.name)

    override fun observeWatchHistory(profileId: String): Flow<List<WatchProgress>> =
        dao.observeWatchHistory(profileId).map { list -> list.map { it.toDomain() } }

    override suspend fun clearWatchHistory(profileId: String) = dao.clearWatchHistory(profileId)

    override suspend fun deleteHistoryItem(profileId: String, contentId: String) =
        dao.deleteByKey(profileId, contentId)

    override suspend fun deleteSeriesFromHistory(profileId: String, seriesId: String) =
        dao.deleteSeriesHistory(profileId, seriesId)

    override suspend fun removeFromContinueWatching(profileId: String, contentId: String, isSeries: Boolean) {
        if (isSeries) dao.hideSeriesFromContinue(profileId, contentId)
        else dao.hideFromContinue(profileId, contentId)
    }

    override fun observeChannelHistory(profileId: String): Flow<List<WatchProgress>> =
        dao.observeChannelHistory(profileId).map { list -> list.map { it.toDomain() } }

    override suspend fun recordChannelWatch(profileId: String, channelId: String) {
        dao.upsert(
            WatchProgressEntity(
                contentId = channelId,
                type = ContentType.LIVE.name,
                profileId = profileId,
                positionMillis = 0L,
                durationMillis = 0L,
                seasonNumber = null,
                episodeNumber = null,
                lastWatchedMillis = System.currentTimeMillis(),
            )
        )
        dao.trimChannelHistory(profileId)
    }
}
