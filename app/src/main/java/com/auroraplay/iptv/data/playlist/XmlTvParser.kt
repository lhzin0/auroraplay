package com.auroraplay.iptv.data.playlist

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.Reader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory

/** One `<programme>` entry from an XMLTV guide. [channelId] is the raw
 * `channel` attribute — the same id space as a playlist's `tvg-id` and
 * [com.auroraplay.iptv.data.database.entity.ChannelEntity.epgChannelId]. */
data class XmlTvProgram(
    val channelId: String,
    val title: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
)

/**
 * Parses the XMLTV guide format:
 * ```xml
 * <tv>
 *   <channel id="globo.br"><display-name>Globo</display-name></channel>
 *   <programme start="20260904200000 +0000" stop="20260904220000 +0000" channel="globo.br">
 *     <title>Jornal Nacional</title>
 *     <desc>Notícias do dia</desc>
 *   </programme>
 * </tv>
 * ```
 * SAX (event-based), not DOM — a multi-day, multi-channel guide can run to
 * tens of MB, and this never holds more than the one `<programme>` currently
 * being read in memory. A `<programme>` this parser can't make sense of
 * (unparseable timestamp, no channel attribute) is skipped, not fatal — the
 * rest of a guide with one malformed entry is still worth having.
 */
object XmlTvParser {
    // XMLTV allows an optional " +ZZZZ" / " Z" timezone suffix; both patterns
    // are tried since providers are inconsistent about including it.
    private fun dateFormat(pattern: String) =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun parseTimestamp(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withOffset = runCatching { dateFormat("yyyyMMddHHmmss Z").parse(trimmed)?.time }.getOrNull()
        if (withOffset != null) return withOffset
        val bare = trimmed.take(14)
        return runCatching { dateFormat("yyyyMMddHHmmss").parse(bare)?.time }.getOrNull()
    }

    fun parse(reader: Reader): List<XmlTvProgram> {
        val results = mutableListOf<XmlTvProgram>()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            // Guide files are user-supplied content from a third-party
            // provider, not trusted local config — never resolve external
            // entities/DTDs (XXE hardening).
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val parser = factory.newSAXParser()

        val handler = object : DefaultHandler() {
            var inTitle = false
            var inDesc = false
            var currentChannelId: String? = null
            var currentStart: Long? = null
            var currentEnd: Long? = null
            var titleBuilder = StringBuilder()
            var descBuilder = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                when (qName) {
                    "programme" -> {
                        currentChannelId = attributes.getValue("channel")?.trim()?.takeIf { it.isNotBlank() }
                        currentStart = attributes.getValue("start")?.let(::parseTimestamp)
                        currentEnd = attributes.getValue("stop")?.let(::parseTimestamp)
                        titleBuilder = StringBuilder()
                        descBuilder = StringBuilder()
                    }
                    "title" -> inTitle = true
                    "desc" -> inDesc = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                when {
                    inTitle -> titleBuilder.append(ch, start, length)
                    inDesc -> descBuilder.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName) {
                    "title" -> inTitle = false
                    "desc" -> inDesc = false
                    "programme" -> {
                        val channelId = currentChannelId
                        val start = currentStart
                        val end = currentEnd
                        if (channelId != null && start != null && end != null && end > start) {
                            results += XmlTvProgram(
                                channelId = channelId,
                                title = titleBuilder.toString().trim(),
                                description = descBuilder.toString().trim(),
                                startMillis = start,
                                endMillis = end,
                            )
                        }
                    }
                }
            }
        }

        parser.parse(InputSource(reader), handler)
        return results
    }
}
