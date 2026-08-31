package com.auroraplay.iptv.data.api

import com.auroraplay.iptv.core.util.Constants

/**
 * Builds every player_api.php / stream URL for a given connection. Kept in
 * one place so the "Xtream dialect" (query param names, stream path shape)
 * only needs to be adjusted here if a server deviates from spec.
 */
class XtreamUrlBuilder(
    private val serverUrl: String,
    private val username: String,
    private val password: String,
) {
    private val api get() = "$serverUrl/player_api.php?username=$username&password=$password"

    fun auth() = api

    fun liveCategories() = "$api&action=${Constants.ACTION_LIVE_CATEGORIES}"
    fun liveStreams(categoryId: String? = null) =
        "$api&action=${Constants.ACTION_LIVE_STREAMS}" + (categoryId?.let { "&category_id=$it" } ?: "")

    fun vodCategories() = "$api&action=${Constants.ACTION_VOD_CATEGORIES}"
    fun vodStreams(categoryId: String? = null) =
        "$api&action=${Constants.ACTION_VOD_STREAMS}" + (categoryId?.let { "&category_id=$it" } ?: "")
    fun vodInfo(vodId: String) = "$api&action=${Constants.ACTION_VOD_INFO}&vod_id=$vodId"

    fun seriesCategories() = "$api&action=${Constants.ACTION_SERIES_CATEGORIES}"
    fun series(categoryId: String? = null) =
        "$api&action=${Constants.ACTION_SERIES}" + (categoryId?.let { "&category_id=$it" } ?: "")
    fun seriesInfo(seriesId: String) = "$api&action=${Constants.ACTION_SERIES_INFO}&series_id=$seriesId"

    fun shortEpg(streamId: String) = "$api&action=${Constants.ACTION_SHORT_EPG}&stream_id=$streamId"

    fun liveStreamPlayback(streamId: String, extension: String = "m3u8") =
        "$serverUrl/live/$username/$password/$streamId.$extension"

    fun vodStreamPlayback(streamId: String, extension: String) =
        "$serverUrl/movie/$username/$password/$streamId.$extension"

    fun seriesEpisodePlayback(episodeId: String, extension: String) =
        "$serverUrl/series/$username/$password/$episodeId.$extension"
}
