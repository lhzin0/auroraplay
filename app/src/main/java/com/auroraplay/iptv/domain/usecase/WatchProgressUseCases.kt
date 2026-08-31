package com.auroraplay.iptv.domain.usecase

import com.auroraplay.iptv.domain.model.WatchProgress
import com.auroraplay.iptv.domain.repository.WatchProgressRepository
import javax.inject.Inject

class SaveWatchProgressUseCase @Inject constructor(
    private val repository: WatchProgressRepository,
) {
    suspend operator fun invoke(progress: WatchProgress) = repository.saveProgress(progress)
}
