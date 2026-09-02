package com.auroraplay.iptv.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraplay.iptv.data.database.AppDatabase
import com.auroraplay.iptv.data.database.entity.ConnectionEntity
import com.auroraplay.iptv.data.database.entity.FavoriteEntity
import com.auroraplay.iptv.data.database.entity.ProfileEntity
import com.auroraplay.iptv.data.database.entity.WatchProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }
    @After fun tearDown() { db.close() }

    private fun profile(id: String, name: String = id) = ProfileEntity(
        id, name, "#7C5CFF", "A", "content://old-device/photo", false, 1, "pin-hash", true,
    )
    private fun progress(id: String, time: Long) = WatchProgressEntity(id, "MOVIE", "local", 500, 1000, null, null, time)

    @Test fun restorePreservesLocalDataAndIsIdempotent() = runBlocking {
        db.profileDao().upsert(profile("local", "Local name"))
        db.connectionDao().upsert(ConnectionEntity("local-connection", "Local TV", "https://local.example", "user", true, "ONLINE"))
        db.watchProgressDao().upsert(progress("newer-locally", 300))
        db.watchProgressDao().upsert(progress("newer-remotely", 100))
        val snapshot = BackupSnapshot(
            savedAt = 123,
            profiles = listOf(profile("local", "Old name"), profile("imported")),
            connections = listOf(
                ConnectionEntity("local-connection", "Old TV", "https://old.example", "old-user", true),
                ConnectionEntity("imported-connection", "Imported", "https://import.example", "user", true, "ONLINE", 100),
            ),
            favorites = listOf(FavoriteEntity("fav", "MOVIE", "imported", 1)),
            watchProgress = listOf(progress("newer-locally", 100), progress("newer-remotely", 400)),
        )
        db.mergeBackup(snapshot)
        db.mergeBackup(snapshot)
        assertEquals(2, db.profileDao().observeAll().first().size)
        assertEquals("Local name", db.profileDao().getById("local")?.name)
        assertEquals("Local TV", db.connectionDao().getById("local-connection")?.name)
        assertEquals("ONLINE", db.connectionDao().getById("local-connection")?.status)
        val restoredProfile = requireNotNull(db.profileDao().getById("imported"))
        assertNull(restoredProfile.avatarUri)
        assertFalse(restoredProfile.biometricEnabled)
        assertEquals("pin-hash", restoredProfile.pinHash)
        val restoredConnection = requireNotNull(db.connectionDao().getById("imported-connection"))
        assertEquals("OFFLINE", restoredConnection.status)
        assertNull(restoredConnection.lastSyncMillis)
        assertFalse(restoredConnection.isDefault)
        assertEquals(1, db.connectionDao().observeAll().first().count { it.isDefault })
        assertEquals(300L, db.watchProgressDao().get("local", "newer-locally")?.lastWatchedMillis)
        assertEquals(400L, db.watchProgressDao().get("local", "newer-remotely")?.lastWatchedMillis)
        assertEquals(1, db.favoriteDao().observe("imported", null).first().size)
    }

    @Test fun freshRestoreKeepsOnlyOneDefaultAndDoesNotImportDeviceLocalFlags() = runBlocking {
        db.mergeBackup(BackupSnapshot(
            savedAt = 123, profiles = listOf(profile("restored")),
            connections = listOf(
                ConnectionEntity("one", "One", "https://one.example", "user", true),
                ConnectionEntity("two", "Two", "https://two.example", "user", true),
            ),
        ))
        assertEquals(1, db.connectionDao().observeAll().first().count { it.isDefault })
        assertFalse(requireNotNull(db.profileDao().getById("restored")).biometricEnabled)
    }
}
