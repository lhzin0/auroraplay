package com.auroraplay.iptv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.notifications.NewEpisodeScheduler
import com.auroraplay.iptv.player.download.DownloadTracker
import com.auroraplay.iptv.domain.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AuroraApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var downloadTracker: DownloadTracker
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var debugConnectionSeeder: DebugConnectionSeeder
    @Inject lateinit var connectionRepository: ConnectionRepository
    @Inject lateinit var contentRepository: ContentRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NewEpisodeScheduler.schedule(this)

        // DEBUG only: seed a test playlist on a fresh install (no-op in
        // release — the BuildConfig.SEED_* fields are blank there).
        if (BuildConfig.DEBUG) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { debugConnectionSeeder.seedIfEmpty() }
            }
        }

        // DownloadManager.requirements resets to its Hilt-provided default
        // every process start, so the persisted Wi-Fi-only preference has to
        // be re-applied here — otherwise it would silently revert to
        // "any network" after the app was killed and relaunched.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val wifiOnly = runCatching { settingsRepository.observeSettings().first().downloadWifiOnly }.getOrDefault(true)
            downloadTracker.applyWifiOnlyPreference(wifiOnly)
        }

        // Auto-sync: on launch, refresh the active playlist if the last sync
        // is older than the interval the user picked (Configurações › Dados).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val hours = settingsRepository.observeSettings().first().autoSyncHours
                if (hours <= 0) return@launch
                val conn = connectionRepository.getDefaultConnection() ?: return@launch
                val last = contentRepository.getLastSyncMillis(conn.id) ?: 0L
                if (System.currentTimeMillis() - last >= hours * 3_600_000L) {
                    contentRepository.syncConnection(conn.id).collect { }
                }
            }
        }
    }
}
