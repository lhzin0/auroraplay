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

    suspend fun getSeriesDetail(connectionId: String, seriesId: String): Series?
    suspend fun getMovieDetail(connectionId: String, movieId: String): Movie?

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
