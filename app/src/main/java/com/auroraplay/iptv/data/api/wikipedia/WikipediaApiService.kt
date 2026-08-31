package com.auroraplay.iptv.data.api.wikipedia

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Wikipedia's public REST/Action API.
 *
 * Chosen as the default metadata source because it needs no API key, no
 * account and no configuration — the user installs the app and synopses
 * simply appear. That is the whole point: asking a viewer to register at a
 * developer site and paste a token is a barrier most people will never pass.
 *
 * Trade-offs, honestly: coverage is strong for well-known films and series
 * and weak for obscure or very recent titles, and the text is encyclopaedic
 * rather than marketing copy — it describes the work, sometimes including
 * plot details a trailer wouldn't reveal. Enrichment stays strictly
 * additive, so a miss just leaves the playlist's own metadata in place.
 */
interface WikipediaApiService {

    /**
     * Full-text search restricted to the given language wiki. Returns page
     * titles ranked by relevance; the top hit is then fetched for a summary.
     */
    @GET
    suspend fun search(@Url url: String): WikiSearchResponseDto

    /** Page summary: short extract plus a thumbnail, when the page has one. */
    @GET
    suspend fun summary(@Url url: String): WikiSummaryDto
}

/** Builds the endpoint URLs for a given language edition (pt, then en). */
object WikipediaUrls {
    fun search(lang: String, query: String, limit: Int = 5): String =
        "https://$lang.wikipedia.org/w/api.php" +
            "?action=query&list=search&format=json&utf8=1&srlimit=$limit" +
            "&srsearch=${encode(query)}"

    fun summary(lang: String, pageTitle: String): String =
        "https://$lang.wikipedia.org/api/rest_v1/page/summary/${encode(pageTitle)}"

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

data class WikiSearchResponseDto(
    @SerializedName("query") val query: WikiSearchQueryDto?,
)

data class WikiSearchQueryDto(
    @SerializedName("search") val search: List<WikiSearchHitDto>?,
)

data class WikiSearchHitDto(
    @SerializedName("title") val title: String?,
    @SerializedName("snippet") val snippet: String?,
)

data class WikiSummaryDto(
    @SerializedName("title") val title: String?,
    @SerializedName("extract") val extract: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("thumbnail") val thumbnail: WikiImageDto?,
    @SerializedName("originalimage") val originalImage: WikiImageDto?,
    @SerializedName("type") val type: String?,
)

data class WikiImageDto(
    @SerializedName("source") val source: String?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?,
)
