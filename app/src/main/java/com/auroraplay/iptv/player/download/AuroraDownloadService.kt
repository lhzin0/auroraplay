package com.auroraplay.iptv.player.download

import android.app.Notification
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.auroraplay.iptv.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val CHANNEL_ID = "aurora_downloads"
private const val JOB_ID = 1001
private const val FOREGROUND_NOTIFICATION_ID = 2001

/**
 * Runs downloads in the background and shows a single progress
 * notification (required by Android for long-running foreground work).
 * All actual download logic lives in Media3's DownloadManager; this class
 * just wires it to the OS.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@AndroidEntryPoint
class AuroraDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    // Media3 feeds this into context.getString() unguarded in onCreate() —
    // 0 here is what threw Resources$NotFoundException on "Baixar". It also
    // creates the channel itself from this, so no manual onCreate needed.
    R.string.download_notification_channel_name,
    0,
) {
    @Inject lateinit var appDownloadManager: DownloadManager

    private val notificationHelper: DownloadNotificationHelper by lazy {
        DownloadNotificationHelper(this, CHANNEL_ID)
    }

    override fun getDownloadManager(): DownloadManager = appDownloadManager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        notificationHelper.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            null,
            downloads,
            notMetRequirements,
        )
}
