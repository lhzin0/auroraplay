package com.auroraplay.iptv.domain.repository

import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.domain.model.Category
import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.Movie
import com.auroraplay.iptv.domain.model.Series
import kotlinx.coroutines.flow.Flow

interface ContentRepository {
    /** Pulls fresh catalogs from the Xtream server and stores them locally. Emits progress. */
    fun syncConnection(connectionId: String): Flow<Resource<SyncStage>>

    fun observeCategories(connectionId: String, type: com.auroraplay.iptv.domain.model.ContentType): Flow<List<Category>>
    fun observeChannels(connectionId: String, categoryId: String? = null): Flow<List<Channel>>
    fun observeMovies(connectionId: String, categoryId: String? = null): Flow<List<Movie>>
    fun observeSeries(connectionId: String, categoryId: String? = null): Flow<List<Series>>

    /**
     * The series row plus its episodes. Episodes are (re-)fetched from the
     * provider when none are cached, when [forceRefresh] is set (manual
     * "atualizar"), or — only if [allowStaleRefresh] — when the cached copy is
     * older than the episode TTL (audit #7). The player passes
     * [allowStaleRefresh] = false so starting playback never waits on a
     * `get_series_info` round-trip (audit #18).
     */
    suspend fun getSeriesDetail(
        connectionId: String,
        seriesId: String,
        forceRefresh: Boolean = false,
        allowStaleRefresh: Boolean = true,
    ): Series?
    suspend fun getMovieDetail(connectionId: String, movieId: String): Movie?

    /**
     * Pulls the current episode list for a single series straight from the
     * provider (`get_series_info`) and replaces the local copy transactionally.
     * Returns the episode ids now known for the series, or null when the
     * provider couldn't be reached or returned nothing — the local data is left
     * untouched in that case. Does not touch the rest of the catalog; used by
     * the "new episode available" background check.
     */
    suspend fun refreshSeriesEpisodes(connectionId: String, seriesId: String): List<String>?

    /** Local-only, no network: the catalog row (and, for a series, whatever
     * episodes are already cached). Returned instantly so the detail page can
     * paint before [getMovieDetail] / [getSeriesDetail] finish enriching. */
    suspend fun getCachedMovie(connectionId: String, movieId: String): Movie?
    suspend fun getCachedSeries(connectionId: String, seriesId: String): Series?

    suspend fun getLastSyncMillis(connectionId: String): Long?

    fun search(connectionId: String, query: String): Flow<SearchResults>

    /** Live "now playing" / "up next" for one channel, fetched on demand — Xtream's
     * short EPG is too volatile to cache alongside the rest of the catalog sync. */
    suspend fun getShortEpg(connectionId: String, channelId: String): Pair<com.auroraplay.iptv.domain.model.EpgProgram?, com.auroraplay.iptv.domain.model.EpgProgram?>

    /** The fuller timeline behind the guide screen — same endpoint as
     * [getShortEpg], just asked for more entries and returned unprocessed. */
    suspend fun getEpgTimeline(connectionId: String, channelId: String, limit: Int = 6): List<com.auroraplay.iptv.domain.model.EpgProgram>
}

enum class SyncStage { CONNECTING, CHANNELS, MOVIES, SERIES, DONE }

data class SearchResults(
    val channels: List<Channel>,
    val movies: List<Movie>,
    val series: List<Series>,
)
