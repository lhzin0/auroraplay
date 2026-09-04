package com.auroraplay.iptv.presentation.profiles

import com.auroraplay.iptv.core.security.ProfileGuard
import com.auroraplay.iptv.domain.model.Movie
import com.auroraplay.iptv.domain.model.Profile
import com.auroraplay.iptv.domain.model.Series
import com.auroraplay.iptv.domain.model.XtreamConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun profile(id: String, isKids: Boolean = false, pinHash: String? = null) =
        Profile(id = id, name = id, avatarColorHex = "#000", isKids = isKids, pinHash = pinHash)

    private fun movie(id: String, name: String = "M$id", poster: String? = "http://img.example/poster-$id.jpg", backdrop: String? = null, category: String = "Geral", genre: String? = null) = Movie(
        id = id, connectionId = "a", name = name, posterUrl = poster, backdropUrl = backdrop,
        categoryId = "g", categoryName = category, year = null, genre = genre, plot = null,
        durationLabel = null, rating = null, streamUrl = "u",
    )

    private fun series(id: String, name: String = "S$id", poster: String? = "poster-$id.jpg", backdrop: String? = null, category: String = "Geral", genre: String? = null) = Series(
        id = id, connectionId = "a", name = name, posterUrl = poster, backdropUrl = backdrop,
        categoryId = "g", categoryName = category, year = null, genre = genre, plot = null, rating = null,
    )

    private fun viewModel(
        profiles: List<Profile> = emptyList(),
        connection: XtreamConnection? = XtreamConnection(id = "a", name = "a", serverUrl = "http://a", username = "u"),
        movies: List<Movie> = emptyList(),
        series: List<Series> = emptyList(),
        profileRepository: FakeProfileRepository = FakeProfileRepository(profiles),
        profileGuard: ProfileGuard = ProfileGuard(),
    ) = ProfileViewModel(
        profileRepository = profileRepository,
        connectionRepository = FakeConnectionRepository(connection),
        contentRepository = FakeContentRepository(moviesByConnection = mapOf("a" to movies), seriesByConnection = mapOf("a" to series)),
        profileGuard = profileGuard,
    )

    @Test
    fun `loads the profile list and clears the loading flag`() = runTest {
        val vm = viewModel(profiles = listOf(profile("1"), profile("2")))
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("1", "2"), state.profiles.map { it.id })
    }

    @Test
    fun `hero slides prefer the poster over the backdrop and drop adult titles`() = runTest {
        val vm = viewModel(
            movies = listOf(
                movie("1", poster = "http://img.example/poster-1.jpg", backdrop = "http://img.example/backdrop-1.jpg"),
                movie("2", poster = null, backdrop = "http://img.example/backdrop-2.jpg"),
                movie("3", poster = "http://img.example/poster-3.jpg", category = "XXX"), // must be excluded
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        val slides = vm.uiState.value.slides
        assertEquals(2, slides.size)
        assertTrue(slides.none { it.title == "M3" })
        val slide1 = slides.single { it.title == "M1" }
        assertEquals("https://img.example/poster-1.jpg", slide1.imageUrl) // http upgraded to https, poster wins over backdrop
        assertFalse(slide1.wide) // poster -> portrait crop
        val slide2 = slides.single { it.title == "M2" }
        assertTrue(slide2.wide) // no poster -> falls back to the (wide) backdrop
    }

    @Test
    fun `no default connection yields no slides and does not crash`() = runTest {
        val vm = viewModel(connection = null, movies = listOf(movie("1")))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.slides.isEmpty())
    }

    @Test
    fun `selectProfile sets the active profile`() = runTest {
        val profileRepository = FakeProfileRepository(listOf(profile("1")))
        val vm = viewModel(profileRepository = profileRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.selectProfile("1")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("1", profileRepository.getActiveProfileId())
    }

    @Test
    fun `isProtected is true for a locked or a kids profile, false otherwise`() {
        val vm = viewModel()
        assertTrue(vm.isProtected(profile("1", pinHash = "hash")))
        assertTrue(vm.isProtected(profile("1", isKids = true)))
        assertFalse(vm.isProtected(profile("1")))
    }

    @Test
    fun `deleting an unprotected profile removes it right away`() = runTest {
        val profileRepository = FakeProfileRepository(listOf(profile("1")))
        val vm = viewModel(profileRepository = profileRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.deleteProfile("1")
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(profileRepository.getProfile("1"))
    }

    @Test
    fun `deleting a protected profile without a grant is refused`() = runTest {
        val profileRepository = FakeProfileRepository(listOf(profile("1", isKids = true)))
        val vm = viewModel(profileRepository = profileRepository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.deleteProfile("1")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(profileRepository.getProfile("1") != null)
    }

    @Test
    fun `authorizeManagement grants the guard, letting a protected delete through and consuming the grant`() = runTest {
        val profileRepository = FakeProfileRepository(listOf(profile("1", isKids = true)))
        val guard = ProfileGuard()
        val vm = viewModel(profileRepository = profileRepository, profileGuard = guard)
        dispatcher.scheduler.advanceUntilIdle()

        vm.authorizeManagement("1")
        vm.deleteProfile("1")
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(profileRepository.getProfile("1"))
        // The grant is single-use — spent by the delete it authorized.
        assertFalse(guard.isAuthorized("1"))
    }
}
