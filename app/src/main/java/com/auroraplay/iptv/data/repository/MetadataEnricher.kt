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
    suspend fun youtubeTrailerForMovie(rawTitle: String, year: String?): String? {
        val credentials = credentials()
        return lookupTrailer("youtube-trailer:$rawTitle:$year:${credentials?.cacheFingerprint}") {
            credentials ?: return@lookupTrailer null
            val cleanTitle = MetadataSanitizer.title(rawTitle)
            val resolvedYear = year ?: MetadataSanitizer.year(null, rawTitle)
            val movie = pickBest(
                tmdbApi.searchMovie(
                    apiKey = credentials.apiKey,
                    query = cleanTitle,
                    year = resolvedYear,
                    authorization = credentials.authorization,
                ).results,
                cleanTitle,
                resolvedYear,
            )
                ?: return@lookupTrailer null

            pickTrailer(
                tmdbApi.movieVideos(
                    movieId = movie.id,
                    apiKey = credentials.apiKey,
                    authorization = credentials.authorization,
                ).results,
            )?.key ?: pickTrailer(
                tmdbApi.movieVideos(
                    movieId = movie.id,
                    apiKey = credentials.apiKey,
                    language = "en-US",
                    authorization = credentials.authorization,
                ).results,
            )?.key
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
