package com.auroraplay.iptv.data.api

import com.auroraplay.iptv.data.api.dto.AuthResponseDto
import com.auroraplay.iptv.data.api.dto.CategoryDto
import com.auroraplay.iptv.data.api.dto.LiveStreamDto
import com.auroraplay.iptv.data.api.dto.SeriesDto
import com.auroraplay.iptv.data.api.dto.SeriesInfoResponseDto
import com.auroraplay.iptv.data.api.dto.ShortEpgResponseDto
import com.auroraplay.iptv.data.api.dto.VodInfoResponseDto
import com.auroraplay.iptv.data.api.dto.VodStreamDto
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Thin wrapper around the Xtream Codes `player_api.php` endpoint. The base
 * server URL changes per user connection, so every call receives the full
 * URL explicitly instead of relying on a fixed Retrofit base URL.
 */
interface XtreamApiService {

    @GET
    suspend fun authenticate(@Url url: String): AuthResponseDto

    @GET
    suspend fun getLiveCategories(@Url url: String): List<CategoryDto>

    @GET
    suspend fun getLiveStreams(@Url url: String): List<LiveStreamDto>

    @GET
    suspend fun getVodCategories(@Url url: String): List<CategoryDto>

    @GET
    suspend fun getVodStreams(@Url url: String): List<VodStreamDto>

    @GET
    suspend fun getVodInfo(@Url url: String): VodInfoResponseDto

    @GET
    suspend fun getSeriesCategories(@Url url: String): List<CategoryDto>

    @GET
    suspend fun getSeries(@Url url: String): List<SeriesDto>

    @GET
    suspend fun getSeriesInfo(@Url url: String): SeriesInfoResponseDto

    @GET
    suspend fun getShortEpg(@Url url: String, @Query("limit") limit: Int = 2): ShortEpgResponseDto
}
