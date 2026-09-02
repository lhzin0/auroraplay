package com.auroraplay.iptv.data.repository

import com.auroraplay.iptv.data.database.AppDatabase
import com.auroraplay.iptv.data.datastore.SettingsDataStore
import com.auroraplay.iptv.domain.repository.AppSettings
import com.auroraplay.iptv.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val database: AppDatabase,
) : SettingsRepository {

    override fun observeSettings(): Flow<AppSettings> = settingsDataStore.settingsFlow

    override suspend fun updateAccentColor(hex: String) = settingsDataStore.updateAccentColor(hex)
    override suspend fun updateCardSizeScale(scale: Float) = settingsDataStore.updateCardScale(scale)
    override suspend fun updateAnimationsEnabled(enabled: Boolean) = settingsDataStore.updateAnimationsEnabled(enabled)
    override suspend fun updateAutoPlayNext(enabled: Boolean) = settingsDataStore.updateAutoPlayNext(enabled)
    override suspend fun updatePlaybackQuality(quality: String) = settingsDataStore.updatePlaybackQuality(quality)
    override suspend fun updatePreferredAudioLang(lang: String?) = settingsDataStore.updatePreferredAudioLang(lang)
    override suspend fun updatePreferredSubtitleLang(lang: String?) = settingsDataStore.updatePreferredSubtitleLang(lang)
    override suspend fun updateTmdbApiKey(key: String) = settingsDataStore.updateTmdbApiKey(key)
    override suspend fun updateNotifyNewEpisodes(enabled: Boolean) = settingsDataStore.updateNotifyNewEpisodes(enabled)
    override suspend fun updateDownloadWifiOnly(enabled: Boolean) = settingsDataStore.updateDownloadWifiOnly(enabled)
    override suspend fun updateSeekSeconds(seconds: Int) = settingsDataStore.updateSeekSeconds(seconds)
    override suspend fun updateFrostGlass(enabled: Boolean) = settingsDataStore.updateFrostGlass(enabled)
    override suspend fun updateAutoSyncHours(hours: Int) = settingsDataStore.updateAutoSyncHours(hours)
    override suspend fun updatePipEnabled(enabled: Boolean) = settingsDataStore.updatePipEnabled(enabled)
    override suspend fun updateCinemaMode(enabled: Boolean) = settingsDataStore.updateCinemaMode(enabled)
    override suspend fun restoreFrom(settings: AppSettings) = settingsDataStore.restoreFrom(settings)

    override fun recentSearches(profileId: String): Flow<List<String>> = settingsDataStore.recentSearchesFlow(profileId)
    override suspend fun addRecentSearch(profileId: String, query: String) = settingsDataStore.addRecentSearch(profileId, query)
    override suspend fun clearRecentSearches(profileId: String) = settingsDataStore.clearRecentSearches(profileId)

    override suspend fun clearCache() {
        // Clears cached catalog rows; connections/credentials and profiles are preserved.
        database.clearAllTables()
    }

    override suspend fun restoreDefaults() = settingsDataStore.restoreDefaults()
}
