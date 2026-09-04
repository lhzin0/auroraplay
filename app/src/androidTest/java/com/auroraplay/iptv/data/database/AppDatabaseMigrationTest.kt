package com.auroraplay.iptv.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audit #5: no `fallbackToDestructiveMigration`, so a real database opens with
 * the shipped migration list without losing data. Audit #3: `type` (7 -> 8) and
 * then `connectionId` (8 -> 9) enter the primary key of `favorites` /
 * `watch_progress`. `MigrationTestHelper` also validates every committed
 * `schemas/N.json` against the `@Database` entities.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDb = "auroraplay-migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun v7_database_opens_with_shipped_migrations_and_keeps_data() = runBlocking {
        helper.createDatabase(testDb, 7).apply {
            execSQL(
                "INSERT INTO profiles(id, name, avatarColorHex, avatarEmoji, avatarUri, isKids, createdAtMillis, pinHash, biometricEnabled) " +
                    "VALUES('p1', 'João', '#7C5CFF', 'A', NULL, 0, 1, NULL, 0)",
            )
            execSQL(
                "INSERT INTO watch_progress(contentId, type, profileId, positionMillis, durationMillis, seasonNumber, episodeNumber, lastWatchedMillis, hiddenFromContinue, title, posterUrl) " +
                    "VALUES('movie-1', 'MOVIE', 'p1', 300000, 1200000, NULL, NULL, 42, 0, 'Filme A', 'http://p/a.jpg')",
            )
            execSQL("INSERT INTO favorites(contentId, type, profileId, addedAtMillis) VALUES('movie-1', 'MOVIE', 'p1', 9)")
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, testDb)
            .addMigrations(*AppDatabaseMigrations.ALL)
            .build()
        try {
            assertEquals("João", db.profileDao().getById("p1")!!.name)
            // Pre-8/9 rows are backfilled with an empty connectionId (no
            // connections in this test DB) — the data is not lost.
            val progress = db.watchProgressDao().get("", "p1", "movie-1", "MOVIE")
            assertNotNull(progress)
            assertEquals("Filme A", progress!!.title)
            assertEquals(300000L, progress.positionMillis)
            assertEquals(9, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate_7_to_8_lets_same_id_coexist_across_types() {
        helper.createDatabase(testDb, 7).apply {
            execSQL(
                "INSERT INTO watch_progress(contentId, type, profileId, positionMillis, durationMillis, seasonNumber, episodeNumber, lastWatchedMillis, hiddenFromContinue, title, posterUrl) " +
                    "VALUES('500', 'MOVIE', 'p1', 10, 100, NULL, NULL, 5, 0, 'Filme', NULL)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(testDb, 8, true, *AppDatabaseMigrations.ALL)
        db.query("SELECT title FROM watch_progress WHERE contentId = '500' AND type = 'MOVIE' AND profileId = 'p1'").use {
            assertEquals(true, it.moveToFirst()); assertEquals("Filme", it.getString(0))
        }
        db.execSQL(
            "INSERT INTO watch_progress(contentId, type, profileId, positionMillis, durationMillis, seasonNumber, episodeNumber, lastWatchedMillis, hiddenFromContinue, title, posterUrl) " +
                "VALUES('500', 'LIVE', 'p1', 0, 0, NULL, NULL, 7, 0, NULL, NULL)",
        )
        db.query("SELECT COUNT(*) FROM watch_progress WHERE contentId = '500' AND profileId = 'p1'").use {
            it.moveToFirst(); assertEquals(2, it.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrate_8_to_9_backfills_connectionId_and_keeps_rows() {
        helper.createDatabase(testDb, 8).apply {
            execSQL("INSERT INTO favorites(contentId, type, profileId, addedAtMillis) VALUES('500', 'MOVIE', 'p1', 1)")
            execSQL(
                "INSERT INTO watch_progress(contentId, type, profileId, positionMillis, durationMillis, seasonNumber, episodeNumber, lastWatchedMillis, hiddenFromContinue, title, posterUrl) " +
                    "VALUES('500', 'MOVIE', 'p1', 10, 100, NULL, NULL, 5, 0, 'Filme', NULL)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(testDb, 9, true, *AppDatabaseMigrations.ALL)

        db.query("SELECT connectionId, title FROM watch_progress WHERE contentId = '500' AND type = 'MOVIE' AND profileId = 'p1'").use {
            assertEquals(true, it.moveToFirst())
            assertEquals("", it.getString(0)) // no connections seeded -> '' fallback
            assertEquals("Filme", it.getString(1))
        }
        db.query("SELECT connectionId FROM favorites WHERE contentId = '500'").use {
            assertEquals(true, it.moveToFirst()); assertEquals("", it.getString(0))
        }

        // Two playlists can now hold the same id independently.
        db.execSQL(
            "INSERT INTO watch_progress(connectionId, contentId, type, profileId, positionMillis, durationMillis, seasonNumber, episodeNumber, lastWatchedMillis, hiddenFromContinue, title, posterUrl) " +
                "VALUES('conn-B', '500', 'MOVIE', 'p1', 20, 200, NULL, NULL, 9, 0, 'Outro Filme', NULL)",
        )
        db.query("SELECT COUNT(*) FROM watch_progress WHERE contentId = '500' AND type = 'MOVIE' AND profileId = 'p1'").use {
            it.moveToFirst(); assertEquals(2, it.getInt(0))
        }
        db.close()
    }
}
