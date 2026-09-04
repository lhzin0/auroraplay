package com.auroraplay.iptv.domain.policy

import com.auroraplay.iptv.domain.model.Channel
import com.auroraplay.iptv.domain.model.Movie
import com.auroraplay.iptv.domain.model.Profile
import com.auroraplay.iptv.domain.model.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audit #2: the single kids/adult content-authorization gate. */
class ContentPolicyTest {

    private val policy = ContentPolicy()

    private fun movie(name: String = "M", category: String = "Ação", genre: String? = "Ação") = Movie(
        id = "m", connectionId = "conn", name = name, posterUrl = null, backdropUrl = null,
        categoryId = "c", categoryName = category, year = null, genre = genre, plot = null,
        durationLabel = null, rating = null, streamUrl = "u",
    )

    private fun series(name: String = "S", category: String = "Drama", genre: String? = "Drama") = Series(
        id = "s", connectionId = "conn", name = name, posterUrl = null, backdropUrl = null,
        categoryId = "c", categoryName = category, year = null, genre = genre, plot = null, rating = null,
    )

    private fun channel(name: String = "Canal", category: String = "Esportes") = Channel(
        id = "ch", connectionId = "conn", name = name, logoUrl = null, categoryId = "c",
        categoryName = category, streamUrl = "u", epgChannelId = null,
    )

    @Test fun restricts_only_kids_profile() {
        assertFalse(policy.restricts(null))
        assertFalse(policy.restricts(Profile("p", "P", "#000", "A", isKids = false)))
        assertTrue(policy.restricts(Profile("p", "P", "#000", "A", isKids = true)))
    }

    @Test fun adult_profile_sees_everything() {
        assertTrue(policy.allows(isKidsProfile = false, movie = movie(category = "XXX")))
        assertTrue(policy.allows(isKidsProfile = false, series = series(category = "Terror")))
        assertTrue(policy.allows(isKidsProfile = false, channel = channel(category = "Filmes Adultos")))
    }

    @Test fun kids_profile_sees_only_kids_categories() {
        assertTrue(policy.allows(true, movie = movie(category = "Infantil")))
        assertTrue(policy.allows(true, series = series(category = "Desenhos")))
        assertFalse(policy.allows(true, movie = movie(category = "Ação")))
        assertFalse(policy.allows(true, series = series(category = "Drama")))
        assertFalse(policy.allows(true, channel = channel(category = "Esportes")))
    }

    @Test fun kids_profile_never_sees_adult_even_if_also_tagged_kids() {
        assertFalse(policy.allows(true, movie = movie(category = "Infantil XXX")))
    }

    @Test fun list_helpers_filter_for_kids_and_passthrough_for_adults() {
        val movies = listOf(movie(name = "A", category = "Infantil"), movie(name = "B", category = "Ação"))
        assertEquals(listOf("A"), policy.movies(true, movies).map { it.name })
        assertEquals(listOf("A", "B"), policy.movies(false, movies).map { it.name })
    }

    @Test fun visibleLoose_only_hides_adult_looking_titles_for_kids() {
        assertTrue(policy.visibleLoose(true, "Peppa Pig"))
        assertFalse(policy.visibleLoose(true, "XXX Filmes +18"))
        assertTrue(policy.visibleLoose(false, "XXX Filmes +18"))
    }
}
