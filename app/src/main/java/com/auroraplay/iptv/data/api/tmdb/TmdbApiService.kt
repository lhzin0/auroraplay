package com.auroraplay.iptv.data.api.tmdb

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Optional metadata enrichment source. Xtream playlists frequently ship with
 * no plot, no backdrop and no genre, so when the server has nothing we look
 * the title up on TMDB (The Movie Database) instead.
 *
 * This requires the user's own free TMDB API key — see
 * Settings > Interface > "Chave TMDB". With no key configured the app simply
 * skips enrichment and shows whatever the playlist provided; nothing breaks.
 */
interface TmdbApiService {

    @GET("search/movie")
    suspend fun searchMovie(
        @Query("api_key") apiKey: String?,
        @Query("query") query: String,
        @Query("year") year: String? = null,
        @Query("language") language: String = "pt-BR",
        @Header("Authorization") authorization: String? = null,
    ): TmdbSearchResponse

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String?,
        @Query("query") query: String,
        @Query("first_air_date_year") year: String? = null,
        @Query("language") language: String = "pt-BR",
        @Header("Authorization") authorization: String? = null,
    ): TmdbSearchResponse

    /** TMDB supplies the official promotional video keys for a title. We only
     * use entries whose site is YouTube, never the playlist's full VOD URL. */
    @GET("movie/{movie_id}/videos")
    suspend fun movieVideos(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String?,
        @Query("language") language: String = "pt-BR",
        @Header("Authorization") authorization: String? = null,
    ): TmdbVideosResponse

    /** Same as [movieVideos] but for a TV series. Only official YouTube
     * trailers/teasers are used — never a full episode. */
    @GET("tv/{tv_id}/videos")
    suspend fun tvVideos(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String?,
        @Query("language") language: String = "pt-BR",
        @Header("Authorization") authorization: String? = null,
    ): TmdbVideosResponse
}

data class TmdbSearchResponse(
    @SerializedName("results") val results: List<TmdbResultDto>?,
)

data class TmdbVideosResponse(
    @SerializedName("results") val results: List<TmdbVideoDto>?,
)

data class TmdbVideoDto(
    @SerializedName("key") val key: String?,
    @SerializedName("site") val site: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("official") val official: Boolean?,
    @SerializedName("iso_639_1") val language: String?,
)

data class TmdbResultDto(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String?,          // movies
    @SerializedName("name") val name: String?,            // tv
    @SerializedName("overview") val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("genre_ids") val genreIds: List<Int>?,
) {
    val displayTitle: String? get() = title ?: name
    val displayYear: String? get() = (releaseDate ?: firstAirDate)?.take(4)

    fun posterUrl(): String? = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    fun backdropUrl(): String? = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
}

/** TMDB's fixed genre ids -> pt-BR names, so we don't need a second request just for genres. */
object TmdbGenres {
    private val map = mapOf(
        28 to "Ação", 12 to "Aventura", 16 to "Animação", 35 to "Comédia",
        80 to "Crime", 99 to "Documentário", 18 to "Drama", 10751 to "Família",
        14 to "Fantasia", 36 to "História", 27 to "Terror", 10402 to "Música",
        9648 to "Mistério", 10749 to "Romance", 878 to "Ficção científica",
        10770 to "Filme para TV", 53 to "Suspense", 10752 to "Guerra", 37 to "Faroeste",
        10759 to "Ação e Aventura", 10762 to "Infantil", 10763 to "Notícias",
        10764 to "Reality show", 10765 to "Ficção e Fantasia", 10766 to "Novela",
        10767 to "Talk show", 10768 to "Guerra e Política",
    )

    fun nameFor(ids: List<Int>?): String? = ids?.firstNotNullOfOrNull { map[it] }
}
