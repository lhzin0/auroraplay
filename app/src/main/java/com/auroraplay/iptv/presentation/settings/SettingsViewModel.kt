package com.auroraplay.iptv.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.domain.repository.AppSettings
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.SettingsRepository
import com.auroraplay.iptv.domain.model.Profile
import com.auroraplay.iptv.domain.repository.ProfileRepository
import com.auroraplay.iptv.player.download.DownloadTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val connectionRepository: ConnectionRepository,
    private val profileRepository: ProfileRepository,
    private val downloadTracker: DownloadTracker,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val activeProfile: StateFlow<Profile?> = profileRepository.observeActiveProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** All profiles, for the quick-switch sheet in the "Perfil" section. */
    val profiles: StateFlow<List<Profile>> = profileRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Switches the active profile in place. Home, favorites and "continuar
     * assistindo" already observe the active profile, so they refresh for the
     * newly-selected one without leaving Configurações. */
    fun switchProfile(id: String) = viewModelScope.launch { profileRepository.setActiveProfile(id) }

    fun setAccentColor(hex: String) = viewModelScope.launch { settingsRepository.updateAccentColor(hex) }
    fun setCardScale(scale: Float) = viewModelScope.launch { settingsRepository.updateCardSizeScale(scale) }
    fun setAnimationsEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateAnimationsEnabled(enabled) }
    fun setAutoPlayNext(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateAutoPlayNext(enabled) }
    fun setPlaybackQuality(quality: String) = viewModelScope.launch { settingsRepository.updatePlaybackQuality(quality) }
    fun setPreferredAudioLang(lang: String?) = viewModelScope.launch { settingsRepository.updatePreferredAudioLang(lang) }
    fun setPreferredSubtitleLang(lang: String?) = viewModelScope.launch { settingsRepository.updatePreferredSubtitleLang(lang) }
    fun setTmdbApiKey(key: String) = viewModelScope.launch { settingsRepository.updateTmdbApiKey(key) }
    fun setNotifyNewEpisodes(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateNotifyNewEpisodes(enabled) }
    fun setDownloadWifiOnly(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.updateDownloadWifiOnly(enabled)
        downloadTracker.applyWifiOnlyPreference(enabled)
    }
    fun setSeekSeconds(seconds: Int) = viewModelScope.launch { settingsRepository.updateSeekSeconds(seconds) }
    fun setFrostGlass(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateFrostGlass(enabled) }
    fun clearCache() = viewModelScope.launch { settingsRepository.clearCache() }
    fun restoreDefaults() = viewModelScope.launch { settingsRepository.restoreDefaults() }
}
