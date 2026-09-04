package com.auroraplay.iptv.data.repository

import com.auroraplay.iptv.data.database.dao.FavoriteDao
import com.auroraplay.iptv.data.database.entity.FavoriteEntity
import com.auroraplay.iptv.data.mapper.toDomain
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.Favorite
import com.auroraplay.iptv.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepositoryImpl @Inject constructor(
    private val dao: FavoriteDao,
) : FavoriteRepository {

    override fun observeFavorites(profileId: String, type: ContentType?): Flow<List<Favorite>> =
        dao.observe(profileId, type?.name).map { list -> list.map { it.toDomain() } }

    override fun isFavorite(profileId: String, contentId: String, type: ContentType): Flow<Boolean> =
        dao.observeIsFavorite(profileId, contentId, type.name)

    override suspend fun toggleFavorite(profileId: String, contentId: String, type: ContentType) {
        val existing = dao.get(profileId, contentId, type.name)
        if (existing != null) {
            dao.delete(profileId, contentId, type.name)
        } else {
            dao.insert(FavoriteEntity(contentId, type.name, profileId, System.currentTimeMillis()))
        }
    }
}
