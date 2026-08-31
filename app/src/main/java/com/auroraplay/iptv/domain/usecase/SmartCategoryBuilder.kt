package com.auroraplay.iptv.domain.usecase

import com.auroraplay.iptv.domain.model.Movie
import com.auroraplay.iptv.domain.model.Series
import javax.inject.Inject

/**
 * Xtream playlists expose provider-authored category names that are noisy and
 * not useful as browsing rails — things like "✅ Apple TV+", "4k [Dual Áudio]
 * [2]", "A Experiência". This builder derives streaming-style rails from the
 * actual content instead, using genre metadata and recency.
 *
 * Rails with no content are never emitted, so the UI can render whatever
 * comes back without checking for emptiness.
 */
class SmartCategoryBuilder @Inject constructor() {

    /** Genre buckets, matched case-insensitively against provider genre strings. */
    private val genreBuckets: List<Pair<String, List<String>>> = listOf(
        "Ação" to listOf("action", "ação", "acao", "aventura", "adventure"),
        "Comédia" to listOf("comedy", "comédia", "comedia"),
        "Drama" to listOf("drama"),
        "Ficção científica" to listOf("sci-fi", "science fiction", "ficção", "ficcao"),
        "Terror" to listOf("horror", "terror", "suspense", "thriller"),
        "Animação" to listOf("animation", "animação", "animacao", "anime"),
        "Documentários" to listOf("documentary", "documentário", "documentario"),
        "Infantil" to listOf("kids", "family", "infantil", "família", "familia"),
        "Romance" to listOf("romance", "romântico", "romantico"),
        "Crime" to listOf("crime", "policial", "mistério", "misterio"),
    )

    /** Cleans a provider title for display: strips quality/audio tags and codes. */
    fun cleanTitle(raw: String): String =
        raw
            .replace(Regex("""\[[^\]]*]"""), " ")          // [L], [Dual Áudio], [4K]
            .replace(Regex("""\((?:19|20)\d{2}\)"""), " ")  // trailing (2015) — year shown separately
            .replace(Regex("""(?i)\b(4k|fhd|hd|sd|dual\s*áudio|dual\s*audio|leg|dub|h265|1080p|720p)\b"""), " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim(' ', '-', '·', '|')
            .ifBlank { raw.trim() }

    private fun matchesBucket(genre: String?, keywords: List<String>): Boolean {
        val g = genre?.lowercase() ?: return false
        return keywords.any { g.contains(it) }
    }

    /**
     * Builds movie rails. [watchedGenres] biases a "Recomendados para você"
     * rail toward what this profile actually watched.
     */
    fun movieRails(
        movies: List<Movie>,
        watchedGenres: Set<String>,
        watchedIds: Set<String>,
    ): List<Rail<Movie>> {
        if (movies.isEmpty()) return emptyList()
        val rails = mutableListOf<Rail<Movie>>()

        val recent = movies.sortedByDescending { it.addedAtMillis }.take(20)
        if (recent.isNotEmpty()) rails += Rail("recent_movies", "Novidades", recent)

        if (watchedGenres.isNotEmpty()) {
            val recommended = movies
                .filter { m -> m.id !in watchedIds && watchedGenres.any { matchesBucket(m.genre, listOf(it.lowercase())) } }
                .take(20)
            if (recommended.isNotEmpty()) rails += Rail("reco_movies", "Recomendados para você", recommended)
        }

        val rated = movies.filter { (it.rating ?: 0.0) > 0 }.sortedByDescending { it.rating }.take(20)
        if (rated.size >= 4) rails += Rail("top_movies", "Em alta", rated)

        genreBuckets.forEach { (label, keywords) ->
            val bucket = movies.filter { matchesBucket(it.genre, keywords) }.take(20)
            if (bucket.size >= 4) rails += Rail("genre_${label.hashCode()}", label, bucket)
        }
        return rails
    }

    fun seriesRails(
        series: List<Series>,
        watchedGenres: Set<String>,
        watchedIds: Set<String>,
    ): List<Rail<Series>> {
        if (series.isEmpty()) return emptyList()
        val rails = mutableListOf<Rail<Series>>()

        val recent = series.sortedByDescending { it.addedAtMillis }.take(20)
        if (recent.isNotEmpty()) rails += Rail("recent_series", "Séries novas", recent)

        if (watchedGenres.isNotEmpty()) {
            val recommended = series
                .filter { s -> s.id !in watchedIds && watchedGenres.any { matchesBucket(s.genre, listOf(it.lowercase())) } }
                .take(20)
            if (recommended.isNotEmpty()) rails += Rail("reco_series", "Séries para você", recommended)
        }

        val rated = series.filter { (it.rating ?: 0.0) > 0 }.sortedByDescending { it.rating }.take(20)
        if (rated.size >= 4) rails += Rail("top_series", "Séries populares", rated)

        genreBuckets.forEach { (label, keywords) ->
            val bucket = series.filter { matchesBucket(it.genre, keywords) }.take(20)
            if (bucket.size >= 4) rails += Rail("sgenre_${label.hashCode()}", label, bucket)
        }
        return rails
    }

    /** Filter chips for the Movies/Series pages, derived from real genres present. */
    fun genreChips(genres: List<String?>): List<Pair<String, List<String>>> =
        genreBuckets.filter { (_, keywords) ->
            genres.any { g -> matchesBucket(g, keywords) }
        }
}

data class Rail<T>(
    val id: String,
    val title: String,
    val items: List<T>,
)
