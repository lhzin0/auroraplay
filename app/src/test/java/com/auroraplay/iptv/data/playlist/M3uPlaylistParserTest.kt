package com.auroraplay.iptv.data.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uPlaylistParserTest {

    @Test
    fun `parses a well formed entry with all attributes`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="globo.br" tvg-logo="http://x/globo.png" group-title="Abertos",Globo HD
            http://provider.example/live/user/pass/1.ts
        """.trimIndent()

        val entries = M3uPlaylistParser.parse(m3u)

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("Globo HD", entry.name)
        assertEquals("http://provider.example/live/user/pass/1.ts", entry.streamUrl)
        assertEquals("http://x/globo.png", entry.logoUrl)
        assertEquals("Abertos", entry.groupTitle)
        assertEquals("globo.br", entry.tvgId)
    }

    @Test
    fun `parses multiple entries in one playlist`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="News",Canal A
            http://x/a.ts
            #EXTINF:-1 group-title="Sports",Canal B
            http://x/b.ts
        """.trimIndent()

        val entries = M3uPlaylistParser.parse(m3u)

        assertEquals(2, entries.size)
        assertEquals("Canal A", entries[0].name)
        assertEquals("News", entries[0].groupTitle)
        assertEquals("Canal B", entries[1].name)
        assertEquals("Sports", entries[1].groupTitle)
    }

    @Test
    fun `entry with no attributes still parses using just the name`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Canal Sem Atributos
            http://x/c.ts
        """.trimIndent()

        val entries = M3uPlaylistParser.parse(m3u)

        assertEquals(1, entries.size)
        assertEquals("Canal Sem Atributos", entries.single().name)
        assertNull(entries.single().tvgId)
        assertNull(entries.single().groupTitle)
        assertNull(entries.single().logoUrl)
    }

    @Test
    fun `a stream line with no preceding EXTINF is skipped, not fatal`() {
        val m3u = """
            #EXTM3U
            http://orphan.example/x.ts
            #EXTINF:-1,Canal Valido
            http://x/valid.ts
        """.trimIndent()

        val entries = M3uPlaylistParser.parse(m3u)

        assertEquals(1, entries.size)
        assertEquals("Canal Valido", entries.single().name)
    }

    @Test
    fun `EXTGRP sets the group when group-title attribute is absent`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Canal X
            #EXTGRP:Infantil
            http://x/x.ts
        """.trimIndent()

        val entries = M3uPlaylistParser.parse(m3u)

        assertEquals(1, entries.size)
        assertEquals("Infantil", entries.single().groupTitle)
    }

    @Test
    fun `blank input yields no entries`() {
        assertEquals(0, M3uPlaylistParser.parse("").size)
        assertEquals(0, M3uPlaylistParser.parse("#EXTM3U\n\n\n").size)
    }
}
