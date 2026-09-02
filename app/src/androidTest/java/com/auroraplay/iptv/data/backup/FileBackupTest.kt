package com.auroraplay.iptv.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraplay.iptv.data.database.AppDatabase
import com.auroraplay.iptv.data.database.entity.ConnectionEntity
import com.auroraplay.iptv.data.database.entity.FavoriteEntity
import com.auroraplay.iptv.data.database.entity.ProfileEntity
import com.auroraplay.iptv.data.datastore.SettingsDataStore
import com.auroraplay.iptv.data.datastore.SecureCredentialStore
import com.auroraplay.iptv.data.repository.SettingsRepositoryImpl
import com.auroraplay.iptv.presentation.settings.CreateBackupDocument
import com.auroraplay.iptv.presentation.settings.OpenBackupDocument
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FileBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var backup: UserDataBackup
    private lateinit var credentials: SecureCredentialStore
    private val credentialIds = listOf("file-connection", "backup-test-conflict", "backup-test-old")
    private val document = Uri.parse("content://com.auroraplay.iptv.backup.test.files/${UUID.randomUUID()}.json")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val store = SettingsDataStore(context)
        credentials = SecureCredentialStore(context)
        credentialIds.forEach(credentials::deletePassword)
        backup = UserDataBackup(context, db, SettingsRepositoryImpl(store, db), store, credentials)
    }

    @After fun tearDown() {
        context.contentResolver.delete(document, null, null)
        credentialIds.forEach(credentials::deletePassword)
        db.close()
    }

    @Test fun selectedDocumentRoundTripExcludesMediaAndPreservesExistingFiles() = runBlocking {
        db.profileDao().upsert(ProfileEntity("file-profile", "João", "#7C5CFF", "A", null, false, 1))
        db.connectionDao().upsert(ConnectionEntity("file-connection", "TV", "https://example.com", "test-user", true))
        credentials.savePassword("file-connection", "test-only-password-ç@/\\")
        db.favoriteDao().insert(FavoriteEntity("movie-42", "MOVIE", "file-profile", 1))
        // A synthetic downloaded file must neither be exported nor touched on restore.
        val media = File(context.getExternalFilesDir(null) ?: context.filesDir, "backup-test-${UUID.randomUUID()}.mp4")
        val mediaBytes = "DOWNLOADED_VIDEO_MUST_STAY_LOCAL".toByteArray()
        media.writeBytes(mediaBytes)
        try {
            backup.saveToDocument(document)
            val json = context.contentResolver.openInputStream(document)!!.bufferedReader().use { it.readText() }
            val root = JsonParser.parseString(json).asJsonObject
            assertEquals("João", root.getAsJsonArray("profiles")[0].asJsonObject["name"].asString)
            assertFalse(root.has("downloads"))
            assertFalse(root.has("files"))
            assertEquals("test-only-password-ç@/\\", root.getAsJsonObject("connectionPasswords")["file-connection"].asString)
            assertFalse(json.contains("DOWNLOADED_VIDEO_MUST_STAY_LOCAL"))
            assertFalse(json.contains(media.name))
            withContext(Dispatchers.IO) { db.clearAllTables() }
            credentials.deletePassword("file-connection")
            backup.restoreFromDocument(document)
            assertEquals("test-only-password-ç@/\\", credentials.getPassword("file-connection"))
            assertEquals("https://example.com", db.connectionDao().getById("file-connection")?.serverUrl)
            assertEquals("test-user", db.connectionDao().getById("file-connection")?.username)
            // Replaying an older backup must not replace a newer local password.
            credentials.savePassword("file-connection", "test-only-newer-password")
            backup.restoreFromDocument(document)
            assertEquals("test-only-newer-password", credentials.getPassword("file-connection"))
            assertEquals("João", db.profileDao().getById("file-profile")?.name)
            assertEquals("OFFLINE", db.connectionDao().getById("file-connection")?.status)
            assertEquals(1, db.favoriteDao().observe("file-profile", null).first().size)
            assertArrayEquals(mediaBytes, media.readBytes())
        } finally { media.delete() }
    }

    @Test fun invalidDocumentDoesNotChangeExistingData() = runBlocking {
        val profile = ProfileEntity("keep", "Keep me", "#7C5CFF", "A", null, false, 1)
        db.profileDao().upsert(profile)
        context.contentResolver.openOutputStream(document, "wt")!!.use { it.write("{not a backup}".toByteArray()) }
        try {
            backup.restoreFromDocument(document)
            fail("Expected invalid backup rejection")
        } catch (_: IllegalArgumentException) { }
        assertEquals(listOf(profile), db.profileDao().observeAll().first())
    }

    @Test fun documentContractsAllowCloudAndExternalStorageProviders() {
        val save = CreateBackupDocument().createIntent(context, "AuroraPlay-backup.json")
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, save.action)
        assertEquals("application/json", save.type)
        assertEquals("AuroraPlay-backup.json", save.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue(save.hasCategory(Intent.CATEGORY_OPENABLE))
        assertFalse(save.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
        val open = OpenBackupDocument().createIntent(context, arrayOf("*/*"))
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, open.action)
        assertTrue(open.hasCategory(Intent.CATEGORY_OPENABLE))
        assertFalse(open.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
        // No package/volume restriction: local, SD, USB and cloud providers may appear.
        assertNull(save.`package`)
        assertNull(save.component)
        assertNull(open.`package`)
    }

    @Test fun passwordIsNotAppliedToDifferentLocalServerOrLogin() = runBlocking {
        db.connectionDao().upsert(ConnectionEntity("backup-test-conflict", "Current", "https://current.example", "current-user"))
        val snapshot = BackupSnapshot(
            savedAt = 123,
            connections = listOf(ConnectionEntity("backup-test-conflict", "Old", "https://old.example", "old-user")),
            connectionPasswords = mapOf("backup-test-conflict" to "test-only-old-password"),
        )
        context.contentResolver.openOutputStream(document, "wt")!!.use { it.write(BackupSnapshotCodec.encode(snapshot).toByteArray()) }
        backup.restoreFromDocument(document)
        assertEquals("https://current.example", db.connectionDao().getById("backup-test-conflict")?.serverUrl)
        assertNull(credentials.getPassword("backup-test-conflict"))
    }

    @Test fun oldFileRestoresWithoutPasswordsAndLaterBackupCanFillThem() = runBlocking {
        val snapshot = BackupSnapshot(savedAt = 123, connections = listOf(ConnectionEntity("backup-test-old", "TV", "https://example.com", "old-user")))
        val old = JsonParser.parseString(BackupSnapshotCodec.encode(snapshot)).asJsonObject.apply {
            addProperty("version", 1)
            remove("connectionPasswords")
        }
        context.contentResolver.openOutputStream(document, "wt")!!.use { it.write(old.toString().toByteArray()) }
        backup.restoreFromDocument(document)
        assertNotNull(db.connectionDao().getById("backup-test-old"))
        assertNull(credentials.getPassword("backup-test-old"))
        val current = snapshot.copy(connectionPasswords = mapOf("backup-test-old" to "test-only-restored-password"))
        context.contentResolver.openOutputStream(document, "wt")!!.use { it.write(BackupSnapshotCodec.encode(current).toByteArray()) }
        backup.restoreFromDocument(document)
        assertEquals("test-only-restored-password", credentials.getPassword("backup-test-old"))
    }
}
