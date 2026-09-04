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
}
