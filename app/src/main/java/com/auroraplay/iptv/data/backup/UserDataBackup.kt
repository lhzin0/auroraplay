package com.auroraplay.iptv.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.auroraplay.iptv.data.database.AppDatabase
import com.auroraplay.iptv.data.datastore.SettingsDataStore
import com.auroraplay.iptv.data.datastore.SecureCredentialStore
import com.auroraplay.iptv.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class MissingBackupPasswordException : IllegalStateException("Uma conexão não possui credenciais completas.")

data class BackupRestoreResult(val readyConnectionIds: List<String>, val missingPasswords: Int)

/** Explicit user-data snapshot. Never traverses storage or reads the download database. */
@Singleton
class UserDataBackup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val settingsDataStore: SettingsDataStore,
    private val credentials: SecureCredentialStore,
) {
    private val mutex = Mutex()
    suspend fun saveToDocument(uri: Uri, password: CharArray? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Capture and validate before opening the user-selected destination.
            val plain = capture().toByteArray(Charsets.UTF_8)
            val bytes = try { if (password == null) plain else BackupEncryption.encrypt(plain, password) }
            finally { if (password != null) plain.fill(0) }
            try {
                val stream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("Não foi possível abrir o arquivo para gravação.")
                stream.use { it.write(bytes); it.flush() }
            } finally { bytes.fill(0) }
        }
    }

    suspend fun restoreFromDocument(uri: Uri, password: CharArray? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Não foi possível abrir o backup.")
            val bytes = stream.use { it.readBytesBounded(BackupEncryption.MAX_FILE_BYTES) }
            // Validate the entire file before making any database changes.
            try {
                val plain = BackupEncryption.decrypt(bytes, password)
                try { restore(BackupSnapshotCodec.decode(plain.toString(Charsets.UTF_8))) }
                finally { plain.fill(0) }
            } finally { bytes.fill(0) }
        }
    }

    private suspend fun capture(): String {
        val settings = settingsRepository.observeSettings().first()
        val activeId = settingsDataStore.activeProfileIdFlow.first()
        val snapshot = db.withTransaction {
            val profiles = db.profileDao().observeAll().first()
            val profileIds = profiles.map { it.id }.toSet()
            BackupSnapshot(
                savedAt = System.currentTimeMillis(), activeProfileId = activeId,
                profiles = profiles,
                connections = db.connectionDao().observeAll().first().map {
                    it.copy(profileId = it.profileId?.takeIf { id -> id in profileIds })
                },
                favorites = profiles.flatMap { db.favoriteDao().observe(it.id, null).first() },
                watchProgress = db.watchProgressDao().getAll().filter { it.profileId in profileIds }, settings = settings,
            )
        }
        val passwords = snapshot.connections.map { connection ->
            val password = credentials.getPassword(connection.id) ?: throw MissingBackupPasswordException()
            connection.id to password
        }.toMap()
        return BackupSnapshotCodec.encode(snapshot.copy(connectionPasswords = passwords))
    }

    /** Merge without deleting local records; keep newer local playback positions. */
    private suspend fun restore(snapshot: BackupSnapshot): BackupRestoreResult {
        db.mergeBackup(snapshot)
        // Never pair an imported password with a different local server or login.
        // Explicit restore applies the file's password to the matching connection.
        val matchingPasswords = snapshot.connections.mapNotNull { connection ->
            val local = db.connectionDao().getById(connection.id)
            val password = snapshot.connectionPasswords[connection.id]
            if (password != null && local != null && local.serverUrl == connection.serverUrl && local.username == connection.username) {
                connection.id to password
            } else null
        }.toMap()
        credentials.restorePasswords(matchingPasswords)
        // Room and DataStore are separate stores. The merge is idempotent so an
        // interrupted restore can safely be retried without deleting local data.
        snapshot.settings?.let { settingsRepository.restoreFrom(it) }
        if (settingsDataStore.activeProfileIdFlow.first() == null) {
            snapshot.activeProfileId?.takeIf { id -> snapshot.profiles.any { it.id == id } }
                ?.let { settingsDataStore.setActiveProfileId(it) }
        }
        val matchingConnections = snapshot.connections.filter { connection ->
            val local = db.connectionDao().getById(connection.id)
            local?.serverUrl == connection.serverUrl && local.username == connection.username
        }
        val ready = matchingConnections.filter { credentials.getPassword(it.id) != null }.map { it.id }
        return BackupRestoreResult(ready, matchingConnections.size - ready.size)
    }
}

internal fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size().toLong() + count <= limit) { "Backup maior que 20 MB." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
