package com.auroraplay.iptv.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.domain.repository.SyncStage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The work outlives its screen; concurrent callers follow the same connection job. */
@Singleton
class CatalogSyncScheduler @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)
    private val enqueueMutex = Mutex()

    suspend fun enqueue(connectionId: String): UUID = enqueueMutex.withLock {
        val name = "catalog_sync:$connectionId"
        val active = workManager.getWorkInfosForUniqueWorkFlow(name).first().firstOrNull { !it.state.isFinished }
        if (active != null) return@withLock active.id
        val request = OneTimeWorkRequestBuilder<CatalogSyncWorker>()
            .setInputData(workDataOf(CONNECTION_ID to connectionId))
            .addTag(TAG).addTag(CONNECTION_TAG + connectionId)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            // Without this, a sync requested with no network runs immediately,
            // fails every call in one shot, and only retries whenever the app
            // happens to ask again — instead of WorkManager itself waiting for
            // connectivity, same as NewEpisodeScheduler already does.
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request).await()
        request.id
    }

    fun sync(connectionId: String): Flow<Resource<SyncStage>> = flow<Resource<SyncStage>> {
        emit(Resource.Loading)
        val id = enqueue(connectionId)
        emitAll(workManager.getWorkInfoByIdFlow(id).filterNotNull().transformWhile { info ->
            emit(info.toSyncResource())
            !info.state.isFinished
        })
    }.catch { emit(Resource.Error("Não foi possível iniciar a sincronização. Tente atualizar novamente.")) }

    fun observeActive(): Flow<Map<String, SyncStage?>> = workManager.getWorkInfosByTagFlow(TAG).map { jobs ->
        jobs.filter { !it.state.isFinished }.mapNotNull { job ->
            val connectionId = job.tags.firstOrNull { it.startsWith(CONNECTION_TAG) }?.removePrefix(CONNECTION_TAG)
            connectionId?.let { it to job.syncStage() }
        }.toMap()
    }

    suspend fun cancel(connectionId: String) {
        workManager.cancelUniqueWork("catalog_sync:$connectionId").await()
    }

    companion object {
        const val TAG = "catalog_sync"
        const val CONNECTION_TAG = "catalog_connection:"
        const val CONNECTION_ID = "connection_id"
        const val STAGE = "stage"
        const val ERROR = "error"
    }
}

internal fun WorkInfo.syncStage(): SyncStage? = progress.getString(CatalogSyncScheduler.STAGE)?.let { value ->
    SyncStage.entries.firstOrNull { it.name == value }
}

internal fun WorkInfo.toSyncResource(): Resource<SyncStage> = when (state) {
    WorkInfo.State.SUCCEEDED -> Resource.Success(SyncStage.DONE)
    WorkInfo.State.FAILED -> Resource.Error(outputData.getString(CatalogSyncScheduler.ERROR) ?: "Não foi possível sincronizar. Tente atualizar novamente.")
    WorkInfo.State.CANCELLED -> Resource.Error("Sincronização cancelada.")
    else -> syncStage()?.let { Resource.Success(it) } ?: Resource.Loading
}

fun SyncStage?.syncLabel(): String = when (this) {
    null -> "Aguardando sincronização…"
    SyncStage.CONNECTING -> "Conectando à playlist…"
    SyncStage.CHANNELS -> "Etapa 1 de 3: canais…"
    SyncStage.MOVIES -> "Etapa 2 de 3: filmes…"
    SyncStage.SERIES -> "Etapa 3 de 3: séries…"
    SyncStage.DONE -> "Sincronização concluída."
    SyncStage.PARTIAL -> "Sincronização parcial — tentaremos as seções restantes em breve."
}
