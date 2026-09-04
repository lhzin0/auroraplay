package com.auroraplay.iptv.player.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit #3c: a Media3 download id must be unique across connections and content
 * kinds. An Xtream stream id is only unique within one (provider, kind), so two
 * playlists that both number a movie "42" would otherwise collide on a single
 * download entry — deleting one would delete the other.
 */
class DownloadKeyTest {

    private val sep = Char(1).toString()

    @Test
    fun `key joins connection, type and content id with the unit separator`() {
        val key = DownloadTracker.downloadKey("conn-a", "MOVIE", "42")
        assertEquals("conn-a" + sep + "MOVIE" + sep + "42", key)
    }

    @Test
    fun `same content id on different connections does not collide`() {
        assertNotEquals(
            DownloadTracker.downloadKey("conn-a", "MOVIE", "42"),
            DownloadTracker.downloadKey("conn-b", "MOVIE", "42"),
        )
    }

    @Test
    fun `same id as a movie and as an episode does not collide`() {
        assertNotEquals(
            DownloadTracker.downloadKey("conn-a", "MOVIE", "42"),
            DownloadTracker.downloadKey("conn-a", "SERIES", "42"),
        )
    }

    @Test
    fun `key is stable for the same inputs`() {
        assertEquals(
            DownloadTracker.downloadKey("conn-a", "SERIES", "s1:e2"),
            DownloadTracker.downloadKey("conn-a", "SERIES", "s1:e2"),
        )
    }

    @Test
    fun `separator is not a character a title or id would realistically contain`() {
        val key = DownloadTracker.downloadKey("conn-a", "MOVIE", "Título: Ação, Vol. 2")
        // Splitting on the separator yields exactly the three original parts —
        // punctuation in the id never widens the split.
        assertEquals(3, key.split(sep).size)
        assertTrue(key.split(sep).last() == "Título: Ação, Vol. 2")
    }
}
