package com.auroraplay.iptv.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/**
 * Audit #12: username / password / ids are percent-encoded exactly once, so a
 * credential containing reserved characters can't break the query or path, and
 * decoding it server-side yields the identical string.
 */
class XtreamUrlBuilderTest {

    private fun decode(s: String) = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

    @Test
    fun `ampersand in the password does not add a query parameter`() {
        val url = XtreamUrlBuilder("http://h", "u", "a&b=c").auth()
        assertEquals("http://h/player_api.php?username=u&password=a%26b%3Dc", url)
        // Exactly two params, and password decodes back to the original.
        val query = url.substringAfter('?').split('&')
        assertEquals(2, query.size)
        assertEquals("a&b=c", decode(query[1].substringAfter('=')))
    }

    @Test
    fun `hash in the password is encoded, not treated as a fragment`() {
        val url = XtreamUrlBuilder("http://h", "u", "p#1").auth()
        assertTrue(url.endsWith("password=p%231"))
        assertEquals("", url.substringAfter('#', ""))
    }

    @Test
    fun `slash space and percent in the password survive a path segment`() {
        val b = XtreamUrlBuilder("http://h", "u", "a/b c%d")
        val url = b.vodStreamPlayback("42", "mp4")
        assertEquals("http://h/movie/u/a%2Fb%20c%25d/42.mp4", url)
        // The password segment is the 3rd after the host and decodes exactly.
        val seg = url.removePrefix("http://h/").split('/')[2]
        assertEquals("a/b c%d", decode(seg))
    }

    @Test
    fun `unicode credential round-trips through UTF-8 percent-encoding`() {
        val url = XtreamUrlBuilder("http://h", "usuário", "sen#ã").auth()
        assertEquals("http://h/player_api.php?username=usu%C3%A1rio&password=sen%23%C3%A3", url)
        assertEquals("usuário", decode(url.substringAfter("username=").substringBefore('&')))
    }

    @Test
    fun `unreserved characters are left untouched`() {
        val url = XtreamUrlBuilder("http://h", "A-z_0.9~", "A-z_0.9~").auth()
        assertEquals("http://h/player_api.php?username=A-z_0.9~&password=A-z_0.9~", url)
    }

    @Test
    fun `a plus sign in the password is encoded, never read as a space`() {
        val url = XtreamUrlBuilder("http://h", "u", "a+b").auth()
        assertTrue(url.endsWith("password=a%2Bb"))
    }

    @Test
    fun `a trailing slash on the server url is trimmed once`() {
        assertEquals(
            "http://h:8080/player_api.php?username=u&password=p",
            XtreamUrlBuilder("http://h:8080/", "u", "p").auth(),
        )
    }
}
