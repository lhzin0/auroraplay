package com.auroraplay.iptv.domain.usecase

import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.Favorite
import com.auroraplay.iptv.domain.model.MediaItem
import com.auroraplay.iptv.domain.model.Movie
import com.auroraplay.iptv.domain.model.Series
import com.auroraplay.iptv.domain.model.WatchProgress
import com.auroraplay.iptv.domain.policy.ContentPolicy
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.FavoriteRepository
import com.auroraplay.iptv.domain.repository.SearchResults
import com.auroraplay.iptv.domain.repository.SyncStage
import com.auroraplay.iptv.domain.repository.WatchProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Assembles Home from local (already-synced) data. Coverage is centred on the
 * one thing that has bitten this use case before (audit #3): an Xtream id is
 * only unique within a *kind*, so a channel and a movie can share the numeric
 * id, and the resolution must never cross-attribute one's progress/favourite
 * to the other.
 */
class GetHomeContentUseCaseTest {

    private fun channel(id: String, category: String = "Geral") = Channel(
        id = id, connectionId = "c", name = "Canal $id", logoUrl = null, categoryId = "g",
        categoryName = category, streamUrl = "u", epgChannelId = null,
    )

    private fun movie(
        id: String,
        category: String = "Geral",
        genre: String? = null,
        plot: String? = null,
        backdropUrl: String? = null,
        addedAtMillis: Long = 0,
    ) = Movie(
        id = id, connectionId = "c", name = "Filme $id", posterUrl = null, backdropUrl = backdropUrl,
        categoryId = "g", categoryName = category, year = null, genre = genre, plot = plot,
        durationLabel = null, rating = null, streamUrl = "u", addedAtMillis = addedAtMillis,
    )

    private fun series(id: String, category: String = "Geral") = Series(
        id = id, connectionId = "c", name = "Série $id", posterUrl = null, backdropUrl = null,
        categoryId = "g", categoryName = category, year = null, genre = null, plot = null, rating = null,
        addedAtMillis = 0,
    )

    private fun progress(contentId: String, type: ContentType, lastWatchedMillis: Long = 1) = WatchProgress(
        connectionId = "c", contentId = contentId, type = type, profileId = "p",
        positionMillis = 10, durationMillis = 100, lastWatchedMillis = lastWatchedMillis,
    )

    private fun useCase(
        channels: List<Channel> = emptyList(),
        movies: List<Movie> = emptyList(),
        series: List<Series> = emptyList(),
        continueWatching: List<WatchProgress> = emptyList(),
        channelHistory: List<WatchProgress> = emptyList(),
        favorites: List<Favorite> = emptyList(),
    ) = GetHomeContentUseCase(
        contentRepository = FakeContentRepository(channels, movies, series),
        favoriteRepository = FakeFavoriteRepository(favorites),
        watchProgressRepository = FakeWatchProgressRepository(continueWatching, channelHistory),
        smartCategoryBuilder = SmartCategoryBuilder(),
        contentPolicy = ContentPolicy(),
    )

    @Test
    fun `continue watching resolves by type, not by id alone`() = runBlocking {
        // A channel and a movie sharing the numeric id "1" — only the movie has progress.
        val content = useCase(
            channels = listOf(channel("1")),
            movies = listOf(movie("1")),
            continueWatching = listOf(progress("1", ContentType.MOVIE)),
        ).invoke("c", "p").first()

        val row = content.sections.single { it.id == "continue_watching" }
        assertEquals(1, row.items.size)
        assertTrue(row.items.single() is MediaItem.MovieItem)
        assertTrue(content.resumeByItemId.containsKey("1"))
    }

    @Test
    fun `series progress resolves to the parent and keeps only the most recent episode`() = runBlocking {
        val content = useCase(
            series = listOf(series("s1")),
            continueWatching = listOf(
                progress("s1:e1", ContentType.SERIES, lastWatchedMillis = 100),
                progress("s1:e2", ContentType.SERIES, lastWatchedMillis = 200),
            ),
        ).invoke("c", "p").first()

        val row = content.sections.single { it.id == "continue_watching" }
        assertEquals(1, row.items.size) // one card per series, not one per episode
        assertEquals("s1:e2", content.resumeByItemId.getValue("s1").contentId) // the newer one
    }

    @Test
    fun `favorites never surface a favourited channel, only movies and series`() = runBlocking {
        val content = useCase(
            channels = listOf(channel("1")),
            movies = listOf(movie("2")),
            favorites = listOf(
                Favorite(connectionId = "c", contentId = "1", type = ContentType.LIVE, profileId = "p"),
                Favorite(connectionId = "c", contentId = "2", type = ContentType.MOVIE, profileId = "p"),
            ),
        ).invoke("c", "p").first()

        val row = content.sections.single { it.id == "favorites" }
        assertEquals(listOf("2"), row.items.map { it.id })
    }

    @Test
    fun `a favourite movie and a same-id favourited channel do not cross-attribute`() = runBlocking {
        // Only the channel "1" is favourited; a movie sharing id "1" is not.
        val content = useCase(
            channels = listOf(channel("1")),
            movies = listOf(movie("1")),
            favorites = listOf(Favorite(connectionId = "c", contentId = "1", type = ContentType.LIVE, profileId = "p")),
        ).invoke("c", "p").first()

        // The channel favourite must not leak the movie into "Minha lista".
        assertTrue(content.sections.none { it.id == "favorites" })
    }

    @Test
    fun `empty sections are dropped entirely`() = runBlocking {
        val content = useCase(movies = listOf(movie("1", addedAtMillis = 1))).invoke("c", "p").first()

        assertTrue(content.sections.none { it.id == "continue_watching" })
        assertTrue(content.sections.none { it.id == "favorites" })
        assertTrue(content.sections.none { it.id == "channels_recent" })
    }

    @Test
    fun `recent channels are deduped, drop history for channels no longer in the catalog, and cap at 10`() = runBlocking {
        val channels = (1..15).map { channel(it.toString()) }
        val history = listOf("1", "1", "2", "3", "999") + (4..15).map { it.toString() } // "1" repeats, "999" is gone from the catalog
        val content = useCase(
            channels = channels,
            channelHistory = history.map { progress(it, ContentType.LIVE) },
        ).invoke("c", "p").first()

        val row = content.sections.single { it.id == "channels_recent" }
        assertEquals(10, row.items.size)
        assertEquals(row.items.map { it.id }, row.items.map { it.id }.distinct()) // no duplicates
        assertTrue(row.items.none { it.id == "999" })
    }

    @Test
    fun `a kids profile never sees adult-only content in any section`() = runBlocking {
        val content = useCase(
            movies = listOf(movie("1", category = "Filmes Adultos", addedAtMillis = 1)),
        ).invoke("c", "p", isKids = true).first()

        assertTrue(content.sections.isEmpty())
        assertTrue(content.heroItems.isEmpty())
    }

    @Test
    fun `hero prefers the continue-watching item when one exists`() = runBlocking {
        val content = useCase(
            movies = listOf(movie("1", addedAtMillis = 1), movie("2", addedAtMillis = 2)),
            continueWatching = listOf(progress("1", ContentType.MOVIE)),
        ).invoke("c", "p").first()

        assertEquals("1", content.heroItems.first().id)
    }
}

private class FakeContentRepository(
    private val channels: List<Channel>,
    private val movies: List<Movie>,
    private val series: List<Series>,
) : ContentRepository {
    override fun syncConnection(connectionId: String): Flow<com.auroraplay.iptv.core.util.Resource<SyncStage>> = unsupported()
    override fun observeCategories(connectionId: String, type: ContentType) = unsupported<Nothing>()
    override fun observeChannels(connectionId: String, categoryId: String?): Flow<List<Channel>> = flowOf(channels)
    override fun observeMovies(connectionId: String, categoryId: String?): Flow<List<Movie>> = flowOf(movies)
    override fun observeSeries(connectionId: String, categoryId: String?): Flow<List<Series>> = flowOf(series)
    override suspend fun getSeriesDetail(connectionId: String, seriesId: String, forceRefresh: Boolean, allowStaleRefresh: Boolean): Series? = unsupported()
    override suspend fun getMovieDetail(connectionId: String, movieId: String): Movie? = unsupported()
    override suspend fun refreshSeriesEpisodes(connectionId: String, seriesId: String): List<String>? = unsupported()
    override suspend fun getCachedMovie(connectionId: String, movieId: String): Movie? = unsupported()
    override suspend fun getCachedSeries(connectionId: String, seriesId: String): Series? = unsupported()
    override suspend fun getLastSyncMillis(connectionId: String): Long? = unsupported()
    override fun search(connectionId: String, query: String): Flow<SearchResults> = unsupported()
    override suspend fun getShortEpg(connectionId: String, channelId: String) = unsupported<Nothing>()
    override suspend fun getEpgTimeline(connectionId: String, channelId: String, limit: Int) = unsupported<Nothing>()
}

private class FakeFavoriteRepository(private val favorites: List<Favorite>) : FavoriteRepository {
    override fun observeFavorites(connectionId: String, profileId: String, type: ContentType?): Flow<List<Favorite>> = flowOf(favorites)
    override fun isFavorite(connectionId: String, profileId: String, contentId: String, type: ContentType) = unsupported<Nothing>()
    override suspend fun toggleFavorite(connectionId: String, profileId: String, contentId: String, type: ContentType) = unsupported<Unit>()
}

private class FakeWatchProgressRepository(
    private val continueWatching: List<WatchProgress>,
    private val channelHistory: List<WatchProgress>,
) : WatchProgressRepository {
    override fun observeContinueWatching(connectionId: String, profileId: String): Flow<List<WatchProgress>> = flowOf(continueWatching)
    override suspend fun getProgress(connectionId: String, profileId: String, contentId: String, type: ContentType) = unsupported<Nothing>()
    override suspend fun getLatestSeriesProgress(connectionId: String, profileId: String, seriesId: String) = unsupported<Nothing>()
    override suspend fun saveProgress(progress: WatchProgress) = unsupported<Unit>()
    override suspend fun removeProgress(connectionId: String, profileId: String, contentId: String, type: ContentType) = unsupported<Unit>()
    override fun observeWatchHistory(profileId: String) = unsupported<Nothing>()
    override suspend fun clearWatchHistory(profileId: String) = unsupported<Unit>()
    override suspend fun deleteHistoryItem(profileId: String, contentId: String, type: ContentType) = unsupported<Unit>()
    override suspend fun deleteSeriesFromHistory(profileId: String, seriesId: String) = unsupported<Unit>()
    override suspend fun removeFromContinueWatching(connectionId: String, profileId: String, contentId: String, isSeries: Boolean) = unsupported<Unit>()
    override fun observeChannelHistory(connectionId: String, profileId: String): Flow<List<WatchProgress>> = flowOf(channelHistory)
    override suspend fun recordChannelWatch(connectionId: String, profileId: String, channelId: String) = unsupported<Unit>()
}

private fun <T> unsupported(): T = throw UnsupportedOperationException("Not needed by GetHomeContentUseCase")
