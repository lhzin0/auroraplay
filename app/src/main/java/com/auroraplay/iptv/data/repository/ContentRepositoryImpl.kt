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
import com.auroraplay.iptv.data.mapper.mergedWith
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** How long a cached episode list stays fresh before the next open of a series
 * re-fetches it (audit #7). Six hours: long enough that repeatedly opening a
 * series in one sitting never re-hits the network, short enough that a
 * weekly-airing show's new episode shows up the same day. */
internal const val EPISODE_TTL_MILLIS: Long = 6L * 60L * 60L * 1000L

/** Pure so the TTL decision is unit-testable without a repository. A
 * [syncedAtMillis] of 0 or less ("never fetched") is always stale, regardless
 * of the clock. */
internal fun episodesAreStale(syncedAtMillis: Long, nowMillis: Long, ttlMillis: Long): Boolean =
    syncedAtMillis <= 0L || nowMillis - syncedAtMillis >= ttlMillis

/**
 * Audit #15: "agora" and "a seguir" from an ordered EPG timeline.
 *  - current = the program whose half-open window [start, end) actually
 *    contains [nowMillis]; null when none does (never the first row as a
 *    fallback — that invented a "current" program hours out of date).
 *  - next = the earliest program that starts at or after the current
 *    program's end (a back-to-back program starting exactly on the boundary
 *    counts), or, when there is no current program, the earliest one that
 *    hasn't started yet.
 */
internal fun pickNowAndNext(
    programs: List<com.auroraplay.iptv.domain.model.EpgProgram>,
    nowMillis: Long,
): Pair<com.auroraplay.iptv.domain.model.EpgProgram?, com.auroraplay.iptv.domain.model.EpgProgram?> {
    val ordered = programs.sortedBy { it.startMillis }
    val current = ordered.firstOrNull { nowMillis >= it.startMillis && nowMillis < it.endMillis }
    val threshold = current?.endMillis ?: nowMillis
    val next = ordered.firstOrNull { it !== current && it.startMillis >= threshold }
    return current to next
}

/**
 * Audit #10: given which sync sections reached the server, decide the outcome.
 * [SyncStage.DONE] only when every section reached (so `lastSyncMillis` may be
 * bumped); [SyncStage.PARTIAL] when some but not all did (keep old rows, don't
 * bump, retry later); null when none did (an outage → Resource.Error).
 */
internal fun syncOutcome(
    channelsReached: Boolean,
    moviesReached: Boolean,
    seriesReached: Boolean,
): SyncStage? = when {
    channelsReached && moviesReached && seriesReached -> SyncStage.DONE
    channelsReached || moviesReached || seriesReached -> SyncStage.PARTIAL
    else -> null
}

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
            emit(Resource.Error("Conexão sem credenciais disponíveis. Importe um backup completo ou cadastre a playlist novamente."))
            return@flow
        }

        try {
            emit(Resource.Success(SyncStage.CONNECTING))
            val auth = api.authenticate(urlBuilder.auth())
            if (auth.userInfo?.auth != 1 && !auth.userInfo?.status.equals("Active", ignoreCase = true)) {
                connectionDao.updateStatus(connectionId, "OFFLINE")
                emit(Resource.Error("O servidor recusou as credenciais da playlist."))
                return@flow
            }

            // Every section below follows the same rule: only wipe-and-replace
            // the local rows when the remote fetch actually returned something.
            // A fetch that fails (timeout, 5xx, provider rate-limit) yields null
            // here and we KEEP whatever is already cached — a transient blip
            // during the on-open auto-sync must never leave the catalog empty.
            // A section "reached" the server when its streams call returned a
            // list (even an empty one — a valid "nothing here"); a null means
            // the call failed and that section is still pending (audit #10).
            var channelsReached = false
            var moviesReached = false
            var seriesReached = false

            // --- Live channels ---
            emit(Resource.Success(SyncStage.CHANNELS))
            // Radio is dropped at the source: providers ship 1000+ "RÁDIO"
            // entries that only bloat the catalog and clutter the TV lists.
            val allLiveCategories = runCatching { api.getLiveCategories(urlBuilder.liveCategories()) }.getOrNull()
            val radioCategoryIds = allLiveCategories
                ?.filter { MetadataSanitizer.isRadioCategory(it.categoryName) }
                ?.map { it.categoryId }
                ?.toSet()
                .orEmpty()
            val liveCategories = allLiveCategories?.filterNot { it.categoryId in radioCategoryIds }
            if (!liveCategories.isNullOrEmpty()) {
                categoryDao.replace(connectionId, ContentType.LIVE.name, liveCategories.map { it.toEntity(connectionId, ContentType.LIVE) })
            }
            val liveCategoryNameById = liveCategories?.associate { it.categoryId to it.categoryName }
                ?: categoryDao.getAll(connectionId, ContentType.LIVE.name).associate { it.id to it.name }
            val liveStreams = runCatching { api.getLiveStreams(urlBuilder.liveStreams()) }.getOrNull()
                ?.filterNot { it.categoryId in radioCategoryIds || MetadataSanitizer.isRadioCategory(liveCategoryNameById[it.categoryId]) }
            channelsReached = liveStreams != null
            if (!liveStreams.isNullOrEmpty()) {
                channelDao.replace(
                    connectionId,
                    liveStreams.map { it.toEntity(connectionId, liveCategoryNameById[it.categoryId] ?: "Geral") },
                )
            }

            // --- Movies ---
            emit(Resource.Success(SyncStage.MOVIES))
            val vodCategories = runCatching { api.getVodCategories(urlBuilder.vodCategories()) }.getOrNull()
            if (!vodCategories.isNullOrEmpty()) {
                categoryDao.replace(connectionId, ContentType.MOVIE.name, vodCategories.map { it.toEntity(connectionId, ContentType.MOVIE) })
            }
            val vodCategoryNameById = vodCategories?.associate { it.categoryId to it.categoryName }
                ?: categoryDao.getAll(connectionId, ContentType.MOVIE.name).associate { it.id to it.name }
            val vodStreams = runCatching { api.getVodStreams(urlBuilder.vodStreams()) }.getOrNull()
            moviesReached = vodStreams != null
            if (!vodStreams.isNullOrEmpty()) {
                // Carry forward genre/plot/backdrop/rating that get_vod_info or
                // TMDB filled in earlier — the provider's plain listing omits
                // them, and a blind replace would wipe every enrichment (audit #9).
                val existingById = movieDao.getAll(connectionId).associateBy { it.id }
                movieDao.replace(
                    connectionId,
                    vodStreams.map {
                        it.toEntity(connectionId, vodCategoryNameById[it.categoryId] ?: "Geral")
                            .mergedWith(existingById[it.streamId.toString()])
                    },
                )
            }

            // --- Series ---
            emit(Resource.Success(SyncStage.SERIES))
            val seriesCategories = runCatching { api.getSeriesCategories(urlBuilder.seriesCategories()) }.getOrNull()
            if (!seriesCategories.isNullOrEmpty()) {
                categoryDao.replace(connectionId, ContentType.SERIES.name, seriesCategories.map { it.toEntity(connectionId, ContentType.SERIES) })
            }
            val seriesCategoryNameById = seriesCategories?.associate { it.categoryId to it.categoryName }
                ?: categoryDao.getAll(connectionId, ContentType.SERIES.name).associate { it.id to it.name }
            val seriesList = runCatching { api.getSeries(urlBuilder.series()) }.getOrNull()
            seriesReached = seriesList != null
            if (!seriesList.isNullOrEmpty()) {
                // Same as movies: keep enrichment (backdrop/rating/plot/year)
                // and the episode-fetch timestamp instead of nulling them on
                // every sync (audit #9 / #7).
                val existingById = seriesDao.getAll(connectionId).associateBy { it.id }
                seriesDao.replace(
                    connectionId,
                    seriesList.map {
                        it.toEntity(connectionId, seriesCategoryNameById[it.categoryId] ?: "Geral")
                            .mergedWith(existingById[it.seriesId.toString()])
                    },
                )
            }
            // Episodes are fetched lazily per-series (get_series_info) when the user opens details,
            // to avoid one request per series during a full sync.

            when (syncOutcome(channelsReached, moviesReached, seriesReached)) {
                // Every section reached the server — a genuine full sync.
                SyncStage.DONE -> {
                    connectionDao.updateLastSync(connectionId, System.currentTimeMillis())
                    connectionDao.updateStatus(connectionId, "ONLINE")
                    emit(Resource.Success(SyncStage.DONE))
                }
                // Some sections synced, others failed. Old rows for the failed
                // sections are still in place; the connection is reachable
                // (auth passed), but lastSyncMillis is deliberately NOT bumped
                // so the next scheduled auto-sync retries the whole run (#10).
                SyncStage.PARTIAL -> {
                    connectionDao.updateStatus(connectionId, "ONLINE")
                    emit(Resource.Success(SyncStage.PARTIAL))
                }
                // Nothing came back from any endpoint — an outage.
                else -> {
                    connectionDao.updateStatus(connectionId, "OFFLINE")
                    emit(Resource.Error("Não foi possível atualizar o catálogo agora."))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
        categoryDao.observe(connectionId, type.name)
            .map { list ->
                list.asSequence()
                    .filterNot { type == ContentType.LIVE && MetadataSanitizer.isRadioCategory(it.name) }
                    .map { it.toDomain() }
                    .toList()
            }
            .flowOn(Dispatchers.Default)

    override fun observeChannels(connectionId: String, categoryId: String?): Flow<List<Channel>> =
        flow {
            // Audit #4: creds live only in the secure store; resolve them once
            // here and rebuild each playback URL in memory — never from a
            // stored column.
            val urlBuilder = urlBuilderFor(connectionId)
            emitAll(
                channelDao.observe(connectionId, categoryId)
                    // Also hide radio rows that a pre-filter build already stored,
                    // so the change takes effect without waiting for a re-sync.
                    .map { list -> list.asSequence().filterNot { MetadataSanitizer.isRadioCategory(it.categoryName) }.map { it.toDomain(urlBuilder) }.toList() },
            )
        }.flowOn(Dispatchers.Default)

    override fun observeMovies(connectionId: String, categoryId: String?): Flow<List<Movie>> =
        flow {
            val urlBuilder = urlBuilderFor(connectionId)
            emitAll(
                movieDao.observe(connectionId, categoryId)
                    .map { list -> list.collapseAudioVariants({ it.name }, { it.year }, { it.categoryName }).map { it.toDomain(urlBuilder) } },
            )
        }.flowOn(Dispatchers.Default)

    override fun observeSeries(connectionId: String, categoryId: String?): Flow<List<Series>> =
        seriesDao.observe(connectionId, categoryId)
            .map { list -> list.collapseAudioVariants({ it.name }, { it.year }, { it.categoryName }).map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    /**
     * Collapses a provider's dubbed + subtitled copies of one title to a
     * single row, **keeping the dubbed copy** (pt-BR audience) and preserving
     * the original ordering. Used for both movies and series.
     *
     * Rows are grouped by [MetadataSanitizer.variantKey] (base name minus
     * marker/year, accent-folded, plus the year). A group collapses when
     * either:
     *  - it mixes a subtitled row with a non-subtitled one (a real dub/sub
     *    split — read from the title *and* the category, since a provider
     *    often marks only one), or
     *  - the shared key carries a year, i.e. same title + same year (a
     *    duplicate), capped at 6 rows so an oddly-tagged catalog isn't over-
     *    pruned.
     * Otherwise the group is left intact, so two unrelated titles that merely
     * share a marker-less name are never hidden. Non-destructive: every row
     * stays in the DB, so a favourite / continue-watching entry pointing at
     * the hidden twin still opens.
     */
    private fun <T> List<T>.collapseAudioVariants(
        name: (T) -> String,
        year: (T) -> String?,
        category: (T) -> String,
    ): List<T> {
        if (size < 2) return this
        val order = ArrayList<String>(size)
        val groups = HashMap<String, MutableList<T>>(size)
        for (row in this) {
            val key = MetadataSanitizer.variantKey(name(row), year(row))
            groups.getOrPut(key) { order.add(key); ArrayList(2) }.add(row)
        }
        if (groups.size == size) return this

        val out = ArrayList<T>(size)
        for (key in order) {
            val group = groups.getValue(key)
            if (group.size == 1) { out += group[0]; continue }

            val tags = group.map { MetadataSanitizer.audioVariantFrom(name(it), category(it)) }
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

    private fun seasonsOf(
        episodes: List<com.auroraplay.iptv.data.database.entity.EpisodeEntity>,
        urlBuilder: XtreamUrlBuilder?,
    ) =
        episodes.groupBy { it.seasonNumber }
            .toSortedMap()
            .map { (seasonNumber, eps) ->
                Season(
                    seasonNumber = seasonNumber,
                    name = "Temporada $seasonNumber",
                    episodes = eps.sortedBy { it.episodeNumber }.map { it.toDomain(urlBuilder) },
                )
            }

    override suspend fun getCachedMovie(connectionId: String, movieId: String): Movie? =
        movieDao.getById(connectionId, movieId)?.toDomain(urlBuilderFor(connectionId))

    override suspend fun getCachedSeries(connectionId: String, seriesId: String): Series? {
        val entity = seriesDao.getById(connectionId, seriesId) ?: return null
        return entity.toDomain().copy(
            seasons = seasonsOf(episodeDao.getForSeries(connectionId, seriesId), urlBuilderFor(connectionId)),
        )
    }

    override suspend fun getSeriesDetail(
        connectionId: String,
        seriesId: String,
        forceRefresh: Boolean,
        allowStaleRefresh: Boolean,
    ): Series? {
        val entity = seriesDao.getById(connectionId, seriesId) ?: return null
        val urlBuilder = urlBuilderFor(connectionId)
        var episodes = episodeDao.getForSeries(connectionId, seriesId)

        val needsFetch = episodes.isEmpty() || forceRefresh ||
            (allowStaleRefresh && episodesAreStale(entity.episodesSyncedAtMillis, System.currentTimeMillis(), EPISODE_TTL_MILLIS))
        if (needsFetch && urlBuilder != null) {
            fetchAndStoreEpisodes(connectionId, seriesId, urlBuilder)?.let { episodes = it }
        }

        val seasons = seasonsOf(episodes, urlBuilder)

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

    override suspend fun refreshSeriesEpisodes(connectionId: String, seriesId: String): List<String>? {
        val urlBuilder = urlBuilderFor(connectionId) ?: return null
        return fetchAndStoreEpisodes(connectionId, seriesId, urlBuilder)?.map { it.id }
    }

    /**
     * One `get_series_info` call, then a transactional replace of the local
     * episodes for [seriesId]. Returns null (and leaves local data untouched)
     * when the provider can't be reached or returns no episodes — never
     * replaces a real episode list with an empty one.
     */
    private suspend fun fetchAndStoreEpisodes(
        connectionId: String,
        seriesId: String,
        urlBuilder: XtreamUrlBuilder,
    ): List<com.auroraplay.iptv.data.database.entity.EpisodeEntity>? {
        val info = runCatching { api.getSeriesInfo(urlBuilder.seriesInfo(seriesId)) }.getOrNull() ?: return null
        val episodes = info.episodes?.flatMap { (seasonKey, eps) ->
            val seasonNumber = seasonKey.toIntOrNull() ?: eps.firstOrNull()?.season ?: 1
            eps.map { it.toEntity(seriesId, connectionId, seasonNumber) }
        }.orEmpty()
        if (episodes.isEmpty()) return null
        episodeDao.replaceForSeries(connectionId, seriesId, episodes)
        seriesDao.touchEpisodesSyncedAt(connectionId, seriesId, System.currentTimeMillis())
        return episodes
    }

    override suspend fun getMovieDetail(connectionId: String, movieId: String): Movie? {
        val urlBuilder = urlBuilderFor(connectionId)
        var entity = movieDao.getById(connectionId, movieId) ?: return null

        // Tier 1: ask the playlist itself (get_vod_info) for the synopsis.
        if (entity.plot.isNullOrBlank()) {
            urlBuilder?.let { ub ->
                runCatching { api.getVodInfo(ub.vodInfo(movieId)) }.getOrNull()?.info?.let { info ->
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

        return entity.toDomain(urlBuilder)
    }

    override suspend fun getLastSyncMillis(connectionId: String): Long? =
        connectionDao.getById(connectionId)?.lastSyncMillis

    override fun search(connectionId: String, query: String): Flow<SearchResults> = flow {
        if (query.isBlank()) {
            emit(SearchResults(emptyList(), emptyList(), emptyList()))
            return@flow
        }
        val urlBuilder = urlBuilderFor(connectionId)
        val channels = channelDao.search(connectionId, query)
            .filterNot { MetadataSanitizer.isRadioCategory(it.categoryName) }
            .map { it.toDomain(urlBuilder) }
        val movies = movieDao.search(connectionId, query)
            .collapseAudioVariants({ it.name }, { it.year }, { it.categoryName }).map { it.toDomain(urlBuilder) }
        val series = seriesDao.search(connectionId, query)
            .collapseAudioVariants({ it.name }, { it.year }, { it.categoryName }).map { it.toDomain() }
        emit(SearchResults(channels, movies, series))
    }

    override suspend fun getShortEpg(
        connectionId: String,
        channelId: String,
    ): Pair<com.auroraplay.iptv.domain.model.EpgProgram?, com.auroraplay.iptv.domain.model.EpgProgram?> =
        pickNowAndNext(getEpgTimeline(connectionId, channelId, limit = 4), System.currentTimeMillis())

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
