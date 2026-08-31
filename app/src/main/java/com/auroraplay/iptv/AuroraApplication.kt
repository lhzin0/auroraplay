package com.auroraplay.iptv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.auroraplay.iptv.notifications.NewEpisodeScheduler
import com.auroraplay.iptv.player.download.DownloadTracker
import com.auroraplay.iptv.domain.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AuroraApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var downloadTracker: DownloadTracker
    @Inject lateinit var settingsRepository: SettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NewEpisodeScheduler.schedule(this)

        // DownloadManager.requirements resets to its Hilt-provided default
        // every process start, so the persisted Wi-Fi-only preference has to
        // be re-applied here — otherwise it would silently revert to
        // "any network" after the app was killed and relaunched.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val wifiOnly = runCatching { settingsRepository.observeSettings().first().downloadWifiOnly }.getOrDefault(true)
            downloadTracker.applyWifiOnlyPreference(wifiOnly)
        }
    }
}
