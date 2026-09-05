package com.auroraplay.iptv.data.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class XmlTvParserTest {

    @Test
    fun `parses a programme with timezone offset`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="globo.br"><display-name>Globo</display-name></channel>
              <programme start="20260904200000 +0000" stop="20260904220000 +0000" channel="globo.br">
                <title>Jornal Nacional</title>
                <desc>Notícias do dia</desc>
              </programme>
            </tv>
        """.trimIndent()

        val programs = XmlTvParser.parse(StringReader(xml))

        assertEquals(1, programs.size)
        val p = programs.single()
        assertEquals("globo.br", p.channelId)
        assertEquals("Jornal Nacional", p.title)
        assertEquals("Notícias do dia", p.description)
        assertTrue(p.endMillis > p.startMillis)
    }

    @Test
    fun `parses a programme with no timezone suffix`() {
        val xml = """
            <tv>
              <programme start="20260904200000" stop="20260904220000" channel="ch1">
                <title>Show</title>
              </programme>
            </tv>
        """.trimIndent()

        val programs = XmlTvParser.parse(StringReader(xml))

        assertEquals(1, programs.size)
        assertEquals("Show", programs.single().title)
        assertEquals("", programs.single().description)
    }

    @Test
    fun `multiple programmes across different channels`() {
        val xml = """
            <tv>
              <programme start="20260904200000 +0000" stop="20260904210000 +0000" channel="ch1">
                <title>A</title>
              </programme>
              <programme start="20260904210000 +0000" stop="20260904220000 +0000" channel="ch2">
                <title>B</title>
              </programme>
            </tv>
        """.trimIndent()

        val programs = XmlTvParser.parse(StringReader(xml))

        assertEquals(2, programs.size)
        assertEquals(setOf("ch1", "ch2"), programs.map { it.channelId }.toSet())
    }

    @Test
    fun `a programme missing the channel attribute is skipped, not fatal`() {
        val xml = """
            <tv>
              <programme start="20260904200000 +0000" stop="20260904210000 +0000">
                <title>Sem canal</title>
              </programme>
              <programme start="20260904210000 +0000" stop="20260904220000 +0000" channel="ch2">
                <title>Valido</title>
              </programme>
            </tv>
        """.trimIndent()

        val programs = XmlTvParser.parse(StringReader(xml))

        assertEquals(1, programs.size)
        assertEquals("Valido", programs.single().title)
    }

    @Test
    fun `a programme with an unparseable timestamp is skipped, not fatal`() {
        val xml = """
            <tv>
              <programme start="not-a-date" stop="also-not-a-date" channel="ch1">
                <title>Ruim</title>
              </programme>
              <programme start="20260904210000 +0000" stop="20260904220000 +0000" channel="ch2">
                <title>Bom</title>
              </programme>
            </tv>
        """.trimIndent()

        val programs = XmlTvParser.parse(StringReader(xml))

        assertEquals(1, programs.size)
        assertEquals("Bom", programs.single().title)
    }

    @Test
    fun `empty guide yields no programmes`() {
        assertEquals(0, XmlTvParser.parse(StringReader("<tv></tv>")).size)
    }
}
