package com.auroraplay.iptv.data.playlist

/** One `#EXTINF` entry plus the stream URL line that follows it. */
data class M3uEntry(
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val groupTitle: String?,
    /** `tvg-id` — matched against [com.auroraplay.iptv.data.database.entity.ChannelEntity.epgChannelId]
     * when the connection also has an XMLTV guide imported. */
    val tvgId: String?,
)

/**
 * Parses the M3U ("extended M3U") format IPTV providers distribute playlists
 * in — no library needed, it's plain text:
 * ```
 * #EXTM3U
 * #EXTINF:-1 tvg-id="globo.br" tvg-logo="http://.../globo.png" group-title="Abertos",Globo HD
 * http://provider.example/live/user/pass/12345.ts
 * ```
 * Deliberately tolerant: a provider's export is never perfectly well-formed
 * (missing attributes, stray blank lines, `#EXTGRP` instead of `group-title`,
 * Windows line endings) — a line this parser can't make sense of is skipped
 * rather than aborting the whole file, since one bad entry in a list of
 * thousands shouldn't cost the user everything else in it.
 */
object M3uPlaylistParser {
    private val attrRegex = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")

    fun parse(text: String): List<M3uEntry> {
        val lines = text.lineSequence().map { it.trim().trimEnd('\r') }
        val entries = mutableListOf<M3uEntry>()
        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null
        var pendingTvgId: String? = null

        for (line in lines) {
            if (line.isBlank()) continue
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    pendingLogo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    pendingGroup = attrs["group-title"]?.takeIf { it.isNotBlank() }
                    pendingTvgId = (attrs["tvg-id"] ?: attrs["tvg-chno"])?.takeIf { it.isNotBlank() }
                    // Everything after the last comma on the #EXTINF line is the
                    // display name — attribute values are quoted so a comma
                    // inside one (rare, but seen) can't be mistaken for this.
                    pendingName = line.substringAfterLast(',').trim().ifBlank { null }
                }
                line.startsWith("#EXTGRP", ignoreCase = true) -> {
                    pendingGroup = line.substringAfter(':', "").trim().ifBlank { pendingGroup }
                }
                line.startsWith("#") -> {
                    // #EXTM3U, #EXTVLCOPT, #KODIPROP, etc. — not a stream line.
                }
                else -> {
                    val name = pendingName
                    if (!name.isNullOrBlank() && line.isNotBlank()) {
                        entries += M3uEntry(
                            name = name,
                            streamUrl = line,
                            logoUrl = pendingLogo,
                            groupTitle = pendingGroup,
                            tvgId = pendingTvgId,
                        )
                    }
                    pendingName = null
                    pendingLogo = null
                    pendingGroup = null
                    pendingTvgId = null
                }
            }
        }
        return entries
    }
}
