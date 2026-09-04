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
import com.auroraplay.iptv.data.datastore.hasNewEpisodes
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.FavoriteRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Xtream never marks an episode as "new" — the only signal is a favorited
 * series gaining an episode id it didn't have before. For each connection this
 * worker pulls just the `get_series_info` of the series the user actually
 * favorited (never a whole-catalog sync) and diffs the id set against what
 * [EpisodeCountStore] saved last run.
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
            val favoriteSeriesIds = profiles
                .flatMap { runCatching { favoriteRepository.observeFavorites(connection.id, it.id, ContentType.SERIES).first() }.getOrDefault(emptyList()) }
                .map { it.contentId }
                .toSet()
            if (favoriteSeriesIds.isEmpty()) return@forEach

            // Local, no network — just to label the notification with the show name.
            val nameById = runCatching { contentRepository.observeSeries(connection.id).first() }
                .getOrDefault(emptyList())
                .associate { it.id to it.name }

            favoriteSeriesIds.forEach { seriesId ->
                // One get_series_info per favorited series. null = server
                // unreachable or empty response — skip, keep last known ids,
                // never notify off stale data.
                val currentIds = runCatching { contentRepository.refreshSeriesEpisodes(connection.id, seriesId) }
                    .getOrNull()?.toSet() ?: return@forEach

                val known = episodeCountStore.getKnownEpisodeIds(connection.id, seriesId)
                episodeCountStore.setKnownEpisodeIds(connection.id, seriesId, currentIds)
                if (hasNewEpisodes(currentIds, known)) {
                    newlyAvailable += (nameById[seriesId] ?: "Série")
                }
            }
        }

        if (newlyAvailable.isNotEmpty()) notify(newlyAvailable.distinct())
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
