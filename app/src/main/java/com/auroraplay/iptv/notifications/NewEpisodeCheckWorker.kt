package com.auroraplay.iptv.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.auroraplay.iptv.MainActivity
import com.auroraplay.iptv.data.datastore.EpisodeCountStore
import com.auroraplay.iptv.data.datastore.NotificationStore
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.FavoriteRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Xtream never marks an episode as "new" — the only signal available is a
 * series' own episode count growing between checks. This worker re-syncs
 * each connection, then compares every favorited series against the count
 * [EpisodeCountStore] saved last time it ran.
 */
@HiltWorker
class NewEpisodeCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val connectionRepository: ConnectionRepository,
    private val contentRepository: ContentRepository,
    private val favoriteRepository: FavoriteRepository,
    private val profileRepository: ProfileRepository,
    private val episodeCountStore: EpisodeCountStore,
    private val notificationStore: NotificationStore,
    private val settingsRepository: com.auroraplay.iptv.domain.repository.SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = runCatching { settingsRepository.observeSettings().first() }.getOrNull()
        if (settings?.notifyNewEpisodes == false) return Result.success()

        val connections = runCatching { connectionRepository.observeConnections().first() }.getOrDefault(emptyList())
        if (connections.isEmpty()) return Result.success()

        val profiles = runCatching { profileRepository.observeProfiles().first() }.getOrDefault(emptyList())
        if (profiles.isEmpty()) return Result.success()

        val newlyAvailable = mutableListOf<String>()

        connections.forEach { connection ->
            // Best-effort refresh: an unreachable server just means this run
            // finds nothing new, not a failed job — retrying hourly on a
            // server that's down would only drain the battery for nothing.
            runCatching { contentRepository.syncConnection(connection.id).collect {} }

            val favoriteSeriesIds = profiles
                .flatMap { runCatching { favoriteRepository.observeFavorites(it.id, ContentType.SERIES).first() }.getOrDefault(emptyList()) }
                .map { it.contentId }
                .toSet()
            if (favoriteSeriesIds.isEmpty()) return@forEach

            val allSeries = runCatching { contentRepository.observeSeries(connection.id).first() }.getOrDefault(emptyList())
            allSeries.filter { it.id in favoriteSeriesIds }.forEach { series ->
                val episodeCount = series.seasons.sumOf { it.episodes.size }
                val known = episodeCountStore.getKnownEpisodeCount(series.id)
                episodeCountStore.setKnownEpisodeCount(series.id, episodeCount)
                // null means "first time seeing this series" — not a growth,
                // or every favorite would notify once right after being added.
                if (known != null && episodeCount > known) {
                    newlyAvailable += series.name
                }
            }
        }

        if (newlyAvailable.isNotEmpty()) notify(newlyAvailable)
        return Result.success()
    }

    private suspend fun notify(seriesNames: List<String>) {
        val title = if (seriesNames.size == 1) seriesNames.first() else "${seriesNames.size} séries da sua lista"
        val text = if (seriesNames.size == 1) "Novo episódio disponível" else "Têm novos episódios disponíveis"

        // Recorded here regardless of whether the system tray notification
        // below actually manages to show (missing permission, no channel,
        // etc.) — the bell icon in the app is a second, independent record
        // of the same event, not just a mirror of the OS tray.
        runCatching { notificationStore.add(title, text) }

        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is requested on first launch, but a person can still
            // deny it — silently skipping is correct, not a bug to surface.
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Novos episódios", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "new_episodes"
        // Distinct from AuroraDownloadService's foreground notification id
        // (2001) so posting one can never cancel or replace the other.
        const val NOTIFICATION_ID = 3001
    }
}
