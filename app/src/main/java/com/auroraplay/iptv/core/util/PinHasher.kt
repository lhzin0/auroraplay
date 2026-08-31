package com.auroraplay.iptv.core.util

import java.security.MessageDigest

/**
 * The profile PIN is a "keep the kids from casually hopping into the adult
 * profile" gate, not a real security boundary — no salt, no KDF, just a
 * plain SHA-256 digest so the PIN itself is never stored or logged.
 */
object PinHasher {
    fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun matches(pin: String, hash: String): Boolean = hash(pin) == hash
}
