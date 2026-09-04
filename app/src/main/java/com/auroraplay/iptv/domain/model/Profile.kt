package com.auroraplay.iptv.domain.model

@androidx.compose.runtime.Immutable
data class Profile(
    val id: String,
    val name: String,
    val avatarColorHex: String,
    val avatarEmoji: String = "🎬",
    val avatarUri: String? = null,
    val isKids: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    /** SHA-256 hex digest of the PIN, never the PIN itself. Null means the
     * profile has no lock — this is an opt-in "keep the kids from casually
     * hopping into the adult profile" gate, not a real security boundary. */
    val pinHash: String? = null,
    /** Fingerprint/face unlock as an *alternative* to typing the PIN — it
     * never stands alone: a profile with no PIN has nothing for biometrics
     * to substitute for, so this only has any effect when [isLocked] is true. */
    val biometricEnabled: Boolean = false,
) {
    val isLocked: Boolean get() = pinHash != null
}
