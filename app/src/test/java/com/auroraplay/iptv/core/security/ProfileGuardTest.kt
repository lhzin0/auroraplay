package com.auroraplay.iptv.core.security

import com.auroraplay.iptv.core.util.PinHasher
import com.auroraplay.iptv.domain.model.Profile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audit #1: the central authorization gate for managing a protected profile. */
class ProfileGuardTest {

    private val guard = ProfileGuard()

    private fun profile(kids: Boolean = false, pin: String? = null) = Profile(
        id = "p1",
        name = "P",
        avatarColorHex = "#7C5CFF",
        avatarEmoji = "A",
        isKids = kids,
        pinHash = pin?.let(PinHasher::hash),
    )

    @Test fun plain_profile_is_not_protected() {
        assertFalse(guard.isProtected(profile()))
    }

    @Test fun kids_profile_is_protected() {
        assertTrue(guard.isProtected(profile(kids = true)))
    }

    @Test fun pin_locked_profile_is_protected() {
        assertTrue(guard.isProtected(profile(pin = "1234")))
    }

    @Test fun no_grant_means_not_authorized() {
        assertFalse(guard.isAuthorized("p1"))
    }

    @Test fun grant_authorizes_until_consumed() {
        guard.grant("p1")
        assertTrue(guard.isAuthorized("p1"))
        guard.consume("p1")
        assertFalse(guard.isAuthorized("p1"))
    }

    @Test fun grant_is_per_profile() {
        guard.grant("p1")
        assertTrue(guard.isAuthorized("p1"))
        assertFalse(guard.isAuthorized("p2"))
    }

    @Test fun clear_revokes_every_grant() {
        guard.grant("p1")
        guard.grant("p2")
        guard.clear()
        assertFalse(guard.isAuthorized("p1"))
        assertFalse(guard.isAuthorized("p2"))
    }
}
