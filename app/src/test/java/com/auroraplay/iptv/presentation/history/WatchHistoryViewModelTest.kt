package com.auroraplay.iptv.presentation.history

import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.Category
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.EpgProgram
import com.auroraplay.iptv.domain.model.Movie
import com.auroraplay.iptv.domain.model.Profile
import com.auroraplay.iptv.domain.model.Series
import com.auroraplay.iptv.domain.model.WatchProgress
import com.auroraplay.iptv.domain.model.XtreamConnection
import com.auroraplay.iptv.domain.policy.ContentPolicy
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import com.auroraplay.iptv.domain.repository.SearchResults
import com.auroraplay.iptv.domain.repository.SyncStage
import com.auroraplay.iptv.domain.repository.WatchProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchHistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun profile(isKids: Boolean = false) = Profile(id = "p", name = "P", avatarColorHex = "#000", isKids = isKids)
    private fun connection(id: String = "a") = XtreamConnection(id = id, name = id, serverUrl = "http://$id", username = "u")

    private fun wp(
        contentId: String,
        type: ContentType,
        title: String? = "Snapshot",
        posterUrl: String? = "snapshot.jpg",
        season: Int? = null,
        episode: Int? = null,
        lastWatchedMillis: Long = 1,
    ) = WatchProgress(
        connectionId = "a", contentId = contentId, type = type, profileId = "p",
        positionMillis = 10, durationMillis = 100, seasonNumber = season, episodeNumber = episode,
        lastWatchedMillis = lastWatchedMillis, title = title, posterUrl = posterUrl,
    )

    private fun movie(id: String, category: String = "g") = Movie(
        id = id, connectionId = "a", name = "Filme $id", posterUrl = "catalog-$id.jpg", backdropUrl = null,
        categoryId = category, categoryName = category, year = null, genre = null, plot = null,
        durationLabel = null, rating = null, streamUrl = "u",
    )

    private fun series(id: String, category: String = "g") = Series(
        id = id, connectionId = "a", name = "Série $id", posterUrl = "catalog-$id.jpg", backdropUrl = null,
        categoryId = category, categoryName = category, year = null, genre = null, plot = null, rating = null,
    )

    private fun viewModel(
        connectionFlow: MutableStateFlow<XtreamConnection?> = MutableStateFlow(connection()),
        profileFlow: MutableStateFlow<Profile?> = MutableStateFlow(profile()),
        movies: List<Movie> = emptyList(),
        series: List<Series> = emptyList(),
        history: List<WatchProgress> = emptyList(),
        watchProgressRepository: WatchProgressRepository = RecordingWatchProgressRepository(history),
    ) = WatchHistoryViewModel(
        connectionRepository = FakeConnectionRepository(connectionFlow),
        contentRepository = FakeContentRepository(movies, series),
        profileRepository = FakeProfileRepository(profileFlow),
        watchProgressRepository = watchProgressRepository,
        contentPolicy = ContentPolicy(),
    )

    @Test
    fun `a movie still in the catalog resolves title, poster and is marked available`() = runTest {
        val vm = viewModel(movies = listOf(movie("1")), history = listOf(wp("1", ContentType.MOVIE)))
        dispatcher.scheduler.advanceUntilIdle()

        val entry = vm.uiState.value.entries.single()
        assertEquals("Filme 1", entry.title)
        assertEquals("catalog-1.jpg", entry.posterUrl)
        assertTrue(entry.available)
    }

    @Test
    fun `content no longer in the catalog falls back to the stored snapshot and is unavailable`() = runTest {
        val vm = viewModel(movies = emptyList(), history = listOf(wp("gone", ContentType.MOVIE, title = "Título salvo", posterUrl = "salvo.jpg")))
        dispatcher.scheduler.advanceUntilIdle()

        val entry = vm.uiState.value.entries.single()
        assertEquals("Título salvo", entry.title)
        assertEquals("salvo.jpg", entry.posterUrl)
        assertFalse(entry.available)
    }

    @Test
    fun `series episodes collapse into one entry, headlined by whichever comes first`() = runTest {
        // observeWatchHistory's real contract is newest-first, so the DAO
        // would hand this in with e2 (the more recent watch) ahead of e1;
        // buildEntries trusts that order rather than re-sorting itself.
        val vm = viewModel(
            series = listOf(series("s1")),
            history = listOf(
                wp("s1:e2", ContentType.SERIES, season = 1, episode = 2, lastWatchedMillis = 200),
                wp("s1:e1", ContentType.SERIES, season = 1, episode = 1, lastWatchedMillis = 100),
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        val entries = vm.uiState.value.entries
        assertEquals(1, entries.size) // one card, not one per episode
        val entry = entries.single()
        assertEquals(ContentType.SERIES, entry.type)
        assertEquals("T1 E2", entry.episodeLabel) // headlined by the first (= most recent) row
        assertEquals(2, entry.episodes.size)
    }

    @Test
    fun `a kids profile hides history it is not allowed to watch, catalog-backed or snapshot-only`() = runTest {
        val vm = viewModel(
            profileFlow = MutableStateFlow(profile(isKids = true)),
            movies = listOf(movie("1", category = "Filmes Adultos")),
            history = listOf(
                wp("1", ContentType.MOVIE), // catalog-backed, adult category -> hidden
                wp("gone", ContentType.MOVIE, title = "Sexo Explícito"), // snapshot-only, adult title -> hidden
                wp("2", ContentType.MOVIE, title = "Filme Normal"), // snapshot-only, nothing adult-looking -> kept
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("2"), vm.uiState.value.entries.map { it.id })
    }

    @Test
    fun `no active profile yields an empty, non-loading state`() = runTest {
        val vm = viewModel(profileFlow = MutableStateFlow(null), history = listOf(wp("1", ContentType.MOVIE)))
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertTrue(state.entries.isEmpty())
    }

    @Test
    fun `switching connection re-resolves catalog availability without recreating the screen`() = runTest {
        val connectionFlow = MutableStateFlow<XtreamConnection?>(connection("a"))
        // Movie "1" only exists in connection "a"'s in-memory catalog fake, so
        // this test doubles as a stand-in for a real per-connection catalog.
        val vm = WatchHistoryViewModel(
            connectionRepository = FakeConnectionRepository(connectionFlow),
            contentRepository = SwitchingFakeContentRepository(),
            profileRepository = FakeProfileRepository(MutableStateFlow(profile())),
            watchProgressRepository = RecordingWatchProgressRepository(listOf(wp("1", ContentType.MOVIE))),
            contentPolicy = ContentPolicy(),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.entries.single().available)

        connectionFlow.value = connection("b") // "b" has no movies at all
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.entries.single().available)
    }

    @Test
    fun `deleteEntry removes a whole series but only one movie`() = runTest {
        val repo = RecordingWatchProgressRepository(emptyList())
        val vm = viewModel(watchProgressRepository = repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.deleteEntry(HistoryEntry("m1", ContentType.MOVIE, "M", null, 0f, 0, true, null))
        vm.deleteEntry(HistoryEntry("s1", ContentType.SERIES, "S", null, 0f, 0, true, null))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("p" to "m1"), repo.deletedItems)
        assertEquals(listOf("p" to "s1"), repo.deletedSeries)
    }

    @Test
    fun `clearHistory clears for the active profile`() = runTest {
        val repo = RecordingWatchProgressRepository(emptyList())
        val vm = viewModel(watchProgressRepository = repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.clearHistory()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("p"), repo.cleared)
    }
}

private class FakeConnectionRepository(private val flow: MutableStateFlow<XtreamConnection?>) : ConnectionRepository {
    override fun observeConnections(): Flow<List<XtreamConnection>> = unsupported()
    override suspend fun getConnection(id: String): XtreamConnection? = unsupported()
    override suspend fun getDefaultConnection(): XtreamConnection? = unsupported()
    override fun observeDefaultConnection(): Flow<XtreamConnection?> = flow
    override fun addConnection(name: String, serverUrl: String, username: String, password: String, profileId: String?, backupServerUrl: String?, sourceType: String, xmltvUrl: String?): Flow<Resource<XtreamConnection>> = unsupported()
    override suspend fun updateConnection(connection: XtreamConnection, newPassword: String?) = unsupported<Unit>()
    override suspend fun deleteConnection(id: String) = unsupported<Unit>()
    override suspend fun setDefault(id: String) = unsupported<Unit>()
    override fun testConnection(id: String): Flow<Resource<Unit>> = unsupported()
    override suspend fun getPassword(id: String): String? = unsupported()
}

private class FakeProfileRepository(private val flow: MutableStateFlow<Profile?>) : ProfileRepository {
    override fun observeProfiles(): Flow<List<Profile>> = unsupported()
    override suspend fun addProfile(name: String, avatarColorHex: String, avatarEmoji: String, avatarUri: String?, isKids: Boolean): Profile = unsupported()
    override suspend fun updateProfile(profile: Profile) = unsupported<Unit>()
    override suspend fun getProfile(id: String): Profile? = unsupported()
    override suspend fun deleteProfile(id: String) = unsupported<Unit>()
    override suspend fun getActiveProfileId(): String? = unsupported()
    override suspend fun setActiveProfile(id: String) = unsupported<Unit>()
    override fun observeActiveProfile(): Flow<Profile?> = flow
}

private open class FakeContentRepository(
    private val movies: List<Movie>,
    private val series: List<Series>,
) : ContentRepository {
    override fun syncConnection(connectionId: String) = unsupported<Flow<Resource<SyncStage>>>()
    override fun observeCategories(connectionId: String, type: ContentType) = unsupported<Flow<List<Category>>>()
    override fun observeChannels(connectionId: String, categoryId: String?) = unsupported<Flow<List<Channel>>>()
    override fun observeMovies(connectionId: String, categoryId: String?): Flow<List<Movie>> = flowOf(movies)
    override fun observeSeries(connectionId: String, categoryId: String?): Flow<List<Series>> = flowOf(series)
    override suspend fun getSeriesDetail(connectionId: String, seriesId: String, forceRefresh: Boolean, allowStaleRefresh: Boolean): Series? = unsupported()
    override suspend fun getMovieDetail(connectionId: String, movieId: String): Movie? = unsupported()
    override suspend fun refreshSeriesEpisodes(connectionId: String, seriesId: String): List<String>? = unsupported()
    override suspend fun getCachedMovie(connectionId: String, movieId: String): Movie? = unsupported()
    override suspend fun getCachedSeries(connectionId: String, seriesId: String): Series? = unsupported()
    override suspend fun getLastSyncMillis(connectionId: String): Long? = unsupported()
    override fun search(connectionId: String, query: String): Flow<SearchResults> = unsupported()
    override suspend fun getShortEpg(connectionId: String, channelId: String): Pair<EpgProgram?, EpgProgram?> = unsupported()
    override suspend fun getEpgTimeline(connectionId: String, channelId: String, limit: Int): List<EpgProgram> = unsupported()
}

/** Movie "1" only exists under connection "a"; every other connection has none. */
private class SwitchingFakeContentRepository : FakeContentRepository(emptyList(), emptyList()) {
    override fun observeMovies(connectionId: String, categoryId: String?): Flow<List<Movie>> =
        flowOf(if (connectionId == "a") listOf(Movie("1", "a", "Filme 1", null, null, "g", "g", null, null, null, null, null, "u")) else emptyList())
}

private class RecordingWatchProgressRepository(private val history: List<WatchProgress>) : WatchProgressRepository {
    val deletedItems = mutableListOf<Pair<String, String>>()
    val deletedSeries = mutableListOf<Pair<String, String>>()
    val cleared = mutableListOf<String>()

    override fun observeContinueWatching(connectionId: String, profileId: String) = unsupported<Flow<List<WatchProgress>>>()
    override suspend fun getProgress(connectionId: String, profileId: String, contentId: String, type: ContentType): WatchProgress? = unsupported()
    override suspend fun getLatestSeriesProgress(connectionId: String, profileId: String, seriesId: String): WatchProgress? = unsupported()
    override suspend fun saveProgress(progress: WatchProgress) = unsupported<Unit>()
    override suspend fun removeProgress(connectionId: String, profileId: String, contentId: String, type: ContentType) = unsupported<Unit>()
    override fun observeWatchHistory(profileId: String): Flow<List<WatchProgress>> = flowOf(history)
    override suspend fun clearWatchHistory(profileId: String) { cleared += profileId }
    override suspend fun deleteHistoryItem(profileId: String, contentId: String, type: ContentType) { deletedItems += profileId to contentId }
    override suspend fun deleteSeriesFromHistory(profileId: String, seriesId: String) { deletedSeries += profileId to seriesId }
    override suspend fun removeFromContinueWatching(connectionId: String, profileId: String, contentId: String, isSeries: Boolean) = unsupported<Unit>()
    override fun observeChannelHistory(connectionId: String, profileId: String) = unsupported<Flow<List<WatchProgress>>>()
    override suspend fun recordChannelWatch(connectionId: String, profileId: String, channelId: String) = unsupported<Unit>()
}

private fun <T> unsupported(): T = throw UnsupportedOperationException("Not needed by WatchHistoryViewModel")
