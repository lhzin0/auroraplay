package com.auroraplay.iptv.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataSanitizerTest {

    @Test
    fun `title strips a trailing decorative marker some providers tack on`() {
        assertEquals("Silo", MetadataSanitizer.title("Silo *"))
        assertEquals("Silo", MetadataSanitizer.title("Silo*"))
        assertEquals("Duna", MetadataSanitizer.title("Duna •"))
    }

    @Test
    fun `title still strips the trailing year and bracketed tags as before`() {
        assertEquals("Matrix", MetadataSanitizer.title("Matrix (1999)"))
        assertEquals("Wonka", MetadataSanitizer.title("Wonka [L]"))
    }

    @Test
    fun `title falls back to the trimmed original when stripping empties it`() {
        assertEquals("*", MetadataSanitizer.title("*"))
    }

    @Test
    fun `title leaves an ordinary title with no provider decoration alone`() {
        assertEquals("Duna Parte Dois", MetadataSanitizer.title("Duna Parte Dois"))
    }

    @Test
    fun `durationFromMillis formats hours and minutes like duration does`() {
        assertEquals("52min", MetadataSanitizer.durationFromMillis(52 * 60_000L + 1_000L))
        assertEquals("1h 30min", MetadataSanitizer.durationFromMillis(90 * 60_000L))
        assertEquals("2h 0min", MetadataSanitizer.durationFromMillis(120 * 60_000L))
    }

    @Test
    fun `durationFromMillis is null for zero or negative input`() {
        assertEquals(null, MetadataSanitizer.durationFromMillis(0L))
        assertEquals(null, MetadataSanitizer.durationFromMillis(-1_000L))
    }
}
