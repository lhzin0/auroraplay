package com.auroraplay.iptv.domain.usecase

import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.repository.FavoriteRepository
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
) {
    suspend operator fun invoke(profileId: String, contentId: String, type: ContentType) {
        favoriteRepository.toggleFavorite(profileId, contentId, type)
    }
}
