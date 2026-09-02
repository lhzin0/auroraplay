package com.auroraplay.iptv

import com.auroraplay.iptv.data.database.dao.ConnectionDao
import com.auroraplay.iptv.data.database.dao.ProfileDao
import com.auroraplay.iptv.data.database.entity.ConnectionEntity
import com.auroraplay.iptv.data.database.entity.ProfileEntity
import com.auroraplay.iptv.data.datastore.SecureCredentialStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DEBUG builds only. On a fresh install, pre-loads a fixed Xtream playlist and
 * a throwaway profile so testing doesn't start from the onboarding flow every
 * time.
 *
 * The credentials come from [BuildConfig] fields that are blank in the release
 * build type, so nothing here reaches a release APK; [seedIfEmpty] also bails
 * on blank input, and the call site in [AuroraApplication] is guarded by
 * `BuildConfig.DEBUG`. It never overwrites a connection/profile the user added
 * themselves — it only fills an empty table.
 */
@Singleton
class DebugConnectionSeeder @Inject constructor(
    private val connectionDao: ConnectionDao,
    private val profileDao: ProfileDao,
    private val secureStore: SecureCredentialStore,
) {
    suspend fun seedIfEmpty() {
        val url = BuildConfig.SEED_XTREAM_URL
        if (url.isBlank()) return

        if (connectionDao.getDefault() == null && connectionDao.observeAll().first().isEmpty()) {
            connectionDao.upsert(
                ConnectionEntity(
                    id = SEED_CONNECTION_ID,
                    name = BuildConfig.SEED_XTREAM_NAME.ifBlank { "HubPlay" },
                    serverUrl = url,
                    username = BuildConfig.SEED_XTREAM_USER,
                    isDefault = true,
                    status = "UNKNOWN",
                )
            )
            secureStore.savePassword(SEED_CONNECTION_ID, BuildConfig.SEED_XTREAM_PASS)
        }

        if (profileDao.observeAll().first().isEmpty()) {
            profileDao.upsert(
                ProfileEntity(
                    id = SEED_PROFILE_ID,
                    name = "Debug",
                    avatarColorHex = "#7C5CFF",
                    avatarEmoji = "🧪", // 🧪
                    createdAtMillis = System.currentTimeMillis(),
                )
            )
        }
    }

    private companion object {
        const val SEED_CONNECTION_ID = "debug-seed-hubplay"
        const val SEED_PROFILE_ID = "debug-seed-profile"
    }
}
