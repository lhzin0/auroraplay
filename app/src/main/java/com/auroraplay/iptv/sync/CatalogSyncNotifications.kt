package com.auroraplay.iptv.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.auroraplay.iptv.MainActivity
import com.auroraplay.iptv.domain.repository.SyncStage
import java.util.UUID

internal class CatalogSyncNotifications(private val context: Context, private val workId: UUID) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notificationId = 10_000 + (workId.hashCode() and Int.MAX_VALUE) % 1_000_000

    fun foreground(stage: SyncStage): ForegroundInfo {
        val completed = when (stage) {
            SyncStage.CONNECTING, SyncStage.CHANNELS -> 0
            SyncStage.MOVIES -> 1
            SyncStage.SERIES -> 2
            SyncStage.DONE -> 3
        }
        val notification = builder()
            .setContentTitle("Sincronizando catálogo")
            .setContentText(stage.syncLabel())
            .setOngoing(true)
            .setProgress(3, completed, stage == SyncStage.CONNECTING)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", WorkManager.getInstance(context).createCancelPendingIntent(workId))
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(notificationId, notification)
    }

    fun finished(connectionId: String, success: Boolean, message: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        // A separate, tagged notification survives removal of the foreground notification.
        val notification = builder()
            .setContentTitle(if (success) "Sincronização concluída" else "Falha na sincronização")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        try { manager.notify("catalog_result:$connectionId", 4002, notification) }
        catch (_: SecurityException) { /* Permission can be revoked while the work is running. */ }
    }

    private fun builder(): NotificationCompat.Builder {
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sincronização de playlists", NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    companion object { const val CHANNEL = "catalog_sync" }
}
