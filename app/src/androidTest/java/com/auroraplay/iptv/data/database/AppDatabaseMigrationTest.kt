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
            assertEquals(14, db.openHelper.readableDatabase.version)
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

    /**
     * Audit #4: 9 -> 10 drops the credential-bearing `streamUrl` from the
     * catalog tables, keeps every row, and recovers the container extension
     * from the old URL so already-synced content still plays before a re-sync.
     */
    @Test
    fun migrate_9_to_10_drops_stream_url_and_keeps_catalog_rows() {
        helper.createDatabase(testDb, 9).apply {
            execSQL(
                "INSERT INTO channels(id, connectionId, name, logoUrl, categoryId, categoryName, streamUrl, epgChannelId) " +
                    "VALUES('c1', 'conn', 'Canal 1', NULL, 'g', 'Geral', 'http://host:8080/live/user/s3cr3t/c1.m3u8', NULL)",
            )
            execSQL(
                "INSERT INTO movies(id, connectionId, name, posterUrl, backdropUrl, categoryId, categoryName, year, genre, plot, durationLabel, rating, streamUrl, audioLabel, addedAtMillis) " +
                    "VALUES('m1', 'conn', 'Filme', NULL, NULL, 'g', 'Geral', NULL, NULL, NULL, NULL, NULL, 'http://host:8080/movie/user/s3cr3t/m1.mkv', NULL, 10)",
            )
            execSQL(
                "INSERT INTO episodes(id, seriesId, connectionId, seasonNumber, episodeNumber, title, thumbnailUrl, durationLabel, plot, streamUrl) " +
                    "VALUES('e1', 's1', 'conn', 1, 1, 'Ep 1', NULL, NULL, NULL, 'http://host:8080/series/user/s3cr3t/e1.mp4')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 10, true, *AppDatabaseMigrations.ALL)

        // streamUrl is gone from every catalog table.
        for (table in listOf("channels", "movies", "episodes")) {
            db.query("PRAGMA table_info($table)").use { c ->
                val cols = generateSequence { if (c.moveToNext()) c.getString(1) else null }.toList()
                assertEquals("streamUrl still present in $table", false, cols.contains("streamUrl"))
            }
        }

        db.query("SELECT name FROM channels WHERE id = 'c1'").use {
            assertEquals(true, it.moveToFirst()); assertEquals("Canal 1", it.getString(0))
        }
        db.query("SELECT containerExtension FROM movies WHERE id = 'm1'").use {
            assertEquals(true, it.moveToFirst()); assertEquals("mkv", it.getString(0))
        }
        db.query("SELECT containerExtension FROM episodes WHERE id = 'e1'").use {
            assertEquals(true, it.moveToFirst()); assertEquals("mp4", it.getString(0))
        }
        db.close()
    }

    /** Audit #7: 10 -> 11 adds the per-series episode-fetch timestamp,
     * additively, defaulting existing rows to 0 ("never fetched"). */
    @Test
    fun migrate_10_to_11_adds_episode_sync_timestamp_defaulting_to_zero() {
        helper.createDatabase(testDb, 10).apply {
            execSQL(
                "INSERT INTO series(id, connectionId, name, posterUrl, backdropUrl, categoryId, categoryName, year, genre, plot, rating, audioLabel, addedAtMillis) " +
                    "VALUES('s1', 'conn', 'Série', NULL, NULL, 'g', 'Geral', NULL, NULL, NULL, NULL, NULL, 5)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(testDb, 11, true, *AppDatabaseMigrations.ALL)
        db.query("SELECT episodesSyncedAtMillis FROM series WHERE id = 's1'").use {
            assertEquals(true, it.moveToFirst()); assertEquals(0L, it.getLong(0))
        }
        db.close()
    }

    /** Audit #20: 11 -> 12 only adds indexes — every row is preserved and the
     * expected indexes exist afterwards. */
    @Test
    fun migrate_11_to_12_adds_indexes_without_touching_data() {
        helper.createDatabase(testDb, 11).apply {
            execSQL(
                "INSERT INTO movies(id, connectionId, name, posterUrl, backdropUrl, categoryId, categoryName, year, genre, plot, durationLabel, rating, containerExtension, audioLabel, addedAtMillis) " +
                    "VALUES('m1', 'c', 'Filme', NULL, NULL, 'g', 'G', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(testDb, 12, true, *AppDatabaseMigrations.ALL)
        db.query("SELECT name FROM movies WHERE id = 'm1'").use {
            assertEquals(true, it.moveToFirst()); assertEquals("Filme", it.getString(0))
        }
        val indexes = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { c ->
            while (c.moveToNext()) indexes += c.getString(0)
        }
        assertEquals(true, indexes.contains("index_movies_connectionId_categoryId"))
        assertEquals(true, indexes.contains("index_watch_progress_profileId_lastWatchedMillis"))
        db.close()
    }

    /** 12 -> 13 adds the optional backup-server column — additive, existing
     * connections keep working with no backup configured (NULL). */
    @Test
    fun migrate_12_to_13_adds_backup_server_url_defaulting_to_null() {
        helper.createDatabase(testDb, 12).apply {
            execSQL(
                "INSERT INTO connections(id, name, serverUrl, username, isDefault, status, lastSyncMillis, profileId) " +
                    "VALUES('c1', 'Minha lista', 'http://primary.example', 'user', 1, 'ONLINE', NULL, NULL)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(testDb, 13, true, *AppDatabaseMigrations.ALL)
        db.query("SELECT serverUrl, backupServerUrl FROM connections WHERE id = 'c1'").use {
            assertEquals(true, it.moveToFirst())
            assertEquals("http://primary.example", it.getString(0))
            assertEquals(true, it.isNull(1))
        }
        db.execSQL("UPDATE connections SET backupServerUrl = 'http://backup.example' WHERE id = 'c1'")
        db.query("SELECT backupServerUrl FROM connections WHERE id = 'c1'").use {
            assertEquals(true, it.moveToFirst()); assertEquals("http://backup.example", it.getString(0))
        }
        db.close()
    }

    /** 13 -> 14 adds M3U/XMLTV support: `sourceType` defaults every existing
     * (Xtream) connection row to 'XTREAM', `xmltvUrl` and `directStreamUrl`
     * are nullable/additive, and `epg_programs` is a brand new table. */
    @Test
    fun migrate_13_to_14_adds_m3u_and_xmltv_support() {
        helper.createDatabase(testDb, 13).apply {
            execSQL(
                "INSERT INTO connections(id, name, serverUrl, username, isDefault, status, lastSyncMillis, profileId, backupServerUrl) " +
                    "VALUES('c1', 'Minha lista', 'http://primary.example', 'user', 1, 'ONLINE', NULL, NULL, NULL)",
            )
            execSQL(
                "INSERT INTO channels(id, connectionId, name, logoUrl, categoryId, categoryName, epgChannelId) " +
                    "VALUES('ch1', 'c1', 'Canal 1', NULL, 'g', 'Geral', NULL)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(testDb, 14, true, *AppDatabaseMigrations.ALL)

        db.query("SELECT sourceType, xmltvUrl FROM connections WHERE id = 'c1'").use {
            assertEquals(true, it.moveToFirst())
            assertEquals("XTREAM", it.getString(0))
            assertEquals(true, it.isNull(1))
        }
        db.query("SELECT directStreamUrl FROM channels WHERE id = 'ch1'").use {
            assertEquals(true, it.moveToFirst()); assertEquals(true, it.isNull(0))
        }

        db.execSQL(
            "INSERT INTO epg_programs(id, connectionId, epgChannelId, title, description, startMillis, endMillis) " +
                "VALUES('c1:ep1:1000', 'c1', 'ep1', 'Jornal', 'Notícias do dia', 1000, 2000)",
        )
        db.query("SELECT title FROM epg_programs WHERE connectionId = 'c1' AND epgChannelId = 'ep1'").use {
            assertEquals(true, it.moveToFirst()); assertEquals("Jornal", it.getString(0))
        }
        db.close()
    }
}
