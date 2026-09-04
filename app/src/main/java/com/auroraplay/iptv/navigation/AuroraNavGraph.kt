package com.auroraplay.iptv.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.auroraplay.iptv.core.theme.AuroraColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.presentation.connections.AddConnectionScreen
import com.auroraplay.iptv.presentation.connections.ConnectionsScreen
import com.auroraplay.iptv.presentation.home.HomeScreen
import com.auroraplay.iptv.presentation.live.LiveTvScreen
import com.auroraplay.iptv.presentation.movies.MovieDetailsScreen
import com.auroraplay.iptv.presentation.movies.MoviesScreen
import com.auroraplay.iptv.presentation.player.PlayerScreen
import com.auroraplay.iptv.presentation.profiles.ProfileEditorScreen
import com.auroraplay.iptv.presentation.profiles.ProfileSelectionScreen
import com.auroraplay.iptv.presentation.series.SeriesDetailsScreen
import com.auroraplay.iptv.presentation.search.SearchScreen
import com.auroraplay.iptv.presentation.series.SeriesScreen
import com.auroraplay.iptv.presentation.settings.SettingsScreen

/**
 * Root navigation host. Profile selection and connection setup live outside
 * the main shell; once a profile + connection exist, the main tabs (Home,
 * Movies, Series, Live) share one shell with either a floating bottom bar
 * (phones/tablets) or a side rail (Android TV).
 */
