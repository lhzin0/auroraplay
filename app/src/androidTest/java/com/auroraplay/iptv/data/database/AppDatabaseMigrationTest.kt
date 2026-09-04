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
 * Audit #5: the database no longer has `fallbackToDestructiveMigration`, so a
 * real v7 database must open with the shipped migration list **without losing
 * data**. This also validates that the committed `schemas/7.json` matches the
 * `@Database` entity definitions (MigrationTestHelper.createDatabase enforces
 * that). Future version bumps add a `runMigrationsAndValidate(TEST_DB, N, ...)`
 * case here.
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
        // Create a v7 DB straight from the exported schema and seed it.
        helper.createDatabase(testDb, 7).use { db ->
            db.execSQL(
                "INSERT INTO profiles(id, name, avatarColorHex, avatarEmoji, avatarUri, isKids, createdAtMillis, pinHash, biometricEnabled) " +
                    "VALUES('p1', 'João', '#7C5CFF', 'A', NULL, 0, 1, NULL, 0)",
            )
            db.execSQL(
                "INSERT INTO watch_progress(contentId, type, profileId, positionMillis, durationMillis, seasonNumber, episodeNumber, lastWatchedMillis, hiddenFromContinue, title, posterUrl) " +
                    "VALUES('movie-1', 'MOVIE', 'p1', 300000, 1200000, NULL, NULL, 42, 0, 'Filme A', 'http://p/a.jpg')",
            )
        }

        // Open with the exact runtime configuration (no destructive fallback).
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, testDb)
            .addMigrations(*AppDatabaseMigrations.ALL)
            .build()
        try {
            val profile = db.profileDao().getById("p1")
            assertNotNull(profile)
            assertEquals("João", profile!!.name)

            val progress = db.watchProgressDao().get("p1", "movie-1")
            assertNotNull(progress)
            assertEquals("Filme A", progress!!.title)
            assertEquals(300000L, progress.positionMillis)

            assertEquals(7, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }
}
