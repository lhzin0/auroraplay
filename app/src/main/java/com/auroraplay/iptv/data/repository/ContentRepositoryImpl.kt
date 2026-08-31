package com.auroraplay.iptv.data.repository

import com.auroraplay.iptv.core.util.Resource
import com.auroraplay.iptv.core.util.MetadataSanitizer
import com.auroraplay.iptv.data.api.XtreamApiService
import com.auroraplay.iptv.data.api.XtreamUrlBuilder
import com.auroraplay.iptv.data.database.dao.CategoryDao
import com.auroraplay.iptv.data.database.dao.ChannelDao
import com.auroraplay.iptv.data.database.dao.ConnectionDao
import com.auroraplay.iptv.data.database.dao.EpisodeDao
import com.auroraplay.iptv.data.database.dao.MovieDao
import com.auroraplay.iptv.data.database.dao.SeriesDao
import com.auroraplay.iptv.data.datastore.SecureCredentialStore
import com.auroraplay.iptv.data.mapper.toDomain
import com.auroraplay.iptv.data.mapper.toEntity
import com.auroraplay.iptv.domain.model.Category
import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.Episode
import com.auroraplay.iptv.domain.model.Movie
import com.auroraplay.iptv.domain.model.Season
import com.auroraplay.iptv.domain.model.Series
import com.auroraplay.iptv.domain.repository.ContentRepository
import com.auroraplay.iptv.domain.repository.SearchResults
import com.auroraplay.iptv.domain.repository.SyncStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepositoryImpl @Inject constructor(
    private val api: XtreamApiService,
    private val connectionDao: ConnectionDao,
    private val secureStore: SecureCredentialStore,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val metadataEnricher: MetadataEnricher,
) : ContentRepository {

    private suspend fun urlBuilderFor(connectionId: String): XtreamUrlBuilder? {
        val connection = connectionDao.getById(connectionId) ?: return null
        val password = secureStore.getPassword(connectionId) ?: return null
        return XtreamUrlBuilder(connection.serverUrl, connection.username, password)
    }

    override fun syncConnection(connectionId: String): Flow<Resource<SyncStage>> = flow {
        emit(Resource.Loading)
        val urlBuilder = urlBuilderFor(connectionId)
        if (urlBuilder == null) {
            emit(Resource.Error("Conexão não encontrada."))
            return@flow
        }

        try {
            emit(Resource.Success(SyncStage.CONNECTING))
            api.authenticate(urlBuilder.auth())

            // --- Live channels ---
            emit(Resource.Success(SyncStage.CHANNELS))
            val liveCategories = runCatching { api.getLiveCategories(urlBuilder.liveCategories()) }.getOrDefault(emptyList())
            categoryDao.clear(connectionId, ContentType.LIVE.name)
            categoryDao.upsertAll(liveCategories.map { it.toEntity(connectionId, ContentType.LIVE) })
            val categoryNameById = liveCategories.associate { it.categoryId to it.categoryName }
            val liveStreams = runCatching { api.getLiveStreams(urlBuilder.liveStreams()) }.getOrDefault(emptyList())
            channelDao.clear(connectionId)
            channelDao.upsertAll(
                liveStreams.map { it.toEntity(connectionId, categoryNameById[it.categoryId] ?: "Geral", urlBuilder) }
            )

            // --- Movies ---
            emit(Resource.Success(SyncStage.MOVIES))
            val vodCategories = runCatching { api.getVodCategories(urlBuilder.vodCategories()) }.getOrDefault(emptyList())
            categoryDao.clear(connectionId, ContentType.MOVIE.name)
            categoryDao.upsertAll(vodCategories.map { it.toEntity(connectionId, ContentType.MOVIE) })
            val vodCategoryNameById = vodCategories.associate { it.categoryId to it.categoryName }
            val vodStreams = runCatching { api.getVodStreams(urlBuilder.vodStreams()) }.getOrDefault(emptyList())
            movieDao.clear(connectionId)
            movieDao.upsertAll(
                vodStreams.map { it.toEntity(connectionId, vodCategoryNameById[it.categoryId] ?: "Geral", urlBuilder) }
            )

            // --- Series ---
            emit(Resource.Success(SyncStage.SERIES))
            val seriesCategories = runCatching { api.getSeriesCategories(urlBuilder.seriesCategories()) }.getOrDefault(emptyList())
            categoryDao.clear(connectionId, ContentType.SERIES.name)
            categoryDao.upsertAll(seriesCategories.map { it.toEntity(connectionId, ContentType.SERIES) })
            val seriesCategoryNameById = seriesCategories.associate { it.categoryId to it.categoryName }
            val seriesList = runCatching { api.getSeries(urlBuilder.series()) }.getOrDefault(emptyList())
            seriesDao.clear(connectionId)
            seriesDao.upsertAll(
                seriesList.map { it.toEntity(connectionId, seriesCategoryNameById[it.categoryId] ?: "Geral") }
            )
            // Episodes are fetched lazily per-series (get_series_info) when the user opens details,
            // to avoid one request per series during a full sync.

            connectionDao.updateLastSync(connectionId, System.currentTimeMillis())
            connectionDao.updateStatus(connectionId, "ONLINE")
            emit(Resource.Success(SyncStage.DONE))
        } catch (e: Exception) {
            connectionDao.updateStatus(connectionId, "OFFLINE")
            emit(Resource.Error(mapSyncError(e), e))
        }
    }
        // The DTO→entity mapping over a full Xtream catalog (often 10k+ VOD
        // rows) is plain CPU work — without this it ran on whatever collected
        // the sync (the main thread, from pull-to-refresh) and froze scrolling
        // for the whole sync.
        .flowOn(Dispatchers.IO)

    override fun observeCategories(connectionId: String, type: ContentType): Flow<List<Category>> =
        categoryDao.observe(connectionId, type.name).map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override fun observeChannels(connectionId: String, categoryId: String?): Flow<List<Channel>> =
        channelDao.observe(connectionId, categoryId).map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override fun observeMovies(connectionId: String, categoryId: String?): Flow<List<Movie>> =
        movieDao.observe(connectionId, categoryId).map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override fun observeSeries(connectionId: String, categoryId: String?): Flow<List<Series>> =
        seriesDao.observe(connectionId, categoryId).map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    override suspend fun getSeriesDetail(connectionId: String, seriesId: String): Series? {
        val entity = seriesDao.getById(connectionId, seriesId) ?: return null
        var episodes = episodeDao.getForSeries(connectionId, seriesId)

        if (episodes.isEmpty()) {
            val urlBuilder = urlBuilderFor(connectionId)
            if (urlBuilder != null) {
                runCatching { api.getSeriesInfo(urlBuilder.seriesInfo(seriesId)) }.getOrNull()?.let { info ->
                    val allEpisodes = info.episodes?.flatMap { (seasonKey, eps) ->
                        val seasonNumber = seasonKey.toIntOrNull() ?: eps.firstOrNull()?.season ?: 1
                        eps.map { it.toEntity(seriesId, connectionId, seasonNumber, urlBuilder) }
                    }.orEmpty()
                    episodeDao.clearForSeries(connectionId, seriesId)
                    episodeDao.upsertAll(allEpisodes)
                    episodes = allEpisodes
                }
            }
        }

        val seasons = episodes.groupBy { it.seasonNumber }
            .toSortedMap()
            .map { (seasonNumber, eps) ->
                Season(
                    seasonNumber = seasonNumber,
                    name = "Temporada $seasonNumber",
                    episodes = eps.sortedBy { it.episodeNumber }.map { it.toDomain() },
                )
            }

        var enriched = entity
        if (enriched.plot.isNullOrBlank()) {
            metadataEnricher.forSeries(enriched.name, enriched.year)?.let { extra ->
                enriched = enriched.copy(
                    plot = extra.plot ?: enriched.plot,
                    genre = enriched.genre ?: extra.genre,
                    backdropUrl = enriched.backdropUrl ?: extra.backdropUrl,
                    posterUrl = enriched.posterUrl ?: extra.posterUrl,
                    year = enriched.year ?: extra.year,
                    rating = enriched.rating ?: extra.rating,
                )
                seriesDao.upsertAll(listOf(enriched))
            }
        }

        return enriched.toDomain().copy(seasons = seasons)
    }

    override suspend fun getMovieDetail(connectionId: String, movieId: String): Movie? {
        var entity = movieDao.getById(connectionId, movieId) ?: return null

        // Tier 1: ask the playlist itself (get_vod_info) for the synopsis.
        if (entity.plot.isNullOrBlank()) {
            urlBuilderFor(connectionId)?.let { urlBuilder ->
                runCatching { api.getVodInfo(urlBuilder.vodInfo(movieId)) }.getOrNull()?.info?.let { info ->
                    entity = entity.copy(
                        plot = MetadataSanitizer.text(info.plot) ?: entity.plot,
                        genre = MetadataSanitizer.categoryName(info.genre) ?: entity.genre,
                        durationLabel = MetadataSanitizer.duration(info.duration) ?: entity.durationLabel,
                        backdropUrl = info.backdropPath?.firstOrNull() ?: entity.backdropUrl,
                        year = entity.year ?: MetadataSanitizer.year(null, entity.name, info.releaseDate),
                    )
                    movieDao.update(entity)
                }
            }
        }

        // Tier 2: still no synopsis -> look the title up online (TMDB).
        if (entity.plot.isNullOrBlank()) {
            metadataEnricher.forMovie(entity.name, entity.year)?.let { extra ->
                entity = entity.copy(
                    plot = extra.plot ?: entity.plot,
                    genre = entity.genre ?: extra.genre,
                    backdropUrl = entity.backdropUrl ?: extra.backdropUrl,
                    posterUrl = entity.posterUrl ?: extra.posterUrl,
                    year = entity.year ?: extra.year,
                    rating = entity.rating ?: extra.rating,
                )
                movieDao.update(entity)
            }
        }

        return entity.toDomain()
    }

    override suspend fun getLastSyncMillis(connectionId: String): Long? =
        connectionDao.getById(connectionId)?.lastSyncMillis

    override fun search(connectionId: String, query: String): Flow<SearchResults> = flow {
        if (query.isBlank()) {
            emit(SearchResults(emptyList(), emptyList(), emptyList()))
            return@flow
        }
        val channels = channelDao.search(connectionId, query).map { it.toDomain() }
        val movies = movieDao.search(connectionId, query).map { it.toDomain() }
        val series = seriesDao.search(connectionId, query).map { it.toDomain() }
        emit(SearchResults(channels, movies, series))
    }

    override suspend fun getShortEpg(
        connectionId: String,
        channelId: String,
    ): Pair<com.auroraplay.iptv.domain.model.EpgProgram?, com.auroraplay.iptv.domain.model.EpgProgram?> {
        val programs = getEpgTimeline(connectionId, channelId, limit = 4)
        val now = System.currentTimeMillis()
        // Xtream's short EPG is ordered but doesn't flag which entry is
        // "now" — the one whose window actually contains the current
        // time is current; anything after that is upcoming.
        val current = programs.firstOrNull { now in it.startMillis until it.endMillis } ?: programs.firstOrNull()
        val next = programs.firstOrNull { it.startMillis > (current?.endMillis ?: now) }
        return current to next
    }

    override suspend fun getEpgTimeline(
        connectionId: String,
        channelId: String,
        limit: Int,
    ): List<com.auroraplay.iptv.domain.model.EpgProgram> {
        val urlBuilder = urlBuilderFor(connectionId) ?: return emptyList()
        return runCatching {
            val response = api.getShortEpg(urlBuilder.shortEpg(channelId), limit = limit)
            response.epgListings.orEmpty()
                .mapNotNull { it.toDomain() }
                .sortedBy { it.startMillis }
        }.getOrDefault(emptyList())
    }

    private fun mapSyncError(e: Exception): String = when (e) {
        is java.net.UnknownHostException, is java.net.ConnectException -> "Não foi possível conectar ao servidor."
        is java.net.SocketTimeoutException -> "O servidor demorou para responder. Tente novamente."
        else -> "Não foi possível sincronizar o conteúdo."
    }
}
