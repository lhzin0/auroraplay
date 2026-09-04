package com.auroraplay.iptv.presentation.live

import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.domain.model.Category
import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.EpgProgram
import com.auroraplay.iptv.domain.model.Favorite
import com.auroraplay.iptv.domain.model.Profile
import com.auroraplay.iptv.domain.model.XtreamConnection
import com.auroraplay.iptv.domain.policy.ContentPolicy
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.FavoriteRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import com.auroraplay.iptv.domain.repository.SearchResults
import com.auroraplay.iptv.domain.repository.SyncStage
import com.auroraplay.iptv.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LiveTvViewModel has no debounce/`flowOn(Dispatchers.Default)` in its
 * pipeline, so a [StandardTestDispatcher] gives fully deterministic control
 * over it — every emission below is driven explicitly, no real-time waits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun channel(id: String, connectionId: String, category: String = "g", name: String = "Canal $id") = Channel(
        id = id, connectionId = connectionId, name = name, logoUrl = null, categoryId = category,
        categoryName = category, streamUrl = "u", epgChannelId = null,
    )

    private fun category(id: String, name: String = id) = Category(id = id, name = name, type = ContentType.LIVE, connectionId = "c")

    private fun connection(id: String, isDefault: Boolean = true) =
        XtreamConnection(id = id, name = id, serverUrl = "http://$id", username = "u", isDefault = isDefault)

    private fun profile(isKids: Boolean = false) = Profile(id = "p", name = "P", avatarColorHex = "#000", isKids = isKids)

    private fun viewModel(
        connectionFlow: MutableStateFlow<XtreamConnection?>,
        profileFlow: MutableStateFlow<Profile?>,
        categoriesByConnection: Map<String, List<Category>> = emptyMap(),
        channelsByConnection: Map<String, MutableStateFlow<List<Channel>>> = emptyMap(),
        favoritesByConnection: Map<String, List<Favorite>> = emptyMap(),
        favoriteRepository: FavoriteRepository = FakeFavoriteRepository(favoritesByConnection),
    ) = LiveTvViewModel(
        connectionRepository = FakeConnectionRepository(connectionFlow),
        contentRepository = FakeContentRepository(categoriesByConnection, channelsByConnection),
        profileRepository = FakeProfileRepository(profileFlow),
        favoriteRepository = favoriteRepository,
        toggleFavoriteUseCase = ToggleFavoriteUseCase(favoriteRepository),
        contentPolicy = ContentPolicy(),
    )

    @Test
    fun `loads categories, channels and favorites for the active connection`() = runTest {
        val vm = viewModel(
            connectionFlow = MutableStateFlow(connection("a")),
            profileFlow = MutableStateFlow(profile()),
            categoriesByConnection = mapOf("a" to listOf(category("g"))),
            channelsByConnection = mapOf("a" to MutableStateFlow(listOf(channel("1", "a")))),
            favoritesByConnection = mapOf("a" to listOf(Favorite("a", "1", ContentType.LIVE, "p"))),
        )
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("g"), state.categories.map { it.id })
        assertEquals(listOf("1"), state.channels.map { it.id })
        assertEquals(setOf("1"), state.favoriteIds)
    }

    @Test
    fun `a kids profile only sees kid-appropriate content`() = runTest {
        val vm = viewModel(
            connectionFlow = MutableStateFlow(connection("a")),
            profileFlow = MutableStateFlow(profile(isKids = true)),
            categoriesByConnection = mapOf("a" to listOf(category("Infantil"), category("Filmes Adultos"))),
            channelsByConnection = mapOf(
                "a" to MutableStateFlow(
                    listOf(channel("1", "a", category = "Infantil"), channel("2", "a", category = "Filmes Adultos")),
                ),
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf("Infantil"), state.categories.map { it.id })
        assertEquals(listOf("1"), state.channels.map { it.id })
    }

    @Test
    fun `selecting a category narrows the channel list`() = runTest {
        val vm = viewModel(
            connectionFlow = MutableStateFlow(connection("a")),
            profileFlow = MutableStateFlow(profile()),
            channelsByConnection = mapOf(
                "a" to MutableStateFlow(listOf(channel("1", "a", category = "esportes"), channel("2", "a", category = "filmes"))),
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.uiState.value.channels.size)

        vm.selectCategory("esportes")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("1"), vm.uiState.value.channels.map { it.id })
        assertEquals("esportes", vm.uiState.value.selectedCategoryId)
    }

    @Test
    fun `the selected channel survives a catalog refresh if still present, and is dropped if it disappears`() = runTest {
        val channels = MutableStateFlow(listOf(channel("1", "a"), channel("2", "a")))
        val vm = viewModel(
            connectionFlow = MutableStateFlow(connection("a")),
            profileFlow = MutableStateFlow(profile()),
            channelsByConnection = mapOf("a" to channels),
        )
        dispatcher.scheduler.advanceUntilIdle()
        vm.selectChannel(channel("1", "a"))
        assertEquals("1", vm.uiState.value.selectedChannel?.id)

        // A re-sync that keeps channel "1" — selection must survive, and pick
        // up any field changes (e.g. a renamed channel) rather than the stale copy.
        channels.value = listOf(channel("1", "a", name = "Canal renomeado"), channel("2", "a"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Canal renomeado", vm.uiState.value.selectedChannel?.name)

        // A re-sync that drops channel "1" — the selection must not point at
        // a channel that no longer exists.
        channels.value = listOf(channel("2", "a"))
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.selectedChannel)
    }

    @Test
    fun `switching the default connection resets selection and never leaks a same-id channel`() = runTest {
        val connectionFlow = MutableStateFlow<XtreamConnection?>(connection("a"))
        val vm = viewModel(
            connectionFlow = connectionFlow,
            profileFlow = MutableStateFlow(profile()),
            categoriesByConnection = mapOf("a" to listOf(category("catA")), "b" to listOf(category("catB"))),
            channelsByConnection = mapOf(
                // Both connections reuse the numeric id "1" for a different channel.
                "a" to MutableStateFlow(listOf(channel("1", "a", category = "catA", name = "Old Channel"))),
                "b" to MutableStateFlow(listOf(channel("1", "b", category = "catB", name = "New Channel"))),
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()
        vm.selectCategory("catA")
        vm.selectChannel(channel("1", "a", category = "catA", name = "Old Channel"))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("Old Channel", vm.uiState.value.selectedChannel?.name)

        connectionFlow.value = connection("b")
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.selectedCategoryId)
        // The old connection's "Old Channel" (also id "1") must never leak
        // through as the new connection's selection (audit #3-class bug).
        assertNull(state.selectedChannel)
        assertEquals(listOf("catB"), state.categories.map { it.id })
        assertEquals("New Channel", state.channels.single().name)
    }

    @Test
    fun `no active connection yields an empty, non-loading state`() = runTest {
        val vm = viewModel(connectionFlow = MutableStateFlow(null), profileFlow = MutableStateFlow(profile()))
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.channels.isEmpty())
    }

    @Test
    fun `visibleChannels applies the favorites filter and the local search together`() = runTest {
        val vm = viewModel(
            connectionFlow = MutableStateFlow(connection("a")),
            profileFlow = MutableStateFlow(profile()),
            channelsByConnection = mapOf(
                "a" to MutableStateFlow(listOf(channel("1", "a", name = "Globo"), channel("2", "a", name = "SporTV"))),
            ),
            favoritesByConnection = mapOf("a" to listOf(Favorite("a", "2", ContentType.LIVE, "p"))),
        )
        dispatcher.scheduler.advanceUntilIdle()

        vm.toggleFavoritesFilter()
        assertEquals(listOf("2"), vm.uiState.value.visibleChannels.map { it.id })

        vm.updateQuery("glo")
        // The favourites filter and the query both apply; "Globo" isn't a favourite.
        assertTrue(vm.uiState.value.visibleChannels.isEmpty())
    }

    @Test
    fun `toggleFavorite routes through with the active profile and connection`() = runTest {
        val favoriteRepository = RecordingFavoriteRepository()
        val vm = viewModel(
            connectionFlow = MutableStateFlow(connection("a")),
            profileFlow = MutableStateFlow(profile()),
            channelsByConnection = mapOf("a" to MutableStateFlow(listOf(channel("1", "a")))),
            favoriteRepository = favoriteRepository,
        )
        dispatcher.scheduler.advanceUntilIdle()

        vm.toggleFavorite(channel("1", "a"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(Quadruple("a", "p", "1", ContentType.LIVE)), favoriteRepository.toggled)
    }
}

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private class FakeConnectionRepository(private val flow: MutableStateFlow<XtreamConnection?>) : ConnectionRepository {
    override fun observeConnections(): Flow<List<XtreamConnection>> = unsupported()
    override suspend fun getConnection(id: String): XtreamConnection? = unsupported()
    override suspend fun getDefaultConnection(): XtreamConnection? = unsupported()
    override fun observeDefaultConnection(): Flow<XtreamConnection?> = flow
    override fun addConnection(name: String, serverUrl: String, username: String, password: String, profileId: String?): Flow<Resource<XtreamConnection>> = unsupported()
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

private class FakeContentRepository(
    private val categoriesByConnection: Map<String, List<Category>>,
    private val channelsByConnection: Map<String, MutableStateFlow<List<Channel>>>,
) : ContentRepository {
    override fun syncConnection(connectionId: String) = unsupported<Flow<Resource<SyncStage>>>()
    override fun observeCategories(connectionId: String, type: ContentType): Flow<List<Category>> =
        flowOf(categoriesByConnection[connectionId].orEmpty())
    override fun observeChannels(connectionId: String, categoryId: String?): Flow<List<Channel>> {
        val source = channelsByConnection[connectionId] ?: MutableStateFlow(emptyList())
        return source.map { list -> if (categoryId == null) list else list.filter { it.categoryId == categoryId } }
    }
    override fun observeMovies(connectionId: String, categoryId: String?) = unsupported<Flow<List<com.auroraplay.iptv.domain.model.Movie>>>()
    override fun observeSeries(connectionId: String, categoryId: String?) = unsupported<Flow<List<com.auroraplay.iptv.domain.model.Series>>>()
    override suspend fun getSeriesDetail(connectionId: String, seriesId: String, forceRefresh: Boolean, allowStaleRefresh: Boolean) = unsupported<com.auroraplay.iptv.domain.model.Series?>()
    override suspend fun getMovieDetail(connectionId: String, movieId: String) = unsupported<com.auroraplay.iptv.domain.model.Movie?>()
    override suspend fun refreshSeriesEpisodes(connectionId: String, seriesId: String) = unsupported<List<String>?>()
    override suspend fun getCachedMovie(connectionId: String, movieId: String) = unsupported<com.auroraplay.iptv.domain.model.Movie?>()
    override suspend fun getCachedSeries(connectionId: String, seriesId: String) = unsupported<com.auroraplay.iptv.domain.model.Series?>()
    override suspend fun getLastSyncMillis(connectionId: String): Long? = unsupported()
    override fun search(connectionId: String, query: String): Flow<SearchResults> = unsupported()
    override suspend fun getShortEpg(connectionId: String, channelId: String): Pair<EpgProgram?, EpgProgram?> = unsupported()
    override suspend fun getEpgTimeline(connectionId: String, channelId: String, limit: Int): List<EpgProgram> = unsupported()
}

private open class FakeFavoriteRepository(private val byConnection: Map<String, List<Favorite>>) : FavoriteRepository {
    override fun observeFavorites(connectionId: String, profileId: String, type: ContentType?): Flow<List<Favorite>> =
        flowOf(byConnection[connectionId].orEmpty())
    override fun isFavorite(connectionId: String, profileId: String, contentId: String, type: ContentType): Flow<Boolean> = unsupported()
    override suspend fun toggleFavorite(connectionId: String, profileId: String, contentId: String, type: ContentType) = Unit
}

/** Records every toggleFavorite call instead of doing anything with it. */
private class RecordingFavoriteRepository : FakeFavoriteRepository(emptyMap()) {
    val toggled = mutableListOf<Quadruple<String, String, String, ContentType>>()
    override suspend fun toggleFavorite(connectionId: String, profileId: String, contentId: String, type: ContentType) {
        toggled += Quadruple(connectionId, profileId, contentId, type)
    }
}

private fun <T> unsupported(): T = throw UnsupportedOperationException("Not needed by LiveTvViewModel")
