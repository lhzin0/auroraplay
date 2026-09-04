package com.auroraplay.iptv.domain.repository

import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.Favorite
import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    fun observeFavorites(connectionId: String, profileId: String, type: ContentType? = null): Flow<List<Favorite>>
    fun isFavorite(connectionId: String, profileId: String, contentId: String, type: ContentType): Flow<Boolean>
    suspend fun toggleFavorite(connectionId: String, profileId: String, contentId: String, type: ContentType)
}
