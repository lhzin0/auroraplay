package com.auroraplay.iptv.presentation.series

import androidx.lifecycle.SavedStateHandle
import com.auroraplay.iptv.data.repository.MetadataEnricher
import com.auroraplay.iptv.domain.model.Episode
import com.auroraplay.iptv.domain.model.Profile
import com.auroraplay.iptv.domain.model.Season
import com.auroraplay.iptv.domain.model.Series
import com.auroraplay.iptv.domain.model.XtreamConnection
import com.auroraplay.iptv.domain.policy.ContentPolicy
import com.auroraplay.iptv.domain.repository.ConnectionRepository
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.FavoriteRepository
import com.auroraplay.iptv.domain.repository.ProfileRepository
import com.auroraplay.iptv.domain.repository.WatchProgressRepository
import com.auroraplay.iptv.domain.usecase.ToggleFavoriteUseCase
import com.auroraplay.iptv.player.download.DownloadTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Covers the TMDB per-episode-runtime wiring added for the episode-duration
 * fix: the runtimes for the season on screen are pulled once, folded into
 * [SeriesDetailsUiState.tmdbEpisodeRuntimeMinutesBySeason], and a season is
 * never re-fetched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val connectionRepository = mockk<ConnectionRepository>()
    private val contentRepository = mockk<ContentRepository>(relaxed = true)
    private val profileRepository = mockk<ProfileRepository>()
    private val favoriteRepository = mockk<FavoriteRepository>()
    private val watchProgressRepository = mockk<WatchProgressRepository>()
    private val toggleFavoriteUseCase = mockk<ToggleFavoriteUseCase>(relaxed = true)
    private val downloadTracker = mockk<DownloadTracker>()
    private val metadataEnricher = mockk<MetadataEnricher>(relaxed = true)
    private val contentPolicy = ContentPolicy()

    private fun episode(season: Int, number: Int) = Episode(
        id = "s${season}e$number",
        seriesId = "s1",
        seasonNumber = season,
        episodeNumber = number,
        title = "Ep $number",
        thumbnailUrl = null,
        durationLabel = "21min",
        plot = null,
        streamUrl = "http://host/s${season}e$number.ts",
    )

    private val series = Series(
        id = "s1",
        connectionId = "c1",
        name = "Série",
        posterUrl = null,
        backdropUrl = null,
        categoryId = "cat",
        categoryName = "Drama",
        year = null,
        genre = "Drama",
        plot = "…",
        rating = null,
        seasons = listOf(
            Season(1, "Temporada 1", listOf(episode(1, 1), episode(1, 2))),
            Season(2, "Temporada 2", listOf(episode(2, 1))),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { connectionRepository.getDefaultConnection() } returns
            XtreamConnection(id = "c1", name = "Con", serverUrl = "http://host", username = "u", isDefault = true)
        every { profileRepository.observeActiveProfile() } returns flowOf(Profile("p1", "P", "#ffffff"))
        coEvery { contentRepository.getCachedSeries("c1", "s1") } returns series
        coEvery { contentRepository.getSeriesDetail(any(), any(), any(), any()) } returns series
        every { contentRepository.observeSeries("c1") } returns flowOf(emptyList())
        every { favoriteRepository.isFavorite(any(), any(), any(), any()) } returns flowOf(false)
        coEvery { watchProgressRepository.getLatestSeriesProgress(any(), any(), any()) } returns null
        coEvery { watchProgressRepository.getMeasuredEpisodeDurations(any(), any(), any()) } returns emptyMap()
        every { downloadTracker.downloads } returns MutableStateFlow(emptyMap())
        coEvery { metadataEnricher.youtubeTrailerForSeries(any(), any()) } returns null
        coEvery { metadataEnricher.episodeRuntimes("Série", null, 1) } returns mapOf(1 to 47, 2 to 47)
        coEvery { metadataEnricher.episodeRuntimes("Série", null, 2) } returns mapOf(1 to 50)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel() = SeriesDetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("seriesId" to "s1")),
        connectionRepository = connectionRepository,
        contentRepository = contentRepository,
        profileRepository = profileRepository,
        favoriteRepository = favoriteRepository,
        toggleFavoriteUseCase = toggleFavoriteUseCase,
        watchProgressRepository = watchProgressRepository,
        downloadTracker = downloadTracker,
        metadataEnricher = metadataEnricher,
        contentPolicy = contentPolicy,
    )

    @Test
    fun `pulls TMDB runtimes for the auto-selected season and folds them into state`() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertEquals(mapOf(1 to 47, 2 to 47), state.tmdbEpisodeRuntimeMinutesBySeason[1])
        coVerify(exactly = 1) { metadataEnricher.episodeRuntimes("Série", null, 1) }
    }

    @Test
    fun `selecting another season fetches that season once, never re-fetches`() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()

        vm.selectSeason(2)
        advanceUntilIdle()
        vm.selectSeason(1) // back to a season already fetched
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertEquals(mapOf(1 to 47, 2 to 47), state.tmdbEpisodeRuntimeMinutesBySeason[1])
        assertEquals(mapOf(1 to 50), state.tmdbEpisodeRuntimeMinutesBySeason[2])
        coVerify(exactly = 1) { metadataEnricher.episodeRuntimes("Série", null, 1) }
        coVerify(exactly = 1) { metadataEnricher.episodeRuntimes("Série", null, 2) }
    }
}
