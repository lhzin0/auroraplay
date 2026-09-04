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

    override fun observeContinueWatching(connectionId: String, profileId: String): Flow<List<WatchProgress>> =
        dao.observeContinueWatching(connectionId, profileId).map { list -> list.map { it.toDomain() } }

    override suspend fun getProgress(connectionId: String, profileId: String, contentId: String, type: ContentType): WatchProgress? =
        dao.get(connectionId, profileId, contentId, type.name)?.toDomain()

    override suspend fun saveProgress(progress: WatchProgress) = dao.upsert(progress.toEntity())

    override suspend fun removeProgress(connectionId: String, profileId: String, contentId: String, type: ContentType) =
        dao.delete(connectionId, profileId, contentId, type.name)

    override fun observeWatchHistory(profileId: String): Flow<List<WatchProgress>> =
        dao.observeWatchHistory(profileId).map { list -> list.map { it.toDomain() } }

    override suspend fun clearWatchHistory(profileId: String) = dao.clearWatchHistory(profileId)

    override suspend fun deleteHistoryItem(profileId: String, contentId: String, type: ContentType) =
        dao.deleteByKey(profileId, contentId, type.name)

    override suspend fun deleteSeriesFromHistory(profileId: String, seriesId: String) =
        dao.deleteSeriesHistory(profileId, seriesId)

    override suspend fun removeFromContinueWatching(connectionId: String, profileId: String, contentId: String, isSeries: Boolean) {
        if (isSeries) dao.hideSeriesFromContinue(connectionId, profileId, contentId)
        else dao.hideFromContinue(connectionId, profileId, contentId, ContentType.MOVIE.name)
    }

    override fun observeChannelHistory(connectionId: String, profileId: String): Flow<List<WatchProgress>> =
        dao.observeChannelHistory(connectionId, profileId).map { list -> list.map { it.toDomain() } }

    override suspend fun recordChannelWatch(connectionId: String, profileId: String, channelId: String) {
        dao.upsert(
            WatchProgressEntity(
                connectionId = connectionId,
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
        dao.trimChannelHistory(connectionId, profileId)
    }
}
