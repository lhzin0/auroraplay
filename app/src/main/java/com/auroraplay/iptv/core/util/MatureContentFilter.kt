package com.auroraplay.iptv.core.util

/**
 * Detects the adult / "+18" buckets that Xtream playlists almost always ship
 * as their own categories ("XXX", "ADULTOS", "For Adults", "PRIVE", …).
 *
 * This is a *blocklist* against that naming — the opposite direction of
 * [KidsContentFilter], which is an allowlist. It's used to keep adult titles
 * out of screens shown before any profile has been chosen (the profile
 * picker's rotating hero), where a child could be looking. It is best-effort:
 * a provider that files adult content under an innocuous category name will
 * slip through, but in practice they label it loudly so it can be hidden.
 */
object MatureContentFilter {

    private val adultKeywords = listOf(
        "xxx", "adult", "adulto", "+18", "18+", "porn", "porno", "pornô",
        "erotic", "erótic", "erotik", "sexy", "sex ", " sex", "hentai",
        "onlyfans", "prive", "privê", "brazzers", "playboy", "hot ",
        "nudez", "sexo", "para adultos", "só para maiores", "apenas adultos",
    )

    /** True when any provided field (category name, genre, title) looks like
     * adult content. */
    fun isAdult(vararg fields: String?): Boolean =
        fields.any { field ->
            val v = field?.lowercase()?.trim() ?: return@any false
            adultKeywords.any { v.contains(it) }
        }
}
