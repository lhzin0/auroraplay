package com.auroraplay.iptv.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraplay.iptv.data.database.dao.WatchProgressDao
import com.auroraplay.iptv.data.database.entity.WatchProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Histórico DAO behaviour (roadmap item 2 + audit #17): the full history is
 * kept until the user deletes it, per-episode deletion removes exactly one row,
 * removing a series clears its row + every episode, and clearing keeps LIVE
 * "Canais recentes" rows. The title/poster snapshot columns round-trip.
 */
@RunWith(AndroidJUnit4::class)
class WatchHistoryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WatchProgressDao
    private val profile = "p1"
    private val conn = "c1"

    private fun row(
        contentId: String,
        type: String,
        season: Int? = null,
        episode: Int? = null,
        title: String? = null,
        poster: String? = null,
        watchedAt: Long = 1_000L,
    ) = WatchProgressEntity(
        connectionId = conn,
        contentId = contentId,
        type = type,
        profileId = profile,
        positionMillis = 300_000L,
        durationMillis = 1_200_000L,
        seasonNumber = season,
        episodeNumber = episode,
        lastWatchedMillis = watchedAt,
        title = title,
        posterUrl = poster,
    )

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.watchProgressDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun history_lists_movies_and_episodes_newest_first_and_excludes_live() = runBlocking {
        dao.upsert(row("movie-1", "MOVIE", title = "Filme A", poster = "http://p/a.jpg", watchedAt = 10))
        dao.upsert(row("series-1:ep-1", "SERIES", season = 1, episode = 1, title = "Série X", watchedAt = 20))
        dao.upsert(row("series-1:ep-2", "SERIES", season = 1, episode = 2, title = "Série X", watchedAt = 30))
        dao.upsert(row("chan-1", "LIVE", watchedAt = 40))

        val history = dao.observeWatchHistory(profile).first()

        assertEquals(listOf("series-1:ep-2", "series-1:ep-1", "movie-1"), history.map { it.contentId })
        assertEquals("Filme A", history.first { it.contentId == "movie-1" }.title)
        assertEquals("http://p/a.jpg", history.first { it.contentId == "movie-1" }.posterUrl)
    }

    @Test
    fun deleteByKey_removes_only_that_episode() = runBlocking {
        dao.upsert(row("series-1:ep-1", "SERIES", season = 1, episode = 1))
        dao.upsert(row("series-1:ep-2", "SERIES", season = 1, episode = 2))

        dao.deleteByKey(profile, "series-1:ep-1", "SERIES")

        assertEquals(listOf("series-1:ep-2"), dao.observeWatchHistory(profile).first().map { it.contentId })
        // progress row is gone, not just hidden
        assertNull(dao.get(conn, profile, "series-1:ep-1", "SERIES"))
    }

    @Test
    fun deleteSeriesHistory_removes_series_row_and_all_episodes_only() = runBlocking {
        dao.upsert(row("series-1", "SERIES"))
        dao.upsert(row("series-1:ep-1", "SERIES", season = 1, episode = 1))
        dao.upsert(row("series-1:ep-2", "SERIES", season = 1, episode = 2))
        dao.upsert(row("series-10:ep-1", "SERIES", season = 1, episode = 1)) // different series, similar id
        dao.upsert(row("movie-1", "MOVIE"))

        dao.deleteSeriesHistory(profile, "series-1")

        val left = dao.observeWatchHistory(profile).first().map { it.contentId }.toSet()
        assertEquals(setOf("series-10:ep-1", "movie-1"), left)
    }

    @Test
    fun clearWatchHistory_keeps_live_channel_rows() = runBlocking {
        dao.upsert(row("movie-1", "MOVIE"))
        dao.upsert(row("series-1:ep-1", "SERIES", season = 1, episode = 1))
        dao.upsert(row("chan-1", "LIVE"))

        dao.clearWatchHistory(profile)

        assertTrue(dao.observeWatchHistory(profile).first().isEmpty())
        assertEquals(listOf("chan-1"), dao.observeChannelHistory(conn, profile).first().map { it.contentId })
    }
}