@Composable
fun AuroraNavGraph(
    isTvDevice: Boolean,
    navController: NavHostController = rememberNavController(),
) {
  androidx.compose.runtime.CompositionLocalProvider(
      com.auroraplay.iptv.presentation.components.LocalIsTvDevice provides isTvDevice
  ) {
    // One coherent push/pop feel for the whole app: forward navigations glide
    // in from the right and the page behind recedes slightly; Back reverses it.
    // (NavHost's built-in default is a flat cross-fade, which is what made
    // opening a film / going Back feel abrupt.)
    val d = 300
    NavHost(
        navController = navController,
        startDestination = Screen.ProfileSelection.route,
        enterTransition = {
            slideInHorizontally(tween(d, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween(d))
        },
        exitTransition = {
            scaleOut(tween(d), targetScale = 0.96f) + fadeOut(tween(d * 2 / 3))
        },
        popEnterTransition = {
            scaleIn(tween(d), initialScale = 0.96f) + fadeIn(tween(d))
        },
        popExitTransition = {
            slideOutHorizontally(tween(d, easing = FastOutSlowInEasing)) { it / 5 } + fadeOut(tween(d))
        },
    ) {

        composable(Screen.ProfileSelection.route) {
            ProfileSelectionScreen(
                onProfileSelected = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.ProfileSelection.route) { inclusive = true }
                    }
                },
                onAddProfile = { navController.navigate(Screen.AddProfile.route) },
                onEditProfile = { id -> navController.navigate(Screen.EditProfile.createRoute(id)) },
                onOpenBackup = { navController.navigate(Screen.Backup.route) },
            )
        }

        composable(Screen.Backup.route) {
            com.auroraplay.iptv.presentation.settings.FileBackupScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.AddProfile.route) {
            ProfileEditorScreen(
                profileId = null,
                onBack = { navController.popBackStack() },
                onSaved = {
                    navController.navigate(Screen.AddConnection.route) {
                        popUpTo(Screen.ProfileSelection.route)
                    }
                },
            )
        }

        composable(
            route = Screen.EditProfile.route,
            arguments = listOf(navArgument("profileId") {}),
        ) { entry ->
            ProfileEditorScreen(
                profileId = entry.arguments?.getString("profileId"),
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(Screen.AddConnection.route) {
            AddConnectionScreen(
                profileId = null,
                onBack = { navController.popBackStack() },
                onConnected = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.ProfileSelection.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Connections.route) {
            ConnectionsScreen(
                onBack = { navController.popBackStack() },
                onAddConnection = { navController.navigate(Screen.AddConnection.route) },
            )
        }

        composable(Screen.Epg.route) {
            com.auroraplay.iptv.presentation.live.EpgGuideScreen(
                onBack = { navController.popBackStack() },
                onOpenChannel = { channelId -> navController.navigate(Screen.Player.createRoute("LIVE", channelId)) },
            )
        }

        composable(Screen.Downloads.route) {
            com.auroraplay.iptv.presentation.downloads.DownloadsScreen(
                onBack = { navController.popBackStack() },
                onPlay = { contentType, contentId -> navController.navigate(Screen.Player.createRoute(contentType, contentId)) },
            )
        }

        composable(Screen.Notifications.route) {
            com.auroraplay.iptv.presentation.notifications.NotificationsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // Main shell hosting the 5 primary tabs.
        composable(Screen.Home.route) { MainShell(isTvDevice, MainTab.HOME, navController) }
        composable(Screen.Live.route) { MainShell(isTvDevice, MainTab.LIVE, navController) }
        composable(Screen.Search.route) { MainShell(isTvDevice, MainTab.SEARCH, navController) }

        composable(Screen.Movies.route) {
            MoviesScreen(onOpenMovie = { id -> navController.navigate(Screen.MovieDetails.createRoute(id)) })
        }

        composable(Screen.Series.route) {
            SeriesScreen(onOpenSeries = { id -> navController.navigate(Screen.SeriesDetails.createRoute(id)) })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenConnections = { navController.navigate(Screen.Connections.route) },
                onOpenProfileEditor = { id -> navController.navigate(Screen.EditProfile.createRoute(id)) },
                onOpenHistory = { navController.navigate(Screen.History.route) },
            )
        }

        composable(Screen.History.route) {
            com.auroraplay.iptv.presentation.history.WatchHistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenMovie = { id -> navController.navigate(Screen.MovieDetails.createRoute(id)) },
                onOpenSeries = { id -> navController.navigate(Screen.SeriesDetails.createRoute(id)) },
            )
        }

        composable(
            route = Screen.MovieDetails.route,
            arguments = listOf(navArgument("movieId") {}),
        ) {
            MovieDetailsScreen(
                onBack = { navController.popBackStack() },
                onWatch = { movieId -> navController.navigate(Screen.Player.createRoute("MOVIE", movieId)) },
                onOpenMovie = { id -> navController.navigate(Screen.MovieDetails.createRoute(id)) },
            )
        }

        composable(
            route = Screen.SeriesDetails.route,
            arguments = listOf(navArgument("seriesId") {}),
        ) {
            SeriesDetailsScreen(
                onBack = { navController.popBackStack() },
                onWatchEpisode = { seriesId, episodeId ->
                    navController.navigate(Screen.Player.createRoute("SERIES", "$seriesId:$episodeId"))
                },
                onOpenSeries = { id -> navController.navigate(Screen.SeriesDetails.createRoute(id)) },
            )
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(navArgument("contentType") {}, navArgument("contentId") {}),
        ) { backStackEntry ->
            val rawType = backStackEntry.arguments?.getString("contentType")
            val contentType = rawType?.let { raw ->
                runCatching { ContentType.valueOf(raw.uppercase()) }.getOrNull()
            }
            val contentId = backStackEntry.arguments?.getString("contentId").orEmpty()

            if (contentType == null || contentId.isBlank()) {
                // Invalid deep-link/navigation data must never crash the app.
                Box(
                    Modifier.fillMaxSize().background(AuroraColors.BackgroundBase),
                    contentAlignment = Alignment.Center,
                ) {
                    com.auroraplay.iptv.presentation.components.GlassButton(
                        text = "Voltar",
                        onClick = { navController.popBackStack() },
                    )
                }
            } else {
                PlayerScreen(
                    contentType = contentType,
                    contentId = contentId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
  }
}

@Composable
private fun MainShell(isTvDevice: Boolean, initialTab: MainTab, navController: NavHostController) {
    // rememberSaveable (not remember): navigating to a sub-screen like
    // "Minhas conexões" disposes this composition, and plain remember would
    // reset the tab to HOME on return. Saved, back from a Settings sub-screen
    // lands back on the Ajustes tab where it was opened.
    var currentTab by rememberSaveable { mutableStateOf(initialTab) }
    val activity = LocalContext.current as? androidx.activity.ComponentActivity

    // Switching tabs is a `when(currentTab)` swap, so the outgoing tab leaves
    // composition entirely. Without this, coming back to Home rebuilt every
    // row from scratch and snapped the scroll position back to the top — the
    // visible hitch on tab changes. The holder keeps each tab's rememberSaveable
    // state (crucially the LazyList scroll offset) parked while it's off-screen
    // and restores it on return, so the swap is cheap and lands where you left.
    val tabStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

    // Home is the persistent root: back from any other tab returns here,
    // and back from Home asks before leaving the app.
    MainShellBackHandler(
        currentTab = currentTab,
        onNavigateToHome = { currentTab = MainTab.HOME },
        onConfirmExit = { activity?.finish() },
    )

    fun onOpenMovie(id: String) = navController.navigate(Screen.MovieDetails.createRoute(id))
    fun onOpenSeries(id: String) = navController.navigate(Screen.SeriesDetails.createRoute(id))
    fun onOpenChannel(id: String) = navController.navigate(Screen.Player.createRoute("LIVE", id))

    @Composable
    fun TabContent() {
        tabStateHolder.SaveableStateProvider(currentTab.name) {
            when (currentTab) {
                MainTab.HOME -> HomeScreen(
                    onOpenMovie = ::onOpenMovie,
                    onOpenSeries = ::onOpenSeries,
                    onOpenChannel = ::onOpenChannel,
                    onResume = { type, id -> navController.navigate(Screen.Player.createRoute(type, id)) },
                    onOpenAddConnection = { navController.navigate(Screen.AddConnection.route) },
                    onSeeAllMovies = { navController.navigate(Screen.Movies.route) },
                    onSeeAllSeries = { navController.navigate(Screen.Series.route) },
                    onOpenDownloads = { navController.navigate(Screen.Downloads.route) },
                    onOpenNotifications = { navController.navigate(Screen.Notifications.route) },
                )
                MainTab.LIVE -> LiveTvScreen(
                    onOpenFullscreen = ::onOpenChannel,
                    onOpenGuide = { navController.navigate(Screen.Epg.route) },
                )
                MainTab.SEARCH -> SearchScreen(
                    onOpenMovie = ::onOpenMovie,
                    onOpenSeries = ::onOpenSeries,
                    onOpenChannel = ::onOpenChannel,
                )
                MainTab.SETTINGS -> SettingsScreen(
                    onBack = { currentTab = MainTab.HOME },
                    onOpenConnections = { navController.navigate(Screen.Connections.route) },
                    onOpenProfileEditor = { id -> navController.navigate(Screen.EditProfile.createRoute(id)) },
                    onOpenHistory = { navController.navigate(Screen.History.route) },
                )
            }
        }
    }

    if (isTvDevice) {
        Row(Modifier.fillMaxSize()) {
            AuroraTvNavRail(currentTab = currentTab, onTabSelected = { currentTab = it })
            Box(Modifier.weight(1f)) { TabContent() }
        }
    } else {
        // One UI-style floating navigation. Screens already provide bottom
        // clearance, while this parent observes vertical scrolling from the
        // current tab so the bar can gently slide away on downward movement
        // and return as soon as the user scrolls back up.
        var navVisible by remember(currentTab) { mutableStateOf(true) }
        var scrollAccumulator by remember(currentTab) { mutableStateOf(0f) }
        val scrollConnection = remember(currentTab) {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPreScroll(
                    available: androidx.compose.ui.geometry.Offset,
                    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                ): androidx.compose.ui.geometry.Offset {
                    if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput &&
                        kotlin.math.abs(available.y) > 0.5f
                    ) {
                        scrollAccumulator += available.y
                        if (scrollAccumulator <= -24f) {
                            navVisible = false
                            scrollAccumulator = 0f
                        } else if (scrollAccumulator >= 24f) {
                            navVisible = true
                            scrollAccumulator = 0f
                        }
                    }
                    return androidx.compose.ui.geometry.Offset.Zero
                }
            }
        }
        val navAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (navVisible) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(280),
            label = "bottomNavAlpha",
        )
        val navOffset by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (navVisible) 0.dp else 92.dp,
            animationSpec = androidx.compose.animation.core.tween(320),
            label = "bottomNavOffset",
        )

        // Backdrop the floating nav bar blurs (FrostGlass). The tab content is
        // the source; the bar reads it through its own HazeState.
        val navHaze = remember { HazeState() }
        Box(
            Modifier
                .fillMaxSize()
                .background(AuroraColors.BackgroundBase)
                .nestedScroll(scrollConnection)
        ) {
            Box(Modifier.fillMaxSize().hazeSource(navHaze)) {
                TabContent()
            }
            if (currentTab != MainTab.SETTINGS) {
                AuroraBottomNavBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it },
                    hazeState = navHaze,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            alpha = navAlpha
                            translationY = navOffset.toPx()
                        },
                )
            }
        }
    }
}
