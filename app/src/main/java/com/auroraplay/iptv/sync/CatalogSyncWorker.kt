package com.auroraplay.iptv.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.SyncStage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class CatalogSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val contentRepository: ContentRepository,
) : CoroutineWorker(context, params) {
    private val notifications = CatalogSyncNotifications(context, id)

    override suspend fun getForegroundInfo(): ForegroundInfo = notifications.foreground(SyncStage.CONNECTING)

    override suspend fun doWork(): Result {
        val connectionId = inputData.getString(CatalogSyncScheduler.CONNECTION_ID) ?: return Result.failure()
        var completed = false
        var partial = false
        var error: String? = null
        try {
            setForeground(getForegroundInfo())
            contentRepository.syncConnection(connectionId).collect { result ->
                when (result) {
                    is Resource.Success -> when (result.data) {
                        SyncStage.DONE -> completed = true
                        SyncStage.PARTIAL -> partial = true
                        else -> {
                            setProgress(workDataOf(CatalogSyncScheduler.STAGE to result.data.name))
                            setForeground(notifications.foreground(result.data))
                        }
                    }
                    is Resource.Error -> error = result.message
                    else -> Unit
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = "Não foi possível sincronizar. Tente atualizar novamente."
        }
        return when {
            completed && error == null -> {
                notifications.finished(connectionId, true, "Catálogo atualizado.")
                Result.success()
            }
            // Some sections synced, others didn't — retry the whole run later
            // rather than declaring success (audit #10). Old rows are intact.
            partial && error == null -> {
                notifications.finished(connectionId, false, "Sincronização parcial — tentaremos novamente.")
                Result.retry()
            }
            else -> {
                val message = error ?: "Não foi possível atualizar o catálogo."
                notifications.finished(connectionId, false, message)
                Result.failure(workDataOf(CatalogSyncScheduler.ERROR to message))
            }
        }
    }
}
