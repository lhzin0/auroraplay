package com.auroraplay.iptv.presentation.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.core.util.PinHasher
import com.auroraplay.iptv.domain.model.Profile
import com.auroraplay.iptv.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileEditorUiState(
    val isEditing: Boolean = false,
    val profileId: String? = null,
    val name: String = "",
    val emoji: String = "🎬",
    val colorHex: String = "#7C5CFF",
    val avatarUri: String? = null,
    val isKids: Boolean = false,
    // --- Optional PIN lock — opt-in, only ever touched from this screen ---
    val lockEnabled: Boolean = false,
    val hasSavedPin: Boolean = false,
    val isChangingPin: Boolean = false,
    val newPin: String = "",
    val confirmPin: String = "",
    val pinError: String? = null,
    val biometricEnabled: Boolean = false,
)

@HiltViewModel
class ProfileEditorViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileEditorUiState())
    val uiState: StateFlow<ProfileEditorUiState> = _uiState.asStateFlow()

    // Kept out of UI state on purpose: the hash itself is never something a
    // screen needs to render, only whether one exists (hasSavedPin).
    private var loadedPinHash: String? = null

    fun load(profileId: String?) {
        if (profileId == null || profileId == _uiState.value.profileId) return
        viewModelScope.launch {
            val profile = profileRepository.getProfile(profileId) ?: return@launch
            loadedPinHash = profile.pinHash
            _uiState.value = ProfileEditorUiState(
                isEditing = true,
                profileId = profile.id,
                name = profile.name,
                emoji = profile.avatarEmoji,
                colorHex = profile.avatarColorHex,
                avatarUri = profile.avatarUri,
                isKids = profile.isKids,
                lockEnabled = profile.isLocked,
                hasSavedPin = profile.isLocked,
                biometricEnabled = profile.biometricEnabled,
            )
        }
    }

    fun updateName(name: String) { _uiState.value = _uiState.value.copy(name = name) }
    fun updateEmoji(emoji: String) { _uiState.value = _uiState.value.copy(emoji = emoji) }
    fun updateColor(hex: String) { _uiState.value = _uiState.value.copy(colorHex = hex) }
    fun updateAvatarUri(uri: String?) { _uiState.value = _uiState.value.copy(avatarUri = uri) }
    fun updateIsKids(isKids: Boolean) { _uiState.value = _uiState.value.copy(isKids = isKids) }

    fun toggleLock(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            lockEnabled = enabled,
            pinError = null,
            // Turning the lock off doesn't need digits; turning it on for a
            // profile that never had one does, immediately.
            isChangingPin = if (enabled && !_uiState.value.hasSavedPin) true else _uiState.value.isChangingPin,
            newPin = "",
            confirmPin = "",
            // Biometric is only ever a substitute for typing the PIN — with
            // no PIN to substitute for, leaving this on would be a dangling
            // setting with nothing behind it.
            biometricEnabled = if (!enabled) false else _uiState.value.biometricEnabled,
        )
    }

    fun updateBiometricEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(biometricEnabled = enabled)
    }

    fun requestChangePin() {
        _uiState.value = _uiState.value.copy(isChangingPin = true, newPin = "", confirmPin = "", pinError = null)
    }

    fun updateNewPin(value: String) {
        if (value.length <= 4 && value.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(newPin = value, pinError = null)
        }
    }

    fun updateConfirmPin(value: String) {
        if (value.length <= 4 && value.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(confirmPin = value, pinError = null)
        }
    }

    fun save(onDone: () -> Unit) {
        val s = _uiState.value
        if (s.name.isBlank()) return

        val settingNewPin = s.lockEnabled && (!s.hasSavedPin || s.isChangingPin)
        if (settingNewPin) {
            if (s.newPin.length != 4) {
                _uiState.value = s.copy(pinError = "O PIN precisa ter 4 dígitos.")
                return
            }
            if (s.newPin != s.confirmPin) {
                _uiState.value = s.copy(pinError = "Os PINs não coincidem.")
                return
            }
        }
        val pinHash = when {
            !s.lockEnabled -> null
            settingNewPin -> PinHasher.hash(s.newPin)
            else -> loadedPinHash
        }

        viewModelScope.launch {
            if (s.isEditing && s.profileId != null) {
                profileRepository.updateProfile(
                    Profile(
                        id = s.profileId,
                        name = s.name.trim(),
                        avatarColorHex = s.colorHex,
                        avatarEmoji = s.emoji,
                        avatarUri = s.avatarUri,
                        isKids = s.isKids,
                        pinHash = pinHash,
                        biometricEnabled = s.lockEnabled && s.biometricEnabled,
                    )
                )
            } else {
                val created = profileRepository.addProfile(s.name.trim(), s.colorHex, s.emoji, s.avatarUri, isKids = s.isKids)
                if (pinHash != null) {
                    profileRepository.updateProfile(
                        profileRepository.getProfile(created.id)!!.copy(
                            pinHash = pinHash,
                            biometricEnabled = s.biometricEnabled,
                        )
                    )
                }
                profileRepository.setActiveProfile(created.id)
            }
            onDone()
        }
    }
}
