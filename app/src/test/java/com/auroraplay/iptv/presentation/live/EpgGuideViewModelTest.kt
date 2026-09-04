package com.auroraplay.iptv.presentation.live

import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.EpgProgram
import com.auroraplay.iptv.domain.model.Profile
import com.auroraplay.iptv.domain.model.XtreamConnection
import com.auroraplay.iptv.domain.policy.ContentPolicy
import com.auroraplay.iptv.domain.repository.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EpgGuideViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun channel(id: String, connectionId: String, category: String = "g", name: String = "Canal $id") = Channel(
        id = id, connectionId = connectionId, name = name, logoUrl = null, categoryId = category,
        categoryName = category, streamUrl = "u", epgChannelId = null,
    )

    private fun connection(id: String) = XtreamConnection(id = id, name = id, serverUrl = "http://$id", username = "u")
    private fun profile(isKids: Boolean = false) = Profile(id = "p", name = "P", avatarColorHex = "#000", isKids = isKids)

    private fun viewModel(
        connectionFlow: MutableStateFlow<XtreamConnection?>,
        profileFlow: MutableStateFlow<Profile?> = MutableStateFlow(profile()),
        contentRepository: ContentRepository,
    ) = EpgGuideViewModel(
        connectionRepository = FakeConnectionRepository(connectionFlow),
        contentRepository = contentRepository,
        profileRepository = FakeProfileRepository(profileFlow),
        contentPolicy = ContentPolicy(),
    )

    @Test
    fun `loads channel rows for the active connection, filtered by kids policy`() = runTest {
        val vm = viewModel(
            connectionFlow = MutableStateFlow(connection("a")),
            profileFlow = MutableStateFlow(profile(isKids = true)),
            contentRepository = FakeContentRepository(
                channelsByConnection = mapOf(
                    "a" to MutableStateFlow(
                        listOf(channel("1", "a", category = "Infantil"), channel("2", "a", category = "Filmes Adultos")),
                    ),
                ),
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("1"), vm.uiState.value.rows.map { it.channel.id })
    }

    @Test
    fun `ensureTimeline fetches and attaches programs to the matching row only`() = runTest {
        val repo = RecordingContentRepository(mapOf("a" to MutableStateFlow(listOf(channel("1", "a"), channel("2", "a")))))
        val vm = viewModel(connectionFlow = MutableStateFlow(connection("a")), contentRepository = repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.ensureTimeline("1")
        dispatcher.scheduler.advanceUntilIdle()

        val rows = vm.uiState.value.rows.associateBy { it.channel.id }
        assertEquals(listOf("prog-1"), rows.getValue("1").programs.map { it.id })
        assertTrue(rows.getValue("2").programs.isEmpty())
        assertEquals(1, repo.fetchCount("a", "1"))
    }

    @Test
    fun `ensureTimeline does not re-fetch the same channel right away`() = runTest {
        val repo = RecordingContentRepository(mapOf("a" to MutableStateFlow(listOf(channel("1", "a")))))
        val vm = viewModel(connectionFlow = MutableStateFlow(connection("a")), contentRepository = repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.ensureTimeline("1")
        dispatcher.scheduler.advanceUntilIdle()
        vm.ensureTimeline("1") // called again immediately, e.g. from list scroll recomposition
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.fetchCount("a", "1"))
    }

    @Test
    fun `a catalog refresh preserves an already-fetched row's timeline`() = runTest {
        val channels = MutableStateFlow(listOf(channel("1", "a")))
        val repo = RecordingContentRepository(mapOf("a" to channels))
        val vm = viewModel(connectionFlow = MutableStateFlow(connection("a")), contentRepository = repo)
        dispatcher.scheduler.advanceUntilIdle()
        vm.ensureTimeline("1")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.rows.single().programs.isNotEmpty())

        channels.value = listOf(channel("1", "a", name = "Renomeado"))
        dispatcher.scheduler.advanceUntilIdle()

        val row = vm.uiState.value.rows.single()
        assertEquals("Renomeado", row.channel.name)
        assertTrue(row.programs.isNotEmpty()) // not wiped by the refresh
    }

    @Test
    fun `switching connection resets rows and never leaks a same-id channel's fetched timeline`() = runTest {
        val connectionFlow = MutableStateFlow<XtreamConnection?>(connection("a"))
        val repo = RecordingContentRepository(
            mapOf(
                "a" to MutableStateFlow(listOf(channel("1", "a", name = "Old Channel"))),
                "b" to MutableStateFlow(listOf(channel("1", "b", name = "New Channel"))),
            ),
        )
        val vm = viewModel(connectionFlow = connectionFlow, contentRepository = repo)
        dispatcher.scheduler.advanceUntilIdle()
        vm.ensureTimeline("1")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.rows.single().programs.isNotEmpty())

        connectionFlow.value = connection("b")
        dispatcher.scheduler.advanceUntilIdle()

        val row = vm.uiState.value.rows.single()
        assertEquals("New Channel", row.channel.name)
        assertTrue(row.programs.isEmpty()) // connection "a"'s fetched timeline must not leak onto "b"'s same-id channel

        // The throttle window must also have been reset, or a genuine refetch
        // for the new connection's channel "1" would be silently swallowed.
        vm.ensureTimeline("1")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.fetchCount("b", "1"))
    }

    @Test
    fun `no active connection yields an empty, non-loading state`() = runTest {
        val vm = viewModel(connectionFlow = MutableStateFlow(null), contentRepository = FakeContentRepository())
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(false, state.isLoading)
        assertTrue(state.rows.isEmpty())
    }
}

/** Fakes a successful get_short_epg per (connectionId, channelId) and counts how many times each pair was actually fetched. */
private class RecordingContentRepository(
    channelsByConnection: Map<String, MutableStateFlow<List<Channel>>>,
) : FakeContentRepository(channelsByConnection = channelsByConnection) {
    private val fetches = mutableMapOf<Pair<String, String>, Int>()
    fun fetchCount(connectionId: String, channelId: String): Int = fetches[connectionId to channelId] ?: 0

    override suspend fun getEpgTimeline(connectionId: String, channelId: String, limit: Int): List<EpgProgram> {
        val key = connectionId to channelId
        fetches[key] = (fetches[key] ?: 0) + 1
        return listOf(EpgProgram(id = "prog-$channelId", title = "T", description = "", startMillis = 0, endMillis = 1000))
    }
}
