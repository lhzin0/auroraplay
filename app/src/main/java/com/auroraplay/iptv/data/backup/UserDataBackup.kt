package com.auroraplay.iptv.data.backup

import android.content.Context
import android.util.Log
import com.auroraplay.iptv.data.database.AppDatabase
import com.auroraplay.iptv.data.database.entity.ConnectionEntity
import com.auroraplay.iptv.data.database.entity.FavoriteEntity
import com.auroraplay.iptv.data.database.entity.ProfileEntity
import com.auroraplay.iptv.data.database.entity.WatchProgressEntity
import com.auroraplay.iptv.data.datastore.SettingsDataStore
import com.auroraplay.iptv.domain.repository.AppSettings
import com.auroraplay.iptv.domain.repository.SettingsRepository
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small JSON snapshot of the user's own data — profiles, playlists (metadata
 * only, no passwords), favourites, watch history and settings — written to a
 * single file that Android Auto Backup uploads to the user's Google account.
 *
 * The catalog itself (channels / 40k+ VOD rows) is NOT backed up: it re-syncs
 * from the provider on the new device, and a full DB dump would blow Auto
 * Backup's 25 MB quota. Xtream passwords stay in EncryptedSharedPreferences,
 * which is deliberately excluded from backup (the key doesn't travel), so the
 * user re-enters the playlist password once per device.
 */
@Singleton
class UserDataBackup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val settingsDataStore: SettingsDataStore,
) {
    private val gson = Gson()
    private val file: File get() = File(context.filesDir, "backup/user_data.json")

    @Volatile private var lastExportAt = 0L

    private data class Snapshot(
        val version: Int = 1,
        val savedAt: Long = 0L,
        val activeProfileId: String? = null,
        val profiles: List<ProfileEntity> = emptyList(),
        val connections: List<ConnectionEntity> = emptyList(),
        val favorites: List<FavoriteEntity> = emptyList(),
        val watchProgress: List<WatchProgressEntity> = emptyList(),
        val settings: AppSettings? = null,
    )

    /** Rewrites the snapshot. Debounced — safe to call on every app-to-background. */
    suspend fun export() {
        val now = System.currentTimeMillis()
        if (now - lastExportAt < 20_000L) return
        lastExportAt = now
        runCatching {
            val snap = Snapshot(
                savedAt = now,
                activeProfileId = settingsDataStore.activeProfileIdFlow.first(),
                profiles = db.profileDao().observeAll().first(),
                connections = db.connectionDao().observeAll().first(),
                favorites = collectFavorites(),
                watchProgress = db.watchProgressDao().getAll(),
                // tmdbApiKey comes from BuildConfig — no need to store it.
                settings = settingsRepository.observeSettings().first().copy(tmdbApiKey = null),
            )
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "user_data.json.tmp")
            tmp.writeText(gson.toJson(snap))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }.onFailure { Log.w("UserDataBackup", "export failed", it) }
    }

    /** Favourites across every profile (FavoriteDao only exposes a per-profile query). */
    private suspend fun collectFavorites(): List<FavoriteEntity> =
        db.profileDao().observeAll().first().flatMap { p ->
            db.favoriteDao().observe(p.id, null).first()
        }

    /**
     * If this install has no profiles yet and a snapshot is present (a fresh
     * install on a new device that Auto Backup just restored), load it. Runs
     * once — after it, the profiles table isn't empty so it's a no-op.
     */
    suspend fun restoreIfEmpty() {
        runCatching {
            if (db.profileDao().observeAll().first().isNotEmpty()) return
            if (!file.exists()) return
            val snap = gson.fromJson(file.readText(), Snapshot::class.java) ?: return

            snap.profiles.forEach { db.profileDao().upsert(it) }
            // Restored connections carry no password — the user re-enters it in
            // "Minhas conexões"; mark them OFFLINE so that's obvious.
            snap.connections.forEach { db.connectionDao().upsert(it.copy(status = "OFFLINE", lastSyncMillis = null)) }
            snap.favorites.forEach { db.favoriteDao().insert(it) }
            snap.watchProgress.forEach { db.watchProgressDao().upsert(it) }
            snap.settings?.let { settingsRepository.restoreFrom(it) }
            snap.activeProfileId
                ?.takeIf { id -> snap.profiles.any { it.id == id } }
                ?.let { settingsDataStore.setActiveProfileId(it) }
            Log.i("UserDataBackup", "restored ${snap.profiles.size} profiles, ${snap.watchProgress.size} progress rows")
        }.onFailure { Log.w("UserDataBackup", "restore failed", it) }
    }
}
