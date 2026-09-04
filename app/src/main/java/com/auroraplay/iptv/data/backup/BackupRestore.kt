package com.auroraplay.iptv.data.backup

import androidx.room.withTransaction
import com.auroraplay.iptv.data.database.AppDatabase

/** Atomic, additive and idempotent across retries and device restores. */
internal suspend fun AppDatabase.mergeBackup(snapshot: BackupSnapshot) {
    withTransaction {
        snapshot.profiles.forEach {
            if (profileDao().getById(it.id) == null) {
                profileDao().upsert(it.copy(avatarUri = null, biometricEnabled = false))
            }
        }
        var hasDefault = connectionDao().getDefault() != null
        snapshot.connections.forEach {
            if (connectionDao().getById(it.id) == null) {
                val makeDefault = it.isDefault && !hasDefault
                connectionDao().upsert(it.copy(status = "OFFLINE", lastSyncMillis = null, isDefault = makeDefault))
                hasDefault = hasDefault || makeDefault
            }
        }
        snapshot.favorites.forEach {
            if (favoriteDao().get(it.profileId, it.contentId, it.type) == null) favoriteDao().insert(it)
        }
        snapshot.watchProgress.forEach {
            val local = watchProgressDao().get(it.profileId, it.contentId, it.type)
            if (local == null || it.lastWatchedMillis > local.lastWatchedMillis) watchProgressDao().upsert(it)
        }
    }
}
