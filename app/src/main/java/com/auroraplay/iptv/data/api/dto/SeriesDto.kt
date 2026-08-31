package com.auroraplay.iptv.data.api.dto

import com.google.gson.annotations.SerializedName

data class SeriesDto(
    @SerializedName("num") val num: Int? = null,
    @SerializedName("name") val name: String?,
    @SerializedName("series_id") val seriesId: Int,
    @SerializedName("cover") val cover: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("backdrop_path") val backdropPath: List<String>? = null,
    @SerializedName("last_modified") val lastModified: String? = null,
)

data class SeriesInfoResponseDto(
    @SerializedName("info") val info: SeriesInfoDto?,
    @SerializedName("episodes") val episodes: Map<String, List<EpisodeDto>>?,
    @SerializedName("seasons") val seasons: List<SeasonDto>?,
)

data class SeriesInfoDto(
    @SerializedName("name") val name: String?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("plot") val plot: String?,
    @SerializedName("genre") val genre: String?,
    @SerializedName("releaseDate") val releaseDate: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("backdrop_path") val backdropPath: List<String>?,
)

data class SeasonDto(
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("name") val name: String?,
)

data class EpisodeDto(
    @SerializedName("id") val id: String,
    @SerializedName("episode_num") val episodeNum: Int?,
    @SerializedName("title") val title: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    @SerializedName("season") val season: Int?,
    @SerializedName("info") val info: EpisodeInfoDto?,
)

data class EpisodeInfoDto(
    @SerializedName("plot") val plot: String?,
    @SerializedName("duration") val duration: String?,
    @SerializedName("movie_image") val movieImage: String?,
)
