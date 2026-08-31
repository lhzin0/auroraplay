package com.auroraplay.iptv.data.repository

import com.auroraplay.iptv.core.util.MetadataSanitizer
import com.auroraplay.iptv.data.api.tmdb.TmdbApiService
import com.auroraplay.iptv.data.api.tmdb.TmdbGenres
import com.auroraplay.iptv.data.api.tmdb.TmdbResultDto
import com.auroraplay.iptv.data.api.tmdb.TmdbVideoDto
import com.auroraplay.iptv.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Metadata gathered from an external source, all fields optional. */
data class EnrichedMetadata(
    val plot: String? = null,
    val backdropUrl: String? = null,
    val posterUrl: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val rating: Double? = null,
)

/**
 * Second-tier metadata source. The playlist (Xtream get_vod_info /
 * get_series_info) is always tried first by ContentRepositoryImpl; this
 * class only runs for titles where the server returned no synopsis.
 *
 * Two sources, tried in order:
 *
 *  1. Wikipedia — the default. Needs no key, no account and no setup, so
 *     synopses work out of the box for every user.
 *  2. TMDB — the app's built-in metadata source. It supplies structured data
 *     such as genre, rating, artwork and official YouTube trailers without
 *     adding a setup step to the viewer's first run.
 *
 * Enrichment is additive: if both miss, the playlist's own metadata stands
 * and nothing about playback is affected.
 */
