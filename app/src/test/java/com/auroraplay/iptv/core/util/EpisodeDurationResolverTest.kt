package com.auroraplay.iptv.core.util

import com.auroraplay.iptv.core.util.EpisodeDurationResolver.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeDurationResolverTest {

    // ---- runtimeLabel ---------------------------------------------------

    @Test
    fun `a player measurement wins over TMDB and the static label`() {
        assertEquals(
            "52min",
            EpisodeDurationResolver.runtimeLabel(
                staticLabel = "21min",
                measuredMillis = 52 * 60_000L + 4_000L,
                tmdbMinutes = 47,
                trustStatic = true,
            ),
        )
    }

    @Test
    fun `TMDB runtime is used when there is no player measurement`() {
        assertEquals(
            "47min",
            EpisodeDurationResolver.runtimeLabel(
                staticLabel = "21min",
                measuredMillis = null,
                tmdbMinutes = 47,
                trustStatic = true,
            ),
        )
    }

    @Test
    fun `a sub-minute measurement is ignored as a stray partial write`() {
        assertEquals(
            "47min",
            EpisodeDurationResolver.runtimeLabel(
                staticLabel = "21min",
                measuredMillis = 30_000L,
                tmdbMinutes = 47,
                trustStatic = true,
            ),
        )
    }

    @Test
    fun `the static label shows only while the season is still trusted`() {
        assertEquals(
            "21min",
            EpisodeDurationResolver.runtimeLabel("21min", null, null, trustStatic = true),
        )
        assertNull(
            EpisodeDurationResolver.runtimeLabel("21min", null, null, trustStatic = false),
        )
    }

    @Test
    fun `nothing to show returns null`() {
        assertNull(EpisodeDurationResolver.runtimeLabel(null, null, null, trustStatic = true))
        assertNull(EpisodeDurationResolver.runtimeLabel("  ", null, null, trustStatic = true))
        assertNull(EpisodeDurationResolver.runtimeLabel(null, null, 0, trustStatic = true))
    }

    // ---- realMinutes --------------------------------------------------

    @Test
    fun `realMinutes prefers the measurement, floors sub-minute, falls back to TMDB`() {
        assertEquals(52, EpisodeDurationResolver.realMinutes(52 * 60_000L + 59_000L, 47))
        assertEquals(47, EpisodeDurationResolver.realMinutes(20_000L, 47))
        assertEquals(47, EpisodeDurationResolver.realMinutes(null, 47))
        assertNull(EpisodeDurationResolver.realMinutes(null, null))
        assertNull(EpisodeDurationResolver.realMinutes(null, 0))
    }

    // ---- seasonStaticLabelsTrustworthy ------------------------------

    @Test
    fun `a season with no real runtime yet stays trusted`() {
        assertTrue(
            EpisodeDurationResolver.seasonStaticLabelsTrustworthy(
                listOf(Sample("21min", null), Sample("21min", null)),
            ),
        )
    }

    @Test
    fun `one wildly-understated label makes the whole season untrusted`() {
        assertFalse(
            EpisodeDurationResolver.seasonStaticLabelsTrustworthy(
                listOf(
                    Sample("21min", null),
                    Sample("21min", 52), // 52 / 21 = 2.47x
                    Sample("21min", null),
                ),
            ),
        )
    }

    @Test
    fun `one wildly-overstated label makes the whole season untrusted`() {
        assertFalse(
            EpisodeDurationResolver.seasonStaticLabelsTrustworthy(
                listOf(Sample("2h 0min", 40)), // 40 / 120 = 0.33x
            ),
        )
    }

    @Test
    fun `a close-enough real runtime keeps the season trusted`() {
        assertTrue(
            EpisodeDurationResolver.seasonStaticLabelsTrustworthy(
                listOf(
                    Sample("50min", 52), // 1.04x
                    Sample("50min", 46), // 0.92x
                ),
            ),
        )
    }

    @Test
    fun `an unparseable label can't be judged, so it doesn't break trust on its own`() {
        assertTrue(
            EpisodeDurationResolver.seasonStaticLabelsTrustworthy(
                listOf(Sample(null, 52), Sample("", 52)),
            ),
        )
    }
}
