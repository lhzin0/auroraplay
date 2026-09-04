package com.auroraplay.iptv.core.security

import com.auroraplay.iptv.domain.model.Profile
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central authorization gate for **managing** a protected profile.
 *
 * A protected profile (has a PIN lock, or is a kids profile) may not be edited,
 * deleted, or have its PIN / kids restriction removed until the user has passed
 * an authentication challenge for that profile. The challenge itself is run by
 * the UI (PIN entry, biometric, or device credential); on success it calls
 * [grant]. Every side-effectful path — the profile editor ViewModel and the
 * delete action — checks [isAuthorized] before proceeding, so a screen that
 * forgets to gate (or a deep link straight into the editor) can't bypass it.
 *
 * Grants are per-profile, short-lived, and single-use for destructive actions.
 */
@Singleton
class ProfileGuard @Inject constructor() {

    private val grants = ConcurrentHashMap<String, Long>()

    /** Does this profile require an auth challenge before edit / delete / unprotect? */
    fun isProtected(profile: Profile): Boolean = profile.isLocked || profile.isKids

    /** Record that the user authenticated for managing [profileId] just now. */
    fun grant(profileId: String) {
        grants[profileId] = System.currentTimeMillis() + TTL_MS
    }

    fun isAuthorized(profileId: String): Boolean {
        val expiresAt = grants[profileId] ?: return false
        if (System.currentTimeMillis() > expiresAt) {
            grants.remove(profileId)
            return false
        }
        return true
    }

    /** Spend the grant — the next management action re-authenticates. */
    fun consume(profileId: String) {
        grants.remove(profileId)
    }

    /** Clears everything (e.g. on sign-out / profile switch). */
    fun clear() = grants.clear()

    private companion object {
        const val TTL_MS = 3 * 60_000L
    }
}
