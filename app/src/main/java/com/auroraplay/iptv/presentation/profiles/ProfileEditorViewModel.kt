package com.auroraplay.iptv.presentation.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auroraplay.iptv.core.security.ProfileGuard
import com.auroraplay.iptv.core.util.PinHasher
import com.auroraplay.iptv.domain.model.Profile
import com.auroraplay.iptv.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@androidx.compose.runtime.Immutable
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
    /** This profile is protected and the user has not yet authenticated for
     * managing it — the screen shows a blocking challenge instead of the form,
     * whatever route reached the editor. */
    val authRequired: Boolean = false,
    /** The loaded profile has a PIN, so its own PIN (or biometric) is the
     * challenge; otherwise (kids-only) it's a device-credential challenge. */
    val authUsesProfilePin: Boolean = false,
    val authError: String? = null,
)

@HiltViewModel
class ProfileEditorViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val profileGuard: ProfileGuard,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileEditorUiState())
    val uiState: StateFlow<ProfileEditorUiState> = _uiState.asStateFlow()

    // Kept out of UI state on purpose: the hash itself is never something a
    // screen needs to render, only whether one exists (hasSavedPin).
    private var loadedPinHash: String? = null
    // The protection state as it was on disk — used to decide whether an edit
    // needs authorization and whether protection is being removed.
    private var loadedLocked = false
    private var loadedIsKids = false
    private var authAttempts = 0

    fun load(profileId: String?) {
        if (profileId == null || profileId == _uiState.value.profileId) return
        viewModelScope.launch {
            val profile = profileRepository.getProfile(profileId) ?: return@launch
            loadedPinHash = profile.pinHash
            loadedLocked = profile.isLocked
            loadedIsKids = profile.isKids
            authAttempts = 0
            val protectedProfile = profile.isLocked || profile.isKids
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
                authRequired = protectedProfile && !profileGuard.isAuthorized(profile.id),
                authUsesProfilePin = profile.isLocked,
            )
        }
    }

    /** Biometric / device-credential challenge succeeded (kids-only profile, or
     * the "use biometric" shortcut for a PIN-locked one). */
    fun onDeviceAuthPassed() {
        val id = _uiState.value.profileId ?: return
        profileGuard.grant(id)
        _uiState.value = _uiState.value.copy(authRequired = false, authError = null)
    }

    /** PIN entered on the editor's own challenge. */
    fun submitAuthPin(pin: String): Boolean {
        val id = _uiState.value.profileId ?: return false
        val hash = loadedPinHash ?: return false
        if (PinHasher.matches(pin, hash)) {
            profileGuard.grant(id)
            authAttempts = 0
            _uiState.value = _uiState.value.copy(authRequired = false, authError = null)
            return true
        }
        authAttempts++
        _uiState.value = _uiState.value.copy(
            authError = if (authAttempts >= MAX_PIN_ATTEMPTS) {
                "Muitas tentativas. Volte e tente novamente mais tarde."
            } else {
                "PIN incorreto."
            },
        )
        return false
    }

    fun authLockedOut(): Boolean = authAttempts >= MAX_PIN_ATTEMPTS

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

        // Defense in depth: editing a profile that was protected on disk — or
        // removing its protection (clearing the PIN, turning off "infantil") —
        // requires an authorization grant for this profile id, no matter which
        // screen or deep link reached the editor. The UI challenge is only the
        // means to obtain that grant.
        if (s.isEditing && s.profileId != null && (loadedLocked || loadedIsKids)) {
            if (!profileGuard.isAuthorized(s.profileId)) {
                _uiState.value = s.copy(
                    authRequired = true,
                    authUsesProfilePin = loadedLocked,
                    authError = "Autenticação necessária para alterar este perfil.",
                )
                return
            }
        }

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
            // A protected profile's management grant is single-use: the next
            // edit/delete re-authenticates.
            if (s.isEditing && s.profileId != null && (loadedLocked || loadedIsKids)) {
                profileGuard.consume(s.profileId)
            }
            onDone()
        }
    }

    private companion object {
        const val MAX_PIN_ATTEMPTS = 5
    }
}
