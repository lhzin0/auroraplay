package com.auroraplay.iptv.domain.repository

import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val accentColorHex: String = "#7C5CFF",
    val cardSizeScale: Float = 1.0f,
    val animationsEnabled: Boolean = true,
    val autoPlayNext: Boolean = true,
    val preferredAudioLang: String? = null,
    val preferredSubtitleLang: String? = null,
    val playbackQuality: String = "auto",
    val tmdbApiKey: String? = null,
    val notifyNewEpisodes: Boolean = true,
    val downloadWifiOnly: Boolean = true,
    /** How far the ±10s player buttons / double-tap jump — 10 or 5 seconds. */
    val seekSeconds: Int = 10,
    /** Configurações › Interface. When on, the app's floating translucent
     * panels (glass buttons, bottom nav, the player's ⋮ pop-over) render as a
     * frosted graphite pane instead of a flat wash. Only swaps the material —
     * off restores the exact previous look. */
    val frostGlass: Boolean = true,
)

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun updateAccentColor(hex: String)
    suspend fun updateCardSizeScale(scale: Float)
    suspend fun updateAnimationsEnabled(enabled: Boolean)
    suspend fun updateAutoPlayNext(enabled: Boolean)
    suspend fun updatePlaybackQuality(quality: String)
    suspend fun updatePreferredAudioLang(lang: String?)
    suspend fun updatePreferredSubtitleLang(lang: String?)
    suspend fun updateTmdbApiKey(key: String)
    suspend fun updateNotifyNewEpisodes(enabled: Boolean)
    suspend fun updateDownloadWifiOnly(enabled: Boolean)
    suspend fun updateSeekSeconds(seconds: Int)
    suspend fun updateFrostGlass(enabled: Boolean)
    suspend fun clearCache()
    suspend fun restoreDefaults()
    fun recentSearches(profileId: String): Flow<List<String>>
    suspend fun addRecentSearch(profileId: String, query: String)
    suspend fun clearRecentSearches(profileId: String)
}
