package com.auroraplay.iptv.data.backup

import org.junit.Assert.*
import org.junit.Test

class BackupEncryptionTest {
    private val password = "test-only-🔒-á-long-passphrase".toCharArray()

    @Test fun portableEncryptionHidesCredentialsAndUsesFreshRandomness() {
        val plain = "{\"connectionPasswords\":{\"c\":\"secret-user-password\"}}".toByteArray()
        val first = BackupEncryption.encrypt(plain, password)
        val second = BackupEncryption.encrypt(plain, password)
        assertFalse(first.contentEquals(second))
        assertFalse(first.toString(Charsets.UTF_8).contains("secret-user-password"))
        assertArrayEquals(plain, BackupEncryption.decrypt(first, password))
    }

    @Test fun wrongPasswordTamperingAndTruncationNeverReturnPlaintext() {
        val file = BackupEncryption.encrypt("sensitive data".toByteArray(), password)
        assertThrows(BackupAuthenticationException::class.java) { BackupEncryption.decrypt(file, "wrong-password".toCharArray()) }
        for (position in listOf(8, 24, file.lastIndex)) {
            val damaged = file.copyOf().apply { this[position] = (this[position].toInt() xor 1).toByte() }
            assertThrows(BackupAuthenticationException::class.java) { BackupEncryption.decrypt(damaged, password) }
        }
        assertThrows(BackupAuthenticationException::class.java) { BackupEncryption.decrypt(file.copyOf(file.size - 1), password) }
    }

    @Test fun requiredPasswordAndLegacyFilesRemainDistinct() {
        val file = BackupEncryption.encrypt("test".toByteArray(), password)
        assertThrows(BackupPasswordRequiredException::class.java) { BackupEncryption.decrypt(file, null) }
        val legacy = "{\"version\":2}".toByteArray()
        assertArrayEquals(legacy, BackupEncryption.decrypt(legacy, null))
        assertThrows(IllegalArgumentException::class.java) { BackupEncryption.encrypt(legacy, "short".toCharArray()) }
        assertThrows(IllegalArgumentException::class.java) { BackupEncryption.decrypt(ByteArray(BackupEncryption.MAX_FILE_BYTES + 1), password) }
    }
}
