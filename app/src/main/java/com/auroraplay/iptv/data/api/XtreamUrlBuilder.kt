package com.auroraplay.iptv.data.api

import com.auroraplay.iptv.core.util.Constants

/**
 * Builds every player_api.php / stream URL for a given connection. Kept in
 * one place so the "Xtream dialect" (query param names, stream path shape)
 * only needs to be adjusted here if a server deviates from spec.
 *
 * Audit #12: the username, password and every dynamic id are percent-encoded
 * (RFC 3986, UTF-8) exactly once, here. A password containing `& # ? / %`, a
 * space or a non-ASCII character no longer breaks the query or the path, and
 * the server decodes it back to the identical bytes — the credential is never
 * semantically altered.
 */
class XtreamUrlBuilder(
    serverUrl: String,
    username: String,
    password: String,
) {
    private val server = serverUrl.trim().trimEnd('/')
    private val user = username.percentEncoded()
    private val pass = password.percentEncoded()

    private val api get() = "$server/player_api.php?username=$user&password=$pass"

    fun auth() = api

    fun liveCategories() = "$api&action=${Constants.ACTION_LIVE_CATEGORIES}"
    fun liveStreams(categoryId: String? = null) =
        "$api&action=${Constants.ACTION_LIVE_STREAMS}" + (categoryId?.let { "&category_id=${it.percentEncoded()}" } ?: "")

    fun vodCategories() = "$api&action=${Constants.ACTION_VOD_CATEGORIES}"
    fun vodStreams(categoryId: String? = null) =
        "$api&action=${Constants.ACTION_VOD_STREAMS}" + (categoryId?.let { "&category_id=${it.percentEncoded()}" } ?: "")
    fun vodInfo(vodId: String) = "$api&action=${Constants.ACTION_VOD_INFO}&vod_id=${vodId.percentEncoded()}"

    fun seriesCategories() = "$api&action=${Constants.ACTION_SERIES_CATEGORIES}"
    fun series(categoryId: String? = null) =
        "$api&action=${Constants.ACTION_SERIES}" + (categoryId?.let { "&category_id=${it.percentEncoded()}" } ?: "")
    fun seriesInfo(seriesId: String) = "$api&action=${Constants.ACTION_SERIES_INFO}&series_id=${seriesId.percentEncoded()}"

    fun shortEpg(streamId: String) = "$api&action=${Constants.ACTION_SHORT_EPG}&stream_id=${streamId.percentEncoded()}"

    fun liveStreamPlayback(streamId: String, extension: String = "m3u8") =
        "$server/live/$user/$pass/${streamId.percentEncoded()}.${extension.percentEncoded()}"

    fun vodStreamPlayback(streamId: String, extension: String) =
        "$server/movie/$user/$pass/${streamId.percentEncoded()}.${extension.percentEncoded()}"

    fun seriesEpisodePlayback(episodeId: String, extension: String) =
        "$server/series/$user/$pass/${episodeId.percentEncoded()}.${extension.percentEncoded()}"
}

private const val HEX = "0123456789ABCDEF"

/**
 * Percent-encodes every byte that is not an RFC 3986 *unreserved* character
 * (`A-Z a-z 0-9 - . _ ~`). Safe for both a query component and a single path
 * segment: space becomes `%20` (never `+`), and `/ & # ? % :` are all encoded,
 * so the value can't escape its slot. UTF-8 first, so non-ASCII round-trips.
 */
internal fun String.percentEncoded(): String {
    val out = StringBuilder(length + 8)
    for (byte in toByteArray(Charsets.UTF_8)) {
        val v = byte.toInt() and 0xFF
        val ch = v.toChar()
        if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~') {
            out.append(ch)
        } else {
            out.append('%').append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
    }
    return out.toString()
}
