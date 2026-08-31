package com.auroraplay.iptv.domain.usecase

import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.SyncStage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SyncContentUseCase @Inject constructor(
    private val contentRepository: ContentRepository,
) {
    operator fun invoke(connectionId: String): Flow<Resource<SyncStage>> =
        contentRepository.syncConnection(connectionId)
}
