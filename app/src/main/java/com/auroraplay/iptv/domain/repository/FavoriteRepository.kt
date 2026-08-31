package com.auroraplay.iptv.domain.repository

import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.Favorite
import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    fun observeFavorites(profileId: String, type: ContentType? = null): Flow<List<Favorite>>
    fun isFavorite(profileId: String, contentId: String): Flow<Boolean>
    suspend fun toggleFavorite(profileId: String, contentId: String, type: ContentType)
}
