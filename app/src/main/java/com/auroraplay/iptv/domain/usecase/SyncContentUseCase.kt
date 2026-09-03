package com.auroraplay.iptv.domain.usecase

import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.sync.CatalogSyncScheduler
import com.auroraplay.iptv.domain.repository.SyncStage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SyncContentUseCase @Inject constructor(
    private val scheduler: CatalogSyncScheduler,
) {
    operator fun invoke(connectionId: String): Flow<Resource<SyncStage>> =
        scheduler.sync(connectionId)

    fun observeActive(): Flow<Map<String, SyncStage?>> = scheduler.observeActive()
    suspend fun cancel(connectionId: String) = scheduler.cancel(connectionId)
}
