package com.auroraplay.iptv.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.auroraplay.iptv.BuildConfig
import com.auroraplay.iptv.core.util.Constants
import com.auroraplay.iptv.domain.repository.AppSettings
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = Constants.SETTINGS_DATASTORE)

@Singleton
class SettingsDataStore @Inject constructor(
    private val context: Context,
) {
    private object Keys {
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val CARD_SCALE = floatPreferencesKey("card_scale")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
        val AUDIO_LANG = stringPreferencesKey("audio_lang")
        val SUBTITLE_LANG = stringPreferencesKey("subtitle_lang")
        val PLAYBACK_QUALITY = stringPreferencesKey("playback_quality")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        val NOTIFY_NEW_EPISODES = booleanPreferencesKey("notify_new_episodes")
        val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        val SEEK_SECONDS = intPreferencesKey("seek_seconds")
    }

    val settingsFlow = context.dataStore.data.map { prefs ->
        AppSettings(
            accentColorHex = prefs[Keys.ACCENT_COLOR] ?: "#7C5CFF",
            cardSizeScale = prefs[Keys.CARD_SCALE] ?: 1.0f,
            animationsEnabled = prefs[Keys.ANIMATIONS_ENABLED] ?: true,
            autoPlayNext = prefs[Keys.AUTOPLAY_NEXT] ?: true,
            preferredAudioLang = prefs[Keys.AUDIO_LANG],
            preferredSubtitleLang = prefs[Keys.SUBTITLE_LANG],
            playbackQuality = prefs[Keys.PLAYBACK_QUALITY] ?: "auto",
            tmdbApiKey = BuildConfig.TMDB_API_KEY.takeIf { it.isNotBlank() },
            notifyNewEpisodes = prefs[Keys.NOTIFY_NEW_EPISODES] ?: true,
            downloadWifiOnly = prefs[Keys.DOWNLOAD_WIFI_ONLY] ?: true,
            seekSeconds = prefs[Keys.SEEK_SECONDS]?.takeIf { it == 5 || it == 10 } ?: 10,
        )
    }

    val activeProfileIdFlow = context.dataStore.data.map { it[Keys.ACTIVE_PROFILE_ID] }

    suspend fun setActiveProfileId(id: String) {
        context.dataStore.edit { it[Keys.ACTIVE_PROFILE_ID] = id }
    }

    suspend fun updateAccentColor(hex: String) {
        context.dataStore.edit { it[Keys.ACCENT_COLOR] = hex }
    }

    suspend fun updateCardScale(scale: Float) {
        context.dataStore.edit { it[Keys.CARD_SCALE] = scale }
    }

    suspend fun updateAnimationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANIMATIONS_ENABLED] = enabled }
    }

    suspend fun updateAutoPlayNext(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTOPLAY_NEXT] = enabled }
    }

    suspend fun updatePlaybackQuality(quality: String) {
        context.dataStore.edit { it[Keys.PLAYBACK_QUALITY] = quality }
    }

    suspend fun updatePreferredAudioLang(lang: String?) {
        context.dataStore.edit {
            if (lang == null) it.remove(Keys.AUDIO_LANG) else it[Keys.AUDIO_LANG] = lang
        }
    }

    suspend fun updatePreferredSubtitleLang(lang: String?) {
        context.dataStore.edit {
            if (lang == null) it.remove(Keys.SUBTITLE_LANG) else it[Keys.SUBTITLE_LANG] = lang
        }
    }

    suspend fun updateTmdbApiKey(key: String) {
        context.dataStore.edit { it[Keys.TMDB_API_KEY] = key.trim() }
    }

    suspend fun updateNotifyNewEpisodes(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_NEW_EPISODES] = enabled }
    }

    suspend fun updateDownloadWifiOnly(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DOWNLOAD_WIFI_ONLY] = enabled }
    }

    suspend fun updateSeekSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.SEEK_SECONDS] = if (seconds == 5) 5 else 10 }
    }

    // Recent searches are keyed per profile (each profile browses differently)
    // and stored as one delimited string rather than a stringSetPreferencesKey,
    // because a Set has no defined order — and "most recent first" is the
    // entire point of a search history.
    private fun recentSearchesKey(profileId: String) = stringPreferencesKey("recent_searches_$profileId")

    fun recentSearchesFlow(profileId: String) = context.dataStore.data.map { prefs ->
        prefs[recentSearchesKey(profileId)]?.split(HISTORY_SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun addRecentSearch(profileId: String, query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val key = recentSearchesKey(profileId)
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.split(HISTORY_SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) }).take(10)
            prefs[key] = updated.joinToString(HISTORY_SEPARATOR)
        }
    }

    suspend fun clearRecentSearches(profileId: String) {
        context.dataStore.edit { it.remove(recentSearchesKey(profileId)) }
    }

    suspend fun restoreDefaults() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        // Unit separator: virtually never typed by a person, unlike a comma.
        const val HISTORY_SEPARATOR = "\u001F"
    }
}
