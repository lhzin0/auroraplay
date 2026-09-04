package com.auroraplay.iptv.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit #7: the episode-list TTL decision. `getSeriesDetail` re-fetches
 * episodes when the cached copy is stale; the boundary logic is pure and
 * covered here.
 */
class EpisodeTtlTest {

    private val ttl = EPISODE_TTL_MILLIS

    @Test
    fun `never fetched is always stale`() {
        assertTrue(episodesAreStale(syncedAtMillis = 0, nowMillis = 1_000, ttlMillis = ttl))
    }

    @Test
    fun `fetched just now is fresh`() {
        val now = 1_000L * ttl
        assertFalse(episodesAreStale(syncedAtMillis = now, nowMillis = now, ttlMillis = ttl))
    }

    @Test
    fun `just under the TTL is still fresh`() {
        val now = 1_000L * ttl
        assertFalse(episodesAreStale(syncedAtMillis = now - (ttl - 1), nowMillis = now, ttlMillis = ttl))
    }

    @Test
    fun `exactly at the TTL is stale`() {
        val now = 1_000L * ttl
        assertTrue(episodesAreStale(syncedAtMillis = now - ttl, nowMillis = now, ttlMillis = ttl))
    }

    @Test
    fun `well past the TTL is stale`() {
        val now = 1_000L * ttl
        assertTrue(episodesAreStale(syncedAtMillis = now - (ttl * 10), nowMillis = now, ttlMillis = ttl))
    }
}
