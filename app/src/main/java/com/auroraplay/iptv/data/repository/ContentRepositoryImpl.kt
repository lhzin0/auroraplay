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
import com.auroraplay.iptv.data.database.entity.MovieEntity
import com.auroraplay.iptv.data.datastore.SecureCredentialStore
import com.auroraplay.iptv.data.mapper.toDomain
import com.auroraplay.iptv.data.mapper.toEntity
import com.auroraplay.iptv.domain.model.Category
import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.ContentType
import com.auroraplay.iptv.domain.model.Episode
import com.auroraplay.iptv.domain.model.AudioStreamVariant
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
        movieDao.observe(connectionId, categoryId)
            .map { list -> list.collapseAudioVariants().map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    /**
     * Collapses a provider's dubbed + subtitled copies of one movie to a
     * single row, preferring the dubbed copy for a pt-BR audience and keeping
     * the original ordering.
     *
     * Rows are grouped by [MetadataSanitizer.variantKey] (base name minus
     * marker/year, accent-folded, plus the year). A group collapses when
     * either:
     *  - it mixes a subtitled row with a non-subtitled one (a real dub/sub
     *    split — the split is read from the title *and* the category, since
     *    providers often mark only one), or
     *  - the shared key carries a year, i.e. the rows are the same title from
     *    the same year (an exact/near duplicate).
     * Otherwise the group is left intact, so two unrelated films that merely
     * share a marker-less title are never hidden. Non-destructive: every row
     * stays in the DB, so a favourite / continue-watching entry pointing at
     * the hidden twin still opens.
     */
    private fun List<MovieEntity>.collapseAudioVariants(): List<MovieEntity> {
        if (size < 2) return this
        val order = ArrayList<String>(size)
        val groups = HashMap<String, MutableList<MovieEntity>>(size)
        for (movie in this) {
            val key = MetadataSanitizer.variantKey(movie.name, movie.year)
            groups.getOrPut(key) { order.add(key); ArrayList(2) }.add(movie)
        }
        if (groups.size == size) return this

        val out = ArrayList<MovieEntity>(size)
        for (key in order) {
            val group = groups.getValue(key)
            if (group.size == 1) { out += group[0]; continue }

            val tags = group.map { MetadataSanitizer.audioVariantFrom(it.name, it.categoryName) }
            val hasLeg = tags.any { it == MetadataSanitizer.AudioVariant.LEGENDADO }
            val hasNonLeg = tags.any { it != MetadataSanitizer.AudioVariant.LEGENDADO }
            val yearKnown = key.substringAfterLast('|').isNotBlank()

            if ((hasLeg && hasNonLeg) || (yearKnown && group.size <= 6)) {
                val keep = group.indices.minByOrNull { i -> variantRank(tags[i]) } ?: 0
                out += group[keep]
            } else {
                out += group
            }
        }
        return out
    }

    /** Preference order when picking the survivor: dubbed, then unknown, then subtitled. */
    private fun variantRank(v: MetadataSanitizer.AudioVariant): Int = when (v) {
        MetadataSanitizer.AudioVariant.DUBLADO -> 0
        MetadataSanitizer.AudioVariant.DESCONHECIDO -> 1
        MetadataSanitizer.AudioVariant.LEGENDADO -> 2
    }

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

    override suspend fun getMovieAudioVariants(connectionId: String, movieId: String): List<AudioStreamVariant> {
        val movie = movieDao.getById(connectionId, movieId) ?: return emptyList()
        val base = MetadataSanitizer.variantKeyBase(movie.name)
        if (base.length < 3) return emptyList()
        val myYear = movie.year?.trim()?.takeIf { it.isNotEmpty() }

        // LIKE probe on the longest title word (accent/punctuation-stripped),
        // then keep rows with the same yearless base and no *conflicting*
        // known year (a null year on either side is allowed to pair).
        val probe = MetadataSanitizer.stripAudioMarkers(movie.name)
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }
            .filter { it.length >= 3 }
            .maxByOrNull { it.length } ?: movie.name
        val group = (runCatching { movieDao.searchAll(connectionId, probe) }.getOrDefault(emptyList()) + movie)
            .distinctBy { it.id }
            .filter { MetadataSanitizer.variantKeyBase(it.name) == base }
            .filter { c ->
                val cy = c.year?.trim()?.takeIf { it.isNotEmpty() }
                cy == null || myYear == null || cy == myYear
            }
            .distinctBy { it.streamUrl }
        if (group.size < 2) return emptyList()

        val tagged = group.map { it to MetadataSanitizer.audioVariantFrom(it.name, it.categoryName) }
        val hasLeg = tagged.any { it.second == MetadataSanitizer.AudioVariant.LEGENDADO }
        val hasNonLeg = tagged.any { it.second != MetadataSanitizer.AudioVariant.LEGENDADO }
        val sameYearPair = myYear != null && group.all { it.year?.trim().orEmpty().ifEmpty { myYear } == myYear }
        // Offer the switch when it's a real dub/sub pair, or when the streams
        // are the same title + same year (still an alternate version worth
        // flipping to). Otherwise stay quiet.
        if (!((hasLeg && hasNonLeg) || sameYearPair)) return emptyList()

        val result = tagged.map { (m, tag) ->
            // With a subtitled copy present, the un-marked ones are the dubbed
            // track (pt-BR default), so the toggle reads cleanly.
            val effective = if (tag == MetadataSanitizer.AudioVariant.DESCONHECIDO && hasLeg)
                MetadataSanitizer.AudioVariant.DUBLADO else tag
            AudioStreamVariant(
                label = when (effective) {
                    MetadataSanitizer.AudioVariant.DUBLADO -> "Dublado"
                    MetadataSanitizer.AudioVariant.LEGENDADO -> "Legendado"
                    MetadataSanitizer.AudioVariant.DESCONHECIDO -> "Original"
                },
                streamUrl = m.streamUrl,
                variant = effective,
            )
        }
            .distinctBy { it.streamUrl }
            .sortedBy { it.variant.ordinal }

        // Two copies but nothing told us which is which (identical name +
        // category, no marker anywhere). Don't guess a wrong "Dublado" label —
        // number them; the viewer flips once and hears which is which.
        return if (result.size == 2 && result.all { it.variant == MetadataSanitizer.AudioVariant.DESCONHECIDO }) {
            result.mapIndexed { i, v -> v.copy(label = "Versão ${i + 1}") }
        } else {
            result
        }
    }

    override suspend fun getLastSyncMillis(connectionId: String): Long? =
        connectionDao.getById(connectionId)?.lastSyncMillis

    override fun search(connectionId: String, query: String): Flow<SearchResults> = flow {
        if (query.isBlank()) {
            emit(SearchResults(emptyList(), emptyList(), emptyList()))
            return@flow
        }
        val channels = channelDao.search(connectionId, query).map { it.toDomain() }
        val movies = movieDao.search(connectionId, query).collapseAudioVariants().map { it.toDomain() }
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
