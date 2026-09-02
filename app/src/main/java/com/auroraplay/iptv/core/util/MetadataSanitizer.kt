package com.auroraplay.iptv.core.util

/**
 * Xtream servers are notoriously inconsistent: they return "00:00:00" or "0"
 * for unknown durations, empty strings instead of null, category names
 * decorated with provider branding ("➤# DRAMA", "|BR| FILMES"), and years
 * embedded in the title ("Minions (2015)"). Everything the UI displays goes
 * through here first so a messy playlist never produces messy screens.
 */
object MetadataSanitizer {

    private val junkDurations = setOf("00:00:00", "0:00:00", "00:00", "0", "", "null")

    /** Returns a display-ready duration, or null when the server didn't really provide one. */
    fun duration(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.lowercase() in junkDurations) return null

        // Xtream sometimes sends plain minutes ("122") instead of hh:mm:ss.
        value.toIntOrNull()?.let { minutes ->
            if (minutes <= 0) return null
            val h = minutes / 60
            val m = minutes % 60
            return if (h > 0) "${h}h ${m}min" else "${m}min"
        }

        // Normalise hh:mm:ss -> "2h 3min", dropping a leading zero hour.
        val parts = value.split(":").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size == 3) {
            val (h, m, _) = parts
            if (h == 0 && m == 0) return null
            return if (h > 0) "${h}h ${m}min" else "${m}min"
        }
        if (parts.size == 2) {
            val (m, _) = parts
            return if (m > 0) "${m}min" else null
        }
        return value.ifBlank { null }
    }

    /**
     * Strips provider decorations from category/genre names so chips and
     * badges read cleanly: "➤# DRAMA" -> "Drama", "|BR| FILMES" -> "Filmes".
     */
    fun categoryName(raw: String?): String? {
        var value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null

        // Leading country/provider tags: |BR|, [BR], (BR), BR:
        value = value.replace(Regex("^[\\[|(<]{1,2}[A-Za-z0-9 ._-]{1,12}[\\]|)>]{1,2}\\s*"), "")
        // Decorative symbols commonly used as list separators by providers.
        value = value.replace(Regex("[➤►▶●•★☆♦◆※#*~_|]+"), " ")
        // Trailing "[2]" / "(12)" item-count markers providers append.
        value = value.replace(Regex("\\s*[\\[(]\\s*\\d{1,4}\\s*[\\])]\\s*$"), "")
        // A bare trailing year ("Lançamentos 2026", "Estreias 2025") — the UI
        // shows the real release year separately, so this is just noise.
        value = value.replace(Regex("\\s+(?:19|20)\\d{2}\\s*$"), "")
        value = value.replace(Regex("\\s{2,}"), " ").trim(' ', '-', ':', '.')

        if (value.isEmpty()) return null
        return titleCase(value)
    }

    /** "DRAMA" -> "Drama", but leaves already-mixed-case names alone. */
    private fun titleCase(value: String): String {
        if (value != value.uppercase()) return value
        return value.lowercase().split(" ").joinToString(" ") { word ->
            if (word.length <= 2) word.uppercase()
            else word.replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Extracts a 4-digit year from a title or release date when the dedicated
     * field is missing, e.g. "Minions (2015)" -> "2015".
     */
    fun year(explicit: String?, title: String? = null, releaseDate: String? = null): String? {
        explicit?.trim()?.takeIf { it.length == 4 && it.toIntOrNull() != null }?.let { return it }
        releaseDate?.trim()?.take(4)?.takeIf { it.toIntOrNull() != null }?.let { return it }
        title?.let { Regex("\\((19|20)\\d{2}\\)").find(it)?.value?.trim('(', ')')?.let { y -> return y } }
        return null
    }

    /**
     * Cleans a title for display: removes the trailing "(2015)" (shown
     * separately as a metadata chip) and provider tags like "[L]" / "|BR|".
     */
    fun title(raw: String?): String {
        var value = raw?.trim().orEmpty()
        value = value.replace(Regex("\\s*\\((19|20)\\d{2}\\)\\s*$"), "")
        value = value.replace(Regex("\\s*[\\[|(]\\s*[A-Za-z0-9]{1,3}\\s*[\\]|)]\\s*"), " ")
        value = value.replace(Regex("\\s{2,}"), " ").trim()
        return value.ifEmpty { raw?.trim().orEmpty() }
    }

    // ---- Dubbed / subtitled variant handling -------------------------------
    //
    // Xtream providers routinely list the *same* movie twice, once dubbed and
    // once subtitled, tagging the title: "Duna - DUBLADO" / "Duna LEG",
    // "Oppenheimer (Legendado)", "Wonka [D]". The catalog collapses these to
    // one tile and the player offers the twin as a "Dublado / Legendado"
    // audio choice.

    enum class AudioVariant { DUBLADO, LEGENDADO, DESCONHECIDO }

    // Unambiguous full words — safe to strip even when only space-separated
    // ("Duna Dublado", "Wonka Legendado").
    private const val STRONG_MARKER = "dublado|dublada|dublagem|legendado|legendada|dual\\s?[aá]udio|dual\\s?audio"
    // Short abbreviations / common words — only stripped when bracketed or
    // after a real separator, so a genuine title word ("Break a Leg", "Hino
    // Nacional", "Nightclub", initials) is never eaten.
    private const val WEAK_MARKER = "dub|leg|legenda|sub|subbed|dual|nacional|nac|l|d"

    // Trailing-only on purpose: a leading/median match risks a false positive.
    private val TRAILING_AUDIO_MARKER = Regex(
        "(?i)(?:" +
            "\\s*[\\[({|/–-]+\\s*($STRONG_MARKER|$WEAK_MARKER)\\s*[\\])}|/–-]*" +
            "|\\s+($STRONG_MARKER)" +
            ")\\s*$"
    )
    private val DUB_TOKENS = setOf("dublado", "dublada", "dublagem", "dub", "dual", "dualaudio", "nacional", "nac", "d")

    /** Removes a trailing "- DUBLADO" / "(Legendado)" / "[L]"-style tag (up to two). */
    fun stripAudioMarkers(raw: String?): String {
        var value = raw?.trim().orEmpty()
        repeat(2) {
            val stripped = value.replace(TRAILING_AUDIO_MARKER, "").trim(' ', '-', '–', '|', '/', '.', ':')
            if (stripped == value || stripped.isEmpty()) return@repeat
            value = stripped
        }
        return value.ifEmpty { raw?.trim().orEmpty() }
    }

    /** Classifies a raw provider title as dubbed / subtitled / unknown (trailing tag only). */
    fun audioVariant(raw: String?): AudioVariant {
        val m = TRAILING_AUDIO_MARKER.find(raw?.trim().orEmpty()) ?: return AudioVariant.DESCONHECIDO
        val token = m.groupValues[1].ifBlank { m.groupValues[2] }
            .lowercase().replace(" ", "").replace("á", "a")
        return if (token in DUB_TOKENS) AudioVariant.DUBLADO else AudioVariant.LEGENDADO
    }

    // A dub/sub hint anywhere in the text — whole token or bracketed short
    // form. Loose because it is fed the title AND the (short, controlled)
    // category name; the word boundaries keep a real title word from being
    // mistaken for a marker.
    private val LEG_HINT = Regex(
        "(?i)(?<![\\p{L}])(legendad[oa]s?|legendas?|leg|subtitulad[oa]s?|subtitle[ds]?|subbed|sub|\\[\\s*l\\s*]|\\(\\s*l\\s*\\)|\\[\\s*leg\\s*])(?![\\p{L}])"
    )
    private val DUB_HINT = Regex(
        "(?i)(?<![\\p{L}])(dublad[oa]s?|dublagem|dubbed|dub|nacion(?:al|ais)|dual\\s*[aá]?udio?|dual|\\[\\s*d\\s*]|\\(\\s*d\\s*\\)|\\[\\s*dub\\s*])(?![\\p{L}])"
    )

    /** "Legendado" when [audioVariantFrom] says so, else null — a ready label
     * for the detail page so a viewer isn't surprised by subtitles. */
    fun audioLabelOf(name: String?, categoryName: String?): String? =
        if (audioVariantFrom(name, categoryName) == AudioVariant.LEGENDADO) "Legendado" else null

    // Junk providers often glue onto the end of an episode title: a season/
    // episode code ("S01 E01", "T1E1", "1x01", "EP 3", "Episódio 12").
    private val EPISODE_CODE_TAIL = Regex(
        "(?i)\\s*[-–—|•:]?\\s*(?:s\\s?\\d{1,3}\\s?e\\s?\\d{1,3}|t\\s?\\d{1,3}\\s?e\\s?\\d{1,3}|\\d{1,3}\\s?x\\s?\\d{1,3}|epis[oó]dio\\s?\\d{1,4}|ep?\\.?\\s?\\d{1,4})\\s*$"
    )

    /**
     * Cleans an episode title for display: drops a trailing year, dub/sub tag
     * ("[L]", "- LEGENDADO"), and a season/episode code the provider tacked on
     * ("... (2026) [L] S01 E01" -> "..."). Returns null if nothing readable is
     * left (the caller falls back to "Episódio N").
     */
    fun episodeTitle(raw: String?): String? {
        var s = text(raw) ?: return null
        // markers first (they may sit between the name and the year/code)
        s = s.replace(Regex("(?i)\\s*[\\[(]\\s*(l|d|leg|dub|dual|nac)\\s*[\\])]\\s*"), " ")
        repeat(2) { s = s.replace(EPISODE_CODE_TAIL, "").trim() }
        s = title(stripAudioMarkers(s)).trim(' ', '-', '–', '—', '|', '•', ':', '.')
        return s.ifBlank { null }
    }

    /**
     * Classifies a movie as dubbed / subtitled / unknown from its title AND
     * its category name — providers often mark only one of the two ("Duna"
     * in a "Filmes Legendados" bucket, or "Duna DUBLADO" in a plain one).
     * Subtitled wins when both signals are present.
     */
    fun audioVariantFrom(name: String?, categoryName: String?): AudioVariant {
        val hay = foldAccents(((name ?: "") + "  ¬  " + (categoryName ?: "")).lowercase())
        return when {
            LEG_HINT.containsMatchIn(hay) -> AudioVariant.LEGENDADO
            DUB_HINT.containsMatchIn(hay) -> AudioVariant.DUBLADO
            else -> AudioVariant.DESCONHECIDO
        }
    }

    // Trailing dub/sub/version tail used ONLY for the grouping key (never for
    // display), peeled aggressively so "Duna - LEG [HD]", "Duna LEGENDADO" and
    // "Duna" all reduce to the same base. The marker must sit behind a space,
    // a separator or a bracket, so it can't eat a glued word.
    private val KEY_TAIL = Regex(
        "(?i)(?:\\s+|\\s*[\\-–—_/|.:•]\\s*|\\s*[\\[(]\\s*)" +
            "(dublad[oa]s?|dublagem|dubbed|legendad[oa]s?|legendas?|nacion(?:al|ais)|leg|dub|subbed|sub|dual\\s*[aá]?udio?|dual|nac|[ld])" +
            "\\s*[\\])]?\\s*$"
    )

    /**
     * Yearless base of the grouping key: the title with any trailing dub/sub
     * tag peeled off, accent-folded and reduced to [a-z0-9]. Two copies of one
     * film share this even when the provider tags only one of them.
     */
    fun variantKeyBase(raw: String?): String {
        var s = title(raw).lowercase().let { foldAccents(it) }
        repeat(4) {
            val t = s.replace(KEY_TAIL, "").trim()
            if (t == s || t.isEmpty()) return@repeat
            s = t
        }
        return s.replace(Regex("[^a-z0-9]+"), "")
    }

    /**
     * Identity key equal for the dubbed and subtitled copies of one title:
     * [variantKeyBase] plus the year. Used to group rows within a single year
     * so a remake ("Mulan" 1998 vs 2020) is never folded in.
     */
    fun variantKey(raw: String?, year: String?): String =
        variantKeyBase(raw) + "|" + (year?.trim().orEmpty())

    private fun foldAccents(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    /** Lower-cased and accent-folded — for case/accent-insensitive matching
     * (search by genre: "acao" must match "AÇÃO", "romance" a "Romance" tag). */
    fun fold(s: String): String = foldAccents(s.lowercase()).trim()

    /** Treats blank/"null"/"N/A" server strings as genuinely absent. */
    fun text(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        if (value.lowercase() in setOf("null", "n/a", "na", "-", "undefined")) return null
        return value
    }

    /** Joins metadata chips, dropping blanks so no trailing "•" separator is ever rendered. */
    fun metaChips(vararg values: String?): List<String> =
        values.mapNotNull { text(it) }
}
