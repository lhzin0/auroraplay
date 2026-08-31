package com.auroraplay.iptv.data.api.dto

import com.google.gson.annotations.SerializedName

data class VodStreamDto(
    @SerializedName("num") val num: Int? = null,
    @SerializedName("name") val name: String?,
    @SerializedName("stream_id") val streamId: Int,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    @SerializedName("added") val added: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("year") val year: String? = null,
)

data class VodInfoResponseDto(
    @SerializedName("info") val info: VodInfoDto?,
    @SerializedName("movie_data") val movieData: VodStreamDto?,
)

data class VodInfoDto(
    @SerializedName("name") val name: String?,
    @SerializedName("plot") val plot: String?,
    @SerializedName("cast") val cast: String?,
    @SerializedName("director") val director: String?,
    @SerializedName("genre") val genre: String?,
    @SerializedName("releasedate") val releaseDate: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("duration") val duration: String?,
    @SerializedName("backdrop_path") val backdropPath: List<String>?,
    @SerializedName("movie_image") val movieImage: String?,
)
