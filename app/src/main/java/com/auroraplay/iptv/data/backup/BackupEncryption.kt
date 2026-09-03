package com.auroraplay.iptv.data.backup

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupPasswordRequiredException : IllegalArgumentException()
class BackupAuthenticationException : IllegalArgumentException()

/** Portable, authenticated file encryption; never depends on a device key.
 * Format: APBKENC1 (8 bytes), random salt (16), nonce (12), ciphertext and GCM tag (16).
 * Version 1 fixes the KDF parameters, preventing untrusted files from choosing a huge work factor.
 * PBKDF2-HMAC-SHA1 is available on every supported Android version (24+).
 */
internal object BackupEncryption {
    private val magic = "APBKENC1".toByteArray(Charsets.US_ASCII)
    private const val HEADER_SIZE = 36
    private const val ITERATIONS = 1_300_000
    const val MAX_FILE_BYTES = BackupSnapshotCodec.MAX_BYTES + HEADER_SIZE + 16

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        require(plain.size <= BackupSnapshotCodec.MAX_BYTES)
        require(password.size in 8..1024)
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = magic + salt + nonce
        return header + crypt(Cipher.ENCRYPT_MODE, plain, password, header)
    }

    /** Unencrypted legacy JSON passes through for the existing snapshot validation. */
    fun decrypt(file: ByteArray, password: CharArray?): ByteArray {
        require(file.size <= MAX_FILE_BYTES)
        if (!file.take(magic.size).toByteArray().contentEquals(magic)) {
            require(file.size <= BackupSnapshotCodec.MAX_BYTES)
            return file
        }
        require(file.size >= HEADER_SIZE + 16)
        if (password == null || password.isEmpty()) throw BackupPasswordRequiredException()
        require(password.size <= 1024)
        return try {
            crypt(Cipher.DECRYPT_MODE, file.copyOfRange(HEADER_SIZE, file.size), password, file.copyOfRange(0, HEADER_SIZE))
        } catch (_: AEADBadTagException) {
            // Deliberately do not distinguish an incorrect password from a modified file.
            throw BackupAuthenticationException()
        }
    }

    private fun crypt(mode: Int, bytes: ByteArray, password: CharArray, header: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, header.copyOfRange(8, 24), ITERATIONS, 256)
        val key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, header.copyOfRange(24, 36)))
            cipher.updateAAD(header)
            cipher.doFinal(bytes)
        } finally { key.fill(0) }
    }
}
