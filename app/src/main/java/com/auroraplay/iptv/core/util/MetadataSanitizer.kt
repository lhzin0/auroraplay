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