@Singleton
class MetadataEnricher @Inject constructor(
    private val tmdbApi: TmdbApiService,
    private val wikipedia: WikipediaMetadataSource,
    private val settingsDataStore: SettingsDataStore,
) {
    private val cache = mutableMapOf<String, EnrichedMetadata?>()
    private val trailerCache = mutableMapOf<String, String?>()

    private suspend fun credentials(): TmdbCredentials? {
        val value = settingsDataStore.settingsFlow.first().tmdbApiKey?.trim().orEmpty()
        if (value.isBlank()) return null
        return if (value.startsWith("Bearer ", ignoreCase = true) || value.startsWith("eyJ")) {
            TmdbCredentials(apiKey = null, authorization = "Bearer ${value.substringAfter(' ', value).trim()}")
        } else {
            TmdbCredentials(apiKey = value, authorization = null)
        }
    }

    suspend fun forMovie(rawTitle: String, year: String?): EnrichedMetadata? {
        val credentials = credentials()
        return lookup("movie:$rawTitle:$year:${credentials?.cacheFingerprint}") {
            val cleanTitle = MetadataSanitizer.title(rawTitle)
            val resolvedYear = year ?: MetadataSanitizer.year(null, rawTitle)

            if (credentials != null) {
                val results = tmdbApi.searchMovie(
                    apiKey = credentials.apiKey,
                    query = cleanTitle,
                    year = resolvedYear,
                    authorization = credentials.authorization,
                ).results
                pickBest(results, cleanTitle, resolvedYear)?.toMetadata()?.let { return@lookup it }
            }
            wikipedia.lookup(rawTitle, resolvedYear, isSeries = false)
        }
    }

    suspend fun forSeries(rawTitle: String, year: String?): EnrichedMetadata? {
        val credentials = credentials()
        return lookup("tv:$rawTitle:$year:${credentials?.cacheFingerprint}") {
            val cleanTitle = MetadataSanitizer.title(rawTitle)
            val resolvedYear = year ?: MetadataSanitizer.year(null, rawTitle)

            if (credentials != null) {
                val results = tmdbApi.searchTv(
                    apiKey = credentials.apiKey,
                    query = cleanTitle,
                    year = resolvedYear,
                    authorization = credentials.authorization,
                ).results
                pickBest(results, cleanTitle, resolvedYear)?.toMetadata()?.let { return@lookup it }
            }
            wikipedia.lookup(rawTitle, resolvedYear, isSeries = true)
        }
    }

    /** Resolves a promotional YouTube clip for the details screen. This stays
     * separate from stream playback so a movie URL can never become a trailer. */
    suspend fun youtubeTrailerForMovie(rawTitle: String, year: String?): String? =
        youtubeTrailer(rawTitle, year, isSeries = false)

    /** Series counterpart of [youtubeTrailerForMovie]. TMDB's `tv/{id}/videos`
     * only ever returns marketing clips, so there is no risk of surfacing a
     * full episode as the "trailer". */
    suspend fun youtubeTrailerForSeries(rawTitle: String, year: String?): String? =
        youtubeTrailer(rawTitle, year, isSeries = true)

    private suspend fun youtubeTrailer(rawTitle: String, year: String?, isSeries: Boolean): String? {
        val credentials = credentials() ?: return null
        val kind = if (isSeries) "tv" else "movie"
        return lookupTrailer("youtube-trailer:$kind:$rawTitle:$year:${credentials.cacheFingerprint}") {
            val cleanTitle = MetadataSanitizer.title(rawTitle)
            val resolvedYear = year ?: MetadataSanitizer.year(null, rawTitle)

            suspend fun search(q: String, y: String?, lang: String) =
                if (isSeries) {
                    tmdbApi.searchTv(credentials.apiKey, q, y, lang, credentials.authorization).results
                } else {
                    tmdbApi.searchMovie(credentials.apiKey, q, y, lang, credentials.authorization).results
                }

            // Xtream titles are noisy — try the cleaned title with the year,
            // then without it, then an English-language search, so a match is
            // found for essentially every real title.
            val hit = pickBest(search(cleanTitle, resolvedYear, "pt-BR"), cleanTitle, resolvedYear)
                ?: pickBest(search(cleanTitle, null, "pt-BR"), cleanTitle, null)
                ?: pickBest(search(cleanTitle, null, "en-US"), cleanTitle, null)
                ?: return@lookupTrailer null

            suspend fun videos(lang: String) =
                if (isSeries) {
                    tmdbApi.tvVideos(hit.id, credentials.apiKey, lang, credentials.authorization).results
                } else {
                    tmdbApi.movieVideos(hit.id, credentials.apiKey, lang, credentials.authorization).results
                }

            // pt-BR clips first, then the (usually richer) English set.
            pickTrailer(videos("pt-BR"))?.key
                ?: pickTrailer(videos("en-US"))?.key
        }
    }

    private suspend fun lookup(cacheKey: String, block: suspend () -> EnrichedMetadata?): EnrichedMetadata? {
        if (cache.containsKey(cacheKey)) return cache[cacheKey]
        val result = runCatching { block() }.getOrNull()
        cache[cacheKey] = result
        return result
    }

    private suspend fun lookupTrailer(cacheKey: String, block: suspend () -> String?): String? {
        if (trailerCache.containsKey(cacheKey)) return trailerCache[cacheKey]
        val result = runCatching { block() }.getOrNull()
        trailerCache[cacheKey] = result
        return result
    }

    /**
     * Xtream titles are noisy, so the top TMDB hit isn't always right.
     * Prefer an exact (case-insensitive) title match, then a year match,
     * then fall back to the first result.
     */
    private fun pickBest(results: List<TmdbResultDto>?, title: String, year: String?): TmdbResultDto? {
        if (results.isNullOrEmpty()) return null
        val normalized = title.lowercase().trim()
        results.firstOrNull { it.displayTitle?.lowercase()?.trim() == normalized }?.let { return it }
        if (year != null) {
            results.firstOrNull { it.displayYear == year }?.let { return it }
        }
        return results.first()
    }

    private fun pickTrailer(videos: List<TmdbVideoDto>?): TmdbVideoDto? =
        videos
            .orEmpty()
            .asSequence()
            .filter { it.site.equals("YouTube", ignoreCase = true) }
            .filter { it.key?.matches(YOUTUBE_VIDEO_ID) == true }
            .sortedWith(
                compareByDescending<TmdbVideoDto> { it.type.equals("Trailer", ignoreCase = true) }
                    .thenByDescending { it.official == true }
                    .thenByDescending { it.language.equals("pt", ignoreCase = true) }
                    .thenByDescending { it.type.equals("Teaser", ignoreCase = true) },
            )
            .firstOrNull()

    private fun TmdbResultDto.toMetadata() = EnrichedMetadata(
        plot = MetadataSanitizer.text(overview),
        backdropUrl = backdropUrl(),
        posterUrl = posterUrl(),
        genre = TmdbGenres.nameFor(genreIds),
        year = displayYear,
        rating = voteAverage?.takeIf { it > 0 },
    )

    private companion object {
        val YOUTUBE_VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    }
}

private data class TmdbCredentials(
    val apiKey: String?,
    val authorization: String?,
) {
    val cacheFingerprint: Int = (apiKey ?: authorization).hashCode()
}
