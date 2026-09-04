package com.auroraplay.iptv.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every explicit Room migration for [AppDatabase], from version 1 to the
 * current version. Kept out of the DI module so the instrumented migration
 * tests can use the exact same list the app ships with.
 *
 * There is **no** `fallbackToDestructiveMigration` any more (see
 * `DatabaseModule`): a missing/failed migration must fail loudly, never wipe
 * the user's profiles, favourites, watch history or downloads index. Every
 * version bump has to add its migration here.
 */
object AppDatabaseMigrations {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN avatarUri TEXT")
        }
    }

    // Catches up two columns that were added straight to ProfileEntity without
    // ever bumping the DB version — Room validates a schema hash at startup
    // independent of the version number, so any install that already had a
    // version-2 database (isKids added but never migrated) would fail that
    // check and crash on open.
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN isKids INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE profiles ADD COLUMN pinHash TEXT")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN biometricEnabled INTEGER NOT NULL DEFAULT 0")
        }
    }

    // "Legendado" tag computed at sync from the raw provider name/category.
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE movies ADD COLUMN audioLabel TEXT")
            db.execSQL("ALTER TABLE series ADD COLUMN audioLabel TEXT")
        }
    }

    // "Remover de Continuar assistindo": a per-row flag on watch_progress.
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE watch_progress ADD COLUMN hiddenFromContinue INTEGER NOT NULL DEFAULT 0")
        }
    }

    // Histórico offline: snapshot of title + poster so the list survives a
    // title leaving the catalog. Nullable, additive.
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE watch_progress ADD COLUMN title TEXT")
            db.execSQL("ALTER TABLE watch_progress ADD COLUMN posterUrl TEXT")
        }
    }

    // Audit #3: put `type` in the primary key of favourites and watch_progress
    // so a channel and a movie that share an Xtream numeric id no longer
    // collide. SQLite can't ALTER a primary key, so each table is rebuilt.
    // Existing data is copied 1:1 (the old key already allowed only one row per
    // contentId+profileId, so nothing needs de-duplicating).
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `favorites_new` (`contentId` TEXT NOT NULL, `type` TEXT NOT NULL, `profileId` TEXT NOT NULL, `addedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`contentId`, `type`, `profileId`))"
            )
            db.execSQL(
                "INSERT INTO `favorites_new` (`contentId`, `type`, `profileId`, `addedAtMillis`) SELECT `contentId`, `type`, `profileId`, `addedAtMillis` FROM `favorites`"
            )
            db.execSQL("DROP TABLE `favorites`")
            db.execSQL("ALTER TABLE `favorites_new` RENAME TO `favorites`")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `watch_progress_new` (`contentId` TEXT NOT NULL, `type` TEXT NOT NULL, `profileId` TEXT NOT NULL, `positionMillis` INTEGER NOT NULL, `durationMillis` INTEGER NOT NULL, `seasonNumber` INTEGER, `episodeNumber` INTEGER, `lastWatchedMillis` INTEGER NOT NULL, `hiddenFromContinue` INTEGER NOT NULL, `title` TEXT, `posterUrl` TEXT, PRIMARY KEY(`contentId`, `type`, `profileId`))"
            )
            db.execSQL(
                "INSERT INTO `watch_progress_new` (`contentId`, `type`, `profileId`, `positionMillis`, `durationMillis`, `seasonNumber`, `episodeNumber`, `lastWatchedMillis`, `hiddenFromContinue`, `title`, `posterUrl`) " +
                    "SELECT `contentId`, `type`, `profileId`, `positionMillis`, `durationMillis`, `seasonNumber`, `episodeNumber`, `lastWatchedMillis`, `hiddenFromContinue`, `title`, `posterUrl` FROM `watch_progress`"
            )
            db.execSQL("DROP TABLE `watch_progress`")
            db.execSQL("ALTER TABLE `watch_progress_new` RENAME TO `watch_progress`")
        }
    }

    // Audit #3b: add `connectionId` to the identity of favourites and
    // watch_progress so two playlists that reuse an Xtream numeric id for
    // different titles don't bleed progress/favourites into each other. Table
    // rebuild again (PK change). Existing rows are backfilled with the current
    // default connection — the best available guess for data written before
    // this column existed; a wrong guess only means a resume position not
    // matching, never data loss.
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val defaultConn = db.query("SELECT `id` FROM `connections` WHERE `isDefault` = 1 LIMIT 1").use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: db.query("SELECT `id` FROM `connections` LIMIT 1").use { c ->
                if (c.moveToFirst()) c.getString(0) else ""
            }

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `favorites_new` (`connectionId` TEXT NOT NULL, `contentId` TEXT NOT NULL, `type` TEXT NOT NULL, `profileId` TEXT NOT NULL, `addedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`connectionId`, `contentId`, `type`, `profileId`))"
            )
            db.execSQL(
                "INSERT INTO `favorites_new` (`connectionId`, `contentId`, `type`, `profileId`, `addedAtMillis`) SELECT ?, `contentId`, `type`, `profileId`, `addedAtMillis` FROM `favorites`",
                arrayOf<Any?>(defaultConn),
            )
            db.execSQL("DROP TABLE `favorites`")
            db.execSQL("ALTER TABLE `favorites_new` RENAME TO `favorites`")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `watch_progress_new` (`connectionId` TEXT NOT NULL, `contentId` TEXT NOT NULL, `type` TEXT NOT NULL, `profileId` TEXT NOT NULL, `positionMillis` INTEGER NOT NULL, `durationMillis` INTEGER NOT NULL, `seasonNumber` INTEGER, `episodeNumber` INTEGER, `lastWatchedMillis` INTEGER NOT NULL, `hiddenFromContinue` INTEGER NOT NULL, `title` TEXT, `posterUrl` TEXT, PRIMARY KEY(`connectionId`, `contentId`, `type`, `profileId`))"
            )
            db.execSQL(
                "INSERT INTO `watch_progress_new` (`connectionId`, `contentId`, `type`, `profileId`, `positionMillis`, `durationMillis`, `seasonNumber`, `episodeNumber`, `lastWatchedMillis`, `hiddenFromContinue`, `title`, `posterUrl`) " +
                    "SELECT ?, `contentId`, `type`, `profileId`, `positionMillis`, `durationMillis`, `seasonNumber`, `episodeNumber`, `lastWatchedMillis`, `hiddenFromContinue`, `title`, `posterUrl` FROM `watch_progress`",
                arrayOf<Any?>(defaultConn),
            )
            db.execSQL("DROP TABLE `watch_progress`")
            db.execSQL("ALTER TABLE `watch_progress_new` RENAME TO `watch_progress`")
        }
    }

    // Audit #4: stop persisting the authenticated playback URL (it embeds the
    // Xtream username + password) in the catalog. `streamUrl` is dropped from
    // `channels`, `movies` and `episodes`; movies/episodes gain a
    // `containerExtension` so the URL can still be rebuilt on demand. SQLite
    // can't DROP a column portably on every supported API level, so each table
    // is rebuilt. The extension is recovered from the old URL's suffix where
    // possible so already-synced content still plays before the next sync;
    // an unrecognised suffix becomes NULL and falls back to "mp4".
    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // channels: drop streamUrl only.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `channels_new` (`id` TEXT NOT NULL, `connectionId` TEXT NOT NULL, `name` TEXT NOT NULL, `logoUrl` TEXT, `categoryId` TEXT NOT NULL, `categoryName` TEXT NOT NULL, `epgChannelId` TEXT, PRIMARY KEY(`id`, `connectionId`))"
            )
            db.execSQL(
                "INSERT INTO `channels_new` (`id`, `connectionId`, `name`, `logoUrl`, `categoryId`, `categoryName`, `epgChannelId`) " +
                    "SELECT `id`, `connectionId`, `name`, `logoUrl`, `categoryId`, `categoryName`, `epgChannelId` FROM `channels`"
            )
            db.execSQL("DROP TABLE `channels`")
            db.execSQL("ALTER TABLE `channels_new` RENAME TO `channels`")

            // movies: drop streamUrl, add containerExtension (recovered from the
            // old URL suffix).
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `movies_new` (`id` TEXT NOT NULL, `connectionId` TEXT NOT NULL, `name` TEXT NOT NULL, `posterUrl` TEXT, `backdropUrl` TEXT, `categoryId` TEXT NOT NULL, `categoryName` TEXT NOT NULL, `year` TEXT, `genre` TEXT, `plot` TEXT, `durationLabel` TEXT, `rating` REAL, `containerExtension` TEXT, `audioLabel` TEXT, `addedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`, `connectionId`))"
            )
            db.execSQL(
                "INSERT INTO `movies_new` (`id`, `connectionId`, `name`, `posterUrl`, `backdropUrl`, `categoryId`, `categoryName`, `year`, `genre`, `plot`, `durationLabel`, `rating`, `containerExtension`, `audioLabel`, `addedAtMillis`) " +
                    "SELECT `id`, `connectionId`, `name`, `posterUrl`, `backdropUrl`, `categoryId`, `categoryName`, `year`, `genre`, `plot`, `durationLabel`, `rating`, " +
                    EXTENSION_FROM_URL + ", `audioLabel`, `addedAtMillis` FROM `movies`"
            )
            db.execSQL("DROP TABLE `movies`")
            db.execSQL("ALTER TABLE `movies_new` RENAME TO `movies`")

            // episodes: drop streamUrl, add containerExtension.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `episodes_new` (`id` TEXT NOT NULL, `seriesId` TEXT NOT NULL, `connectionId` TEXT NOT NULL, `seasonNumber` INTEGER NOT NULL, `episodeNumber` INTEGER NOT NULL, `title` TEXT NOT NULL, `thumbnailUrl` TEXT, `durationLabel` TEXT, `plot` TEXT, `containerExtension` TEXT, PRIMARY KEY(`id`, `seriesId`, `connectionId`))"
            )
            db.execSQL(
                "INSERT INTO `episodes_new` (`id`, `seriesId`, `connectionId`, `seasonNumber`, `episodeNumber`, `title`, `thumbnailUrl`, `durationLabel`, `plot`, `containerExtension`) " +
                    "SELECT `id`, `seriesId`, `connectionId`, `seasonNumber`, `episodeNumber`, `title`, `thumbnailUrl`, `durationLabel`, `plot`, " +
                    EXTENSION_FROM_URL + " FROM `episodes`"
            )
            db.execSQL("DROP TABLE `episodes`")
            db.execSQL("ALTER TABLE `episodes_new` RENAME TO `episodes`")
        }
    }

    /** SQLite CASE that maps a stored playback URL to its container extension.
     * Xtream containers are a small known set; anything else → NULL ("mp4"). */
    private const val EXTENSION_FROM_URL =
        "CASE " +
            "WHEN `streamUrl` LIKE '%.mkv' THEN 'mkv' " +
            "WHEN `streamUrl` LIKE '%.mp4' THEN 'mp4' " +
            "WHEN `streamUrl` LIKE '%.avi' THEN 'avi' " +
            "WHEN `streamUrl` LIKE '%.ts' THEN 'ts' " +
            "WHEN `streamUrl` LIKE '%.m3u8' THEN 'm3u8' " +
            "WHEN `streamUrl` LIKE '%.mov' THEN 'mov' " +
            "WHEN `streamUrl` LIKE '%.flv' THEN 'flv' " +
            "ELSE NULL END"

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
    )
}
