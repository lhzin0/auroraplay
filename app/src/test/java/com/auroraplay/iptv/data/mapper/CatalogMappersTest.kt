package com.auroraplay.iptv.data.mapper

import com.auroraplay.iptv.data.api.XtreamUrlBuilder
import com.auroraplay.iptv.data.database.entity.ChannelEntity
import com.auroraplay.iptv.data.database.entity.EpisodeEntity
import com.auroraplay.iptv.data.database.entity.MovieEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Audit #4: the authenticated playback URL (which embeds the Xtream username +
 * password) is never persisted. The catalog stores only ids + the container
 * extension, and [toDomain] rebuilds the URL from live credentials — or leaves
 * it blank when the connection has none.
 */
class CatalogMappersTest {

    // A credential with no reserved characters, so these assertions stay about
    // the URL *shape*; percent-encoding is covered by XtreamUrlBuilderTest.
    private val builder = XtreamUrlBuilder("http://host:8080", "user", "pass")

    private fun channel() = ChannelEntity(
        id = "42", connectionId = "conn", name = "Canal", logoUrl = null,
        categoryId = "g", categoryName = "Geral", epgChannelId = null,
    )

    private fun movie(ext: String? = "mkv") = MovieEntity(
        id = "42", connectionId = "conn", name = "Filme", posterUrl = null, backdropUrl = null,
        categoryId = "g", categoryName = "Geral", year = null, genre = null, plot = null,
        durationLabel = null, rating = null, containerExtension = ext, audioLabel = null, addedAtMillis = 0,
    )

    private fun episode(ext: String? = "mp4") = EpisodeEntity(
        id = "99", seriesId = "7", connectionId = "conn", seasonNumber = 1, episodeNumber = 1,
        title = "Ep", thumbnailUrl = null, durationLabel = null, plot = null, containerExtension = ext,
    )

    @Test
    fun `channel URL is rebuilt from credentials`() {
        assertEquals("http://host:8080/live/user/pass/42.m3u8", channel().toDomain(builder).streamUrl)
    }

    @Test
    fun `movie URL uses the stored container extension`() {
        assertEquals("http://host:8080/movie/user/pass/42.mkv", movie("mkv").toDomain(builder).streamUrl)
    }

    @Test
    fun `movie URL falls back to mp4 when no extension was stored`() {
        assertEquals("http://host:8080/movie/user/pass/42.mp4", movie(null).toDomain(builder).streamUrl)
    }

    @Test
    fun `episode URL is rebuilt from credentials`() {
        assertEquals("http://host:8080/series/user/pass/99.mp4", episode("mp4").toDomain(builder).streamUrl)
    }

    @Test
    fun `no credentials yields a blank URL, never a broken one`() {
        assertEquals("", channel().toDomain(null).streamUrl)
        assertEquals("", movie().toDomain(null).streamUrl)
        assertEquals("", episode().toDomain(null).streamUrl)
    }

    @Test
    fun `domain objects never expose a stored credential column`() {
        // Sanity: the entity itself carries no URL/credential field to leak.
        val fields = MovieEntity::class.java.declaredFields.map { it.name }
        assertFalse("MovieEntity must not store a stream URL", fields.contains("streamUrl"))
        assertFalse("MovieEntity must not store a password", fields.any { it.contains("pass", ignoreCase = true) })
    }
}
