package com.auroraplay.iptv.data.repository

import com.auroraplay.iptv.data.database.dao.WatchProgressDao
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
}
