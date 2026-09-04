package com.auroraplay.iptv.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit #6: the "new episode available" decision. The worker itself needs an
 * Android context + network, but the two rules that decide whether to notify
 * are pure and covered here — a series with no episodes, a series that gained
 * episodes, and two connections that must not share state.
 */
class EpisodeCountStoreLogicTest {

    @Test
    fun `first check never notifies`() {
        assertFalse(hasNewEpisodes(current = setOf("e1", "e2"), known = null))
    }

    @Test
    fun `series with no episodes never notifies`() {
        assertFalse(hasNewEpisodes(current = emptySet(), known = null))
        assertFalse(hasNewEpisodes(current = emptySet(), known = emptySet()))
    }

    @Test
    fun `no change does not notify`() {
        assertFalse(hasNewEpisodes(current = setOf("e1", "e2"), known = setOf("e1", "e2")))
    }

    @Test
    fun `a genuinely new episode id notifies`() {
        assertTrue(hasNewEpisodes(current = setOf("e1", "e2", "e3"), known = setOf("e1", "e2")))
    }

    @Test
    fun `an episode added while another is removed still notifies`() {
        // count is unchanged (2 -> 2) but e3 is new — a bare count would miss it.
        assertTrue(hasNewEpisodes(current = setOf("e2", "e3"), known = setOf("e1", "e2")))
    }

    @Test
    fun `only removing episodes does not notify`() {
        assertFalse(hasNewEpisodes(current = setOf("e1"), known = setOf("e1", "e2")))
    }

    @Test
    fun `the same series id on two connections is tracked separately`() {
        assertNotEquals(
            episodeIdsKeyName("conn-A", "512"),
            episodeIdsKeyName("conn-B", "512"),
        )
        // and stable for the same inputs
        assertEquals(
            episodeIdsKeyName("conn-A", "512"),
            episodeIdsKeyName("conn-A", "512"),
        )
    }
}
