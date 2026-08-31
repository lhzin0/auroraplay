package com.auroraplay.iptv.core.util

/**
 * A Kids profile shouldn't just have adult content hidden (MatureContentFilter) —
 * it should show *only* what's actually kids-appropriate. Xtream playlists give
 * no formal rating, but providers reliably group child-friendly titles into
 * their own category (e.g. "Infantil", "Kids", "Desenhos"), separate from
 * general genre buckets like "Ação" or "Terror" that aren't written for kids
 * even though they're not adult either. This is an allowlist against that
 * category naming, not a blacklist against bad ones — the same limitation
 * as MatureContentFilter applies: a provider that never made a Kids category
 * at all means a Kids profile sees nothing, which is the safe failure mode.
 */
object KidsContentFilter {

    private val kidsKeywords = listOf(
        "infantil", "kids", "kid ", "desenho", "cartoon", "família", "familia",
        "family", "criança", "crianca", "children", "júnior", "junior", "toddler",
    )

    fun isKidsAppropriate(vararg fields: String?): Boolean {
        return fields.any { field ->
            field != null && kidsKeywords.any { keyword -> field.lowercase().contains(keyword) }
        }
    }
}
