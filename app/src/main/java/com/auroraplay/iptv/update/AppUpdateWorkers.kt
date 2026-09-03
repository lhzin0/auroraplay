package com.auroraplay.iptv.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.UUID

@HiltWorker
internal class AppUpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted params: WorkerParameters, private val manager: AppUpdateManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try { manager.check(); Result.success() }
    catch (e: CancellationException) { throw e }
    catch (_: Exception) { if (runAttemptCount < 2) Result.retry() else Result.failure() }
}

@HiltWorker
internal class AppUpdateDownloadWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted params: WorkerParameters, private val manager: AppUpdateManager,
) : CoroutineWorker(context, params) {
    private val notifications = AppUpdateNotifications(context)
    override suspend fun getForegroundInfo() = notifications.foreground(id, 0)
    override suspend fun doWork(): Result {
        try {
            val release = AppRelease.parse(requireNotNull(inputData.getString("manifest")))
            setForeground(getForegroundInfo())
            manager.downloadFile(release) { percent ->
                setProgress(workDataOf("percent" to percent))
                setForeground(notifications.foreground(id, percent))
            }
            notifications.notify("Atualização pronta", "AuroraPlay ${release.version} foi baixado e conferido. Toque para instalar.")
            return Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: IOException) {
            if (runAttemptCount < 2) return Result.retry()
            return failed("Download interrompido. Confira sua conexão e tente novamente.")
        } catch (_: Exception) {
            return failed("A atualização não pôde ser validada. Baixe novamente pelo GitHub oficial.")
        }
    }
    private fun failed(message: String): Result {
        notifications.notify("Atualização não concluída", message)
        return Result.failure(workDataOf("error" to message))
    }
}

internal class AppUpdateNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private fun builder(): NotificationCompat.Builder {
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel("app_updates", "Atualizações do AuroraPlay", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(context, "app_updates")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(PendingIntent.getActivity(context, 3100, Intent(context, AppUpdateActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    }
    fun foreground(workId: UUID, percent: Int): ForegroundInfo {
        val notification = builder().setContentTitle("Baixando atualização do AuroraPlay")
            .setContentText(if (percent >= 100) "Conferindo o arquivo…" else "$percent%")
            .setProgress(100, percent, percent >= 100).setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", WorkManager.getInstance(context).createCancelPendingIntent(workId)).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(3101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(3101, notification)
    }
    fun notify(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        try { manager.notify(3102, builder().setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).build()) }
        catch (_: SecurityException) { }
    }
}
