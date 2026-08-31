package com.auroraplay.iptv.data.repository

import com.auroraplay.iptv.core.util.MetadataSanitizer
import com.auroraplay.iptv.data.api.wikipedia.WikipediaApiService
import com.auroraplay.iptv.data.api.wikipedia.WikipediaUrls
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zero-configuration metadata source backed by Wikipedia.
 *
 * Searches the Portuguese wiki first and falls back to English, since many
 * international titles only have an English article. A result is only
 * accepted when the article looks like it is actually about the title being
 * matched — otherwise a search for a generic name would happily return the
 * article about an unrelated word.
 */
@Singleton
class WikipediaMetadataSource @Inject constructor(
    private val api: WikipediaApiService,
) {
    /** Words that mark an article as being about a film or series. */
    private val mediaHints = listOf(
        "filme", "film", "série", "serie", "series", "televisão", "television",
        "temporada", "season", "animação", "animated", "sitcom", "minissérie",
    )

    suspend fun lookup(rawTitle: String, year: String?, isSeries: Boolean): EnrichedMetadata? {
        val clean = MetadataSanitizer.title(rawTitle)
        if (clean.isBlank()) return null

        for (lang in listOf("pt", "en")) {
            val result = runCatching { searchIn(lang, clean, year, isSeries) }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private suspend fun searchIn(
        lang: String,
        cleanTitle: String,
        year: String?,
        isSeries: Boolean,
    ): EnrichedMetadata? {
        // Bias the query toward the right kind of article.
        val qualifier = if (isSeries) "série de televisão" else "filme"
        val query = listOfNotNull(cleanTitle, year, qualifier).joinToString(" ")

        val hits = api.search(WikipediaUrls.search(lang, query)).query?.search.orEmpty()
        if (hits.isEmpty()) return null

        val candidate = hits.firstOrNull { hit ->
            val title = hit.title?.lowercase() ?: return@firstOrNull false
            // The article title must still contain the work's name; otherwise
            // the search drifted to a merely related page.
            title.contains(cleanTitle.lowercase().take(20))
        } ?: hits.first()

        val pageTitle = candidate.title ?: return null
        val summary = api.summary(WikipediaUrls.summary(lang, pageTitle))

        val extract = summary.extract?.trim().orEmpty()
        if (extract.length < 40) return null

        // Reject disambiguation pages and articles with no media signal at all.
        if (summary.type == "disambiguation") return null
        val descriptor = "${summary.description.orEmpty()} $extract".lowercase()
        if (mediaHints.none { descriptor.contains(it) }) return null

        return EnrichedMetadata(
            plot = extract,
            posterUrl = summary.originalImage?.source ?: summary.thumbnail?.source,
            backdropUrl = summary.originalImage?.source,
            year = year,
        )
    }
}
