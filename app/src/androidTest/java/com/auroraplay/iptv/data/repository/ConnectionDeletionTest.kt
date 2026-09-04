package com.auroraplay.iptv.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraplay.iptv.data.database.AppDatabase
import com.auroraplay.iptv.data.database.entity.ConnectionEntity
import com.auroraplay.iptv.data.database.entity.FavoriteEntity
import com.auroraplay.iptv.data.database.entity.MovieEntity
import com.auroraplay.iptv.data.database.entity.WatchProgressEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audit #16: deleting the default connection promotes another atomically,
 * drops that connection's re-syncable catalog rows, and never touches its
 * personal data (favourites, watch_progress).
 */
@RunWith(AndroidJUnit4::class)
class ConnectionDeletionTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun conn(id: String, name: String, isDefault: Boolean) =
        ConnectionEntity(id = id, name = name, serverUrl = "http://$id", username = "u", isDefault = isDefault)

    private fun movie(id: String, cid: String) = MovieEntity(
        id = id, connectionId = cid, name = "M", posterUrl = null, backdropUrl = null,
        categoryId = "g", categoryName = "G", year = null, genre = null, plot = null,
        durationLabel = null, rating = null, containerExtension = null, audioLabel = null, addedAtMillis = 0,
    )

    private suspend fun delete(id: String) = deleteConnectionAtomically(
        db, db.connectionDao(), db.categoryDao(), db.channelDao(), db.movieDao(),
        db.seriesDao(), db.episodeDao(), id,
    )

    @Test
    fun deleting_the_default_promotes_another_and_drops_only_its_catalog() = runBlocking {
        db.connectionDao().upsert(conn("a", "Alpha", isDefault = true))
        db.connectionDao().upsert(conn("b", "Bravo", isDefault = false))
        db.movieDao().upsertAll(listOf(movie("m1", "a"), movie("m2", "b")))
        db.favoriteDao().insert(FavoriteEntity("a", "m1", "MOVIE", "p1", 1))
        db.watchProgressDao().upsert(
            WatchProgressEntity("a", "m1", "MOVIE", "p1", 10, 100, null, null, 5, false, "M", null),
        )

        delete("a")

        // Alpha gone, Bravo promoted to default.
        assertNull(db.connectionDao().getById("a"))
        assertEquals("b", db.connectionDao().getDefault()?.id)
        // Alpha's catalog dropped, Bravo's kept.
        assertNull(db.movieDao().getById("a", "m1"))
        assertNotNull(db.movieDao().getById("b", "m2"))
        // Personal data for Alpha is deliberately kept.
        assertNotNull(db.favoriteDao().get("a", "p1", "m1", "MOVIE"))
        assertNotNull(db.watchProgressDao().get("a", "p1", "m1", "MOVIE"))
    }

    @Test
    fun deleting_a_non_default_leaves_the_default_alone() = runBlocking {
        db.connectionDao().upsert(conn("a", "Alpha", isDefault = true))
        db.connectionDao().upsert(conn("b", "Bravo", isDefault = false))

        delete("b")

        assertEquals("a", db.connectionDao().getDefault()?.id)
        assertNull(db.connectionDao().getById("b"))
    }

    @Test
    fun deleting_the_last_connection_leaves_none_and_does_not_crash() = runBlocking {
        db.connectionDao().upsert(conn("a", "Alpha", isDefault = true))

        delete("a")

        assertTrue(db.connectionDao().getAllOnce().isEmpty())
        assertNull(db.connectionDao().getDefault())
    }
}
