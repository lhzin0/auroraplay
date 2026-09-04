package com.auroraplay.iptv.presentation.search

import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.Movie
import com.auroraplay.iptv.domain.model.Profile
import com.auroraplay.iptv.domain.model.Series
import com.auroraplay.iptv.domain.model.WatchProgress
import com.auroraplay.iptv.domain.model.XtreamConnection
import com.auroraplay.iptv.domain.policy.ContentPolicy
import com.auroraplay.iptv.domain.usecase.SmartCategoryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * SearchViewModel debounces (250ms for results, 900ms for the persisted
 * "recent search") and does its actual filtering on `Dispatchers.Default`
 * (`flowOn`), so — unlike the other ViewModel tests in this project — a
 * [kotlinx.coroutines.test.StandardTestDispatcher]'s virtual time can't drive
 * it: `advanceUntilIdle()` only fast-forwards the Main test dispatcher, not
 * the real Default thread pool this pipeline actually runs on. Instead this
 * uses [Dispatchers.Unconfined] for Main and [runBlocking] so every delay is
 * a genuine (small) real-time wait, and polls `uiState` with [withTimeout]
 * rather than a single post-advance snapshot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @Before
    fun setUp() { Dispatchers.setMain(Dispatchers.Unconfined) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun movie(id: String, name: String = "M$id", genre: String? = null, category: String = "Geral", addedAtMillis: Long = 0) = Movie(
        id = id, connectionId = "a", name = name, posterUrl = null, backdropUrl = null,
        categoryId = "g", categoryName = category, year = null, genre = genre, plot = null,
        durationLabel = null, rating = null, streamUrl = "u", addedAtMillis = addedAtMillis,
    )

    private fun series(id: String, name: String = "S$id", genre: String? = null, category: String = "Geral", addedAtMillis: Long = 0) = Series(
        id = id, connectionId = "a", name = name, posterUrl = null, backdropUrl = null,
        categoryId = "g", categoryName = category, year = null, genre = genre, plot = null, rating = null, addedAtMillis = addedAtMillis,
    )

    private fun channel(id: String, name: String = "C$id", category: String = "Geral") = Channel(
        id = id, connectionId = "a", name = name, logoUrl = null, categoryId = "g",
        categoryName = category, streamUrl = "u", epgChannelId = null,
    )

    private fun connection(id: String = "a") = XtreamConnection(id = id, name = id, serverUrl = "http://$id", username = "u")
    private fun profile(isKids: Boolean = false) = Profile(id = "p", name = "P", avatarColorHex = "#000", isKids = isKids)

    private fun viewModel(
        connection: XtreamConnection? = connection(),
        profile: Profile? = profile(),
        movies: List<Movie> = emptyList(),
        series: List<Series> = emptyList(),
        channels: List<Channel> = emptyList(),
        continueWatching: List<WatchProgress> = emptyList(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
    ) = SearchViewModel(
        connectionRepository = FakeConnectionRepository(MutableStateFlow(connection)),
        contentRepository = FakeContentRepository(
            moviesByConnection = mapOf("a" to movies),
            seriesByConnection = mapOf("a" to series),
            channelsByConnection = mapOf("a" to channels),
        ),
        profileRepository = FakeProfileRepository(MutableStateFlow(profile)),
        watchProgressRepository = FakeWatchProgressRepository(mapOf("a" to continueWatching)),
        settingsRepository = settingsRepository,
        smartCategoryBuilder = SmartCategoryBuilder(),
        contentPolicy = ContentPolicy(),
    )

    private suspend fun SearchViewModel.awaitSearched(query: String) =
        withTimeout(5.seconds) { uiState.first { it.searchedQuery == query } }

    @Test
    fun `updateQuery reflects in the field immediately, ahead of the debounced results`() = runBlocking {
        val vm = viewModel(movies = listOf(movie("1", name = "Duna")))
        vm.updateQuery("duna")
        // The field itself is synchronous — never waits on the 250ms debounce.
        assertEquals("duna", vm.uiState.value.query)
    }

    @Test
    fun `a title search matches case- and accent-insensitively once the debounce settles`() = runBlocking {
        val vm = viewModel(movies = listOf(movie("1", name = "AÇÃO Nas Ruas"), movie("2", name = "Outro filme")))
        vm.updateQuery("acao")
        val state = vm.awaitSearched("acao")
        assertEquals(listOf("1"), state.results.map { it.id })
    }

    @Test
    fun `the filter narrows results to the selected content type`() = runBlocking {
        val vm = viewModel(
            movies = listOf(movie("1", name = "Zebra Filme")),
            series = listOf(series("2", name = "Zebra Série")),
        )
        vm.updateFilter(SearchFilter.SERIES)
        vm.updateQuery("zebra")
        val state = vm.awaitSearched("zebra")
        assertEquals(listOf("2"), state.results.map { it.id })
    }

    @Test
    fun `loadMoreResults widens the page beyond the first 60 matches`() = runBlocking {
        val movies = (1..61).map { movie(it.toString(), name = "Alvo $it") }
        val vm = viewModel(movies = movies)
        vm.updateQuery("alvo")
        val first = vm.awaitSearched("alvo")
        assertEquals(60, first.results.size)
        assertTrue(first.hasMoreResults)

        vm.loadMoreResults()
        val second = withTimeout(5.seconds) { vm.uiState.first { it.results.size == 61 } }
        assertFalse(second.hasMoreResults)
    }

    @Test
    fun `a kids profile never sees adult content in search results`() = runBlocking {
        val vm = viewModel(
            profile = profile(isKids = true),
            movies = listOf(movie("1", name = "Filme Zeta", category = "Infantil"), movie("2", name = "Zeta XXX", category = "Infantil")),
        )
        vm.updateQuery("zeta")
        val state = vm.awaitSearched("zeta")
        assertEquals(listOf("1"), state.results.map { it.id })
    }

    @Test
    fun `a comma-separated genre query requires every part to match`() = runBlocking {
        val vm = viewModel(
            movies = listOf(
                movie("1", name = "Filme A", genre = "Ação e Aventura"), // has both
                movie("2", name = "Filme B", genre = "Ação"),            // missing "aventura"
                movie("3", name = "Filme C", genre = "Comédia"),         // neither
            ),
        )
        vm.updateQuery("ação, aventura")
        val state = vm.awaitSearched("ação, aventura")
        assertEquals(listOf("1"), state.results.map { it.id })
    }

    @Test
    fun `suggestions prefer an unwatched title in a genre the profile has already watched`() = runBlocking {
        val vm = viewModel(
            movies = listOf(
                movie("1", name = "Já visto", genre = "Terror", addedAtMillis = 10),   // watched -> excluded
                movie("2", name = "Sugestão", genre = "Terror", addedAtMillis = 20),   // unwatched, same genre -> suggested
                movie("3", name = "Sem relação", genre = "Comédia", addedAtMillis = 30), // wrong genre -> not suggested
            ),
            continueWatching = listOf(
                WatchProgress(connectionId = "a", contentId = "1", type = ContentType.MOVIE, profileId = "p", positionMillis = 500, durationMillis = 1000),
            ),
        )
        // Suggestions populate independently of the query field.
        val state = withTimeout(5.seconds) { vm.uiState.first { !it.isLoading } }
        assertEquals(listOf("2"), state.suggestions.map { it.id })
    }

    @Test
    fun `typing settles into a persisted recent search that then feeds back into the UI state`() = runBlocking {
        val settingsRepository = FakeSettingsRepository()
        val vm = viewModel(settingsRepository = settingsRepository)
        vm.updateQuery("duna parte dois")

        withTimeout(5.seconds) {
            while (settingsRepository.addedSearches.isEmpty()) kotlinx.coroutines.delay(20)
        }
        assertEquals(listOf("p" to "duna parte dois"), settingsRepository.addedSearches)

        val state = withTimeout(5.seconds) { vm.uiState.first { it.recentSearches.contains("duna parte dois") } }
        assertTrue(state.recentSearches.contains("duna parte dois"))
    }

    @Test
    fun `clearRecentSearches routes to the repository for the active profile`() = runBlocking {
        val settingsRepository = FakeSettingsRepository()
        val vm = viewModel(settingsRepository = settingsRepository)
        // Establish the active profile id (set inside runSearchPipeline).
        withTimeout(5.seconds) { vm.uiState.first { !it.isLoading } }

        vm.clearRecentSearches()

        assertEquals(listOf("p"), settingsRepository.clearedProfiles)
    }

    @Test
    fun `no active connection yields an empty, non-loading state`() = runBlocking {
        val vm = viewModel(connection = null)
        val state = withTimeout(5.seconds) { vm.uiState.first { !it.isLoading } }
        assertTrue(state.results.isEmpty())
        assertTrue(state.suggestions.isEmpty())
    }
}
