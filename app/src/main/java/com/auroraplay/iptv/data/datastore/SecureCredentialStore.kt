package com.auroraplay.iptv.data.datastore

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.auroraplay.iptv.core.util.Constants
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores Xtream connection passwords using AES256-GCM via
 * androidx.security EncryptedSharedPreferences, keyed by connection id.
 * Passwords never touch Room, logs, or the domain layer's UI-facing models.
 */
@Singleton
class SecureCredentialStore @Inject constructor(
    context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        Constants.SECURE_PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    @Synchronized
    fun savePassword(connectionId: String, password: String) {
        prefs.edit().putString(connectionId, password).apply()
    }

    fun getPassword(connectionId: String): String? = prefs.getString(connectionId, null)

    /** Persist imported passwords before reporting restoration success. Call on an IO thread. */
    @Synchronized
    fun restorePasswords(passwords: Map<String, String>) {
        if (passwords.isEmpty()) return
        val editor = prefs.edit()
        passwords.forEach { (id, password) -> editor.putString(id, password) }
        if (!editor.commit()) throw IOException("Não foi possível gravar as senhas restauradas.")
    }

    @Synchronized
    fun deletePassword(connectionId: String) {
        prefs.edit().remove(connectionId).apply()
    }
}
