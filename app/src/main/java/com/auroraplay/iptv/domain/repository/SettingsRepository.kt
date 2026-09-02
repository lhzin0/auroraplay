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
    /** Auto-sync the active playlist when the app opens if the last sync is
     * older than this many hours. 0 = off. 12 / 24 / 168 (weekly). */
    val autoSyncHours: Int = 24,
    /** Enter Picture-in-Picture when the user leaves the player (Home button)
     * while a video is playing. */
    val pipEnabled: Boolean = true,
    /** Player "Cinema" ambient glow. Sticky: the in-player button is the only
     * thing that turns it on or off, and it stays that way across episodes,
     * videos and app restarts. */
    val cinemaMode: Boolean = false,
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
    suspend fun updateAutoSyncHours(hours: Int)
    suspend fun updatePipEnabled(enabled: Boolean)
    suspend fun updateCinemaMode(enabled: Boolean)
    /** Bulk-apply a restored settings snapshot (Auto Backup). */
    suspend fun restoreFrom(settings: AppSettings)
    suspend fun clearCache()
    suspend fun restoreDefaults()
    fun recentSearches(profileId: String): Flow<List<String>>
    suspend fun addRecentSearch(profileId: String, query: String)
    suspend fun clearRecentSearches(profileId: String)
}
