package com.auroraplay.iptv.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.auroraplay.iptv.core.util.AppLog
import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.SettingsRepository
import com.auroraplay.iptv.domain.repository.SyncStage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Keeps the catalog fresh even when the app isn't opened. The in-app
 * "Configurações › Dados › Sincronização automática" refresh only ever ran on
 * launch (a plain coroutine in AuroraApplication); this is its background
 * counterpart, scheduled once by [CatalogSyncScheduler.schedulePeriodic].
 *
 * Silent — no foreground notification, unlike the user-initiated
 * [CatalogSyncWorker]. It wakes on a fixed cadence and does nothing unless
 * `autoSyncHours` has actually elapsed since the last successful sync of the
 * default connection, so a longer interval than the wake cadence is honoured
 * and "off" (`autoSyncHours <= 0`) is a no-op.
 */
@HiltWorker
class PeriodicCatalogSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val connectionRepository: ConnectionRepository,
    private val contentRepository: ContentRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val hours = runCatching { settingsRepository.observeSettings().first().autoSyncHours }.getOrDefault(0)
        if (hours <= 0) return Result.success() // auto-sync turned off

        val connection = runCatching { connectionRepository.getDefaultConnection() }.getOrNull()
            ?: return Result.success() // nothing to sync yet

        val lastSync = runCatching { contentRepository.getLastSyncMillis(connection.id) }.getOrNull() ?: 0L
        val due = System.currentTimeMillis() - lastSync >= hours * 3_600_000L
        if (!due) return Result.success()

        return try {
            var completed = false
            var partial = false
            var error: String? = null
            contentRepository.syncConnection(connection.id).collect { result ->
                when (result) {
                    is Resource.Success -> when (result.data) {
                        SyncStage.DONE -> completed = true
                        SyncStage.PARTIAL -> partial = true
                        else -> Unit
                    }
                    is Resource.Error -> error = result.message
                    else -> Unit
                }
            }
            when {
                completed && error == null -> Result.success()
                // Retry a partial or failed background run later; old rows stay intact.
                else -> {
                    if (partial) AppLog.w("PeriodicCatalogSync", "partial sync for ${connection.id}, will retry")
                    else AppLog.w("PeriodicCatalogSync", "sync failed for ${connection.id}: ${error ?: "unknown"}")
                    Result.retry()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("PeriodicCatalogSync", "sync threw for ${connection.id}", e)
            Result.retry()
        }
    }
}
