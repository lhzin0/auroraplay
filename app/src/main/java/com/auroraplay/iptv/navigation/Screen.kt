package com.auroraplay.iptv.navigation

sealed class Screen(val route: String) {
    data object ProfileSelection : Screen("profile_selection")
    data object AddProfile : Screen("add_profile")
    data object EditProfile : Screen("edit_profile/{profileId}") {
        fun createRoute(profileId: String) = "edit_profile/$profileId"
    }
    data object Connections : Screen("connections")
    data object AddConnection : Screen("add_connection")

    data object Home : Screen("home")
    data object Live : Screen("live")
    data object Search : Screen("search")
    data object Movies : Screen("movies")
    data object Series : Screen("series")
    data object Settings : Screen("settings")

    data object MovieDetails : Screen("movie_details/{movieId}") {
        fun createRoute(movieId: String) = "movie_details/$movieId"
    }
    data object SeriesDetails : Screen("series_details/{seriesId}") {
        fun createRoute(seriesId: String) = "series_details/$seriesId"
    }
    data object Player : Screen("player/{contentType}/{contentId}") {
        // Series ids carry a ':' ("seriesId:episodeId") which is a reserved
        // character in a route path, so it is encoded here and decoded by
        // the NavHost argument reader.
        fun createRoute(contentType: String, contentId: String) =
            "player/$contentType/" + android.net.Uri.encode(contentId)
    }
    data object Epg : Screen("epg")
    data object Downloads : Screen("downloads")
    data object Notifications : Screen("notifications")
}

/** Bottom (phone) / side (TV) navigation destinations. */
/**
 * Primary destinations. Search is deliberately absent: each content page
 * owns its own contextual search (see PageHeader), so there is no global
 * search tab that could return channels when the user is browsing movies.
 */
/**
 * Primary destinations. Movies and Series are reachable from the Home rails
 * and from search, so they no longer occupy a tab each; that space goes to
 * search and settings, which are needed from anywhere.
 */
enum class MainTab(val screen: Screen, val label: String) {
    HOME(Screen.Home, "Início"),
    LIVE(Screen.Live, "Canais"),
    SEARCH(Screen.Search, "Buscar"),
    SETTINGS(Screen.Settings, "Ajustes"),
}
