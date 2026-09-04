package com.auroraplay.iptv.domain.usecase

import com.auroraplay.iptv.domain.model.Movie
import com.auroraplay.iptv.domain.model.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Turns the raw catalog into Home's streaming-style rails. */
class SmartCategoryBuilderTest {

    private val builder = SmartCategoryBuilder()

    private fun movie(id: String, genre: String? = null, rating: Double? = null, addedAtMillis: Long = 0) = Movie(
        id = id, connectionId = "c", name = "M$id", posterUrl = null, backdropUrl = null,
        categoryId = "g", categoryName = "Geral", year = null, genre = genre, plot = null,
        durationLabel = null, rating = rating, streamUrl = "u", addedAtMillis = addedAtMillis,
    )

    private fun series(id: String, genre: String? = null, rating: Double? = null, addedAtMillis: Long = 0) = Series(
        id = id, connectionId = "c", name = "S$id", posterUrl = null, backdropUrl = null,
        categoryId = "g", categoryName = "Geral", year = null, genre = genre, plot = null,
        rating = rating, addedAtMillis = addedAtMillis,
    )

    // --- cleanTitle ---------------------------------------------------

    @Test
    fun `cleanTitle strips bracketed tags, trailing year, quality and audio markers`() {
        assertEquals("Matrix", builder.cleanTitle("Matrix [Dual Áudio] [4K] (1999) HD"))
        assertEquals("Duna Parte 2", builder.cleanTitle("Duna Parte 2 1080p LEG"))
    }

    @Test
    fun `cleanTitle falls back to the trimmed original when stripping empties it`() {
        assertEquals("[L]", builder.cleanTitle("[L]"))
    }

    // --- movieRails -----------------------------------------------------

    @Test
    fun `no movies means no rails at all`() {
        assertTrue(builder.movieRails(emptyList(), emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun `Novidades lists every movie newest-first`() {
        val movies = listOf(movie("1", addedAtMillis = 10), movie("2", addedAtMillis = 30), movie("3", addedAtMillis = 20))
        val rails = builder.movieRails(movies, emptySet(), emptySet())
        val recent = rails.single { it.id == "recent_movies" }
        assertEquals(listOf("2", "3", "1"), recent.items.map { it.id })
    }

    @Test
    fun `Recomendados only appears with watched genres, excludes already-watched titles`() {
        val movies = listOf(
            movie("1", genre = "Ação"),       // watched already — must be excluded from its own recommendation
            movie("2", genre = "Ação e Aventura"), // substring match on the watched genre
            movie("3", genre = "Comédia"),    // different genre — excluded
        )
        val withoutHistory = builder.movieRails(movies, emptySet(), emptySet())
        assertTrue(withoutHistory.none { it.id == "reco_movies" })

        val withHistory = builder.movieRails(movies, setOf("Ação"), setOf("1"))
        val reco = withHistory.single { it.id == "reco_movies" }
        assertEquals(listOf("2"), reco.items.map { it.id })
    }

    @Test
    fun `Em alta needs at least 4 rated movies, and ignores unrated ones`() {
        val threeRated = (1..3).map { movie(it.toString(), rating = 8.0) } + movie("x") // unrated
        assertTrue(builder.movieRails(threeRated, emptySet(), emptySet()).none { it.id == "top_movies" })

        val fourRated = (1..4).map { movie(it.toString(), rating = it.toDouble()) }
        val top = builder.movieRails(fourRated, emptySet(), emptySet()).single { it.id == "top_movies" }
        assertEquals(listOf("4", "3", "2", "1"), top.items.map { it.id }) // highest rating first
    }

    @Test
    fun `a genre bucket rail needs at least 4 matching movies`() {
        val threeAction = (1..3).map { movie(it.toString(), genre = "Ação") }
        assertTrue(builder.movieRails(threeAction, emptySet(), emptySet()).none { it.title == "Ação" })

        val fourAction = (1..4).map { movie(it.toString(), genre = "Action") } // English keyword, same bucket
        val rail = builder.movieRails(fourAction, emptySet(), emptySet()).single { it.title == "Ação" }
        assertEquals(4, rail.items.size)
    }

    // --- seriesRails mirror the same rules -------------------------------

    @Test
    fun `seriesRails follow the same recency and threshold rules`() {
        assertTrue(builder.seriesRails(emptyList(), emptySet(), emptySet()).isEmpty())

        val series = listOf(series("1", addedAtMillis = 1), series("2", addedAtMillis = 2))
        val recent = builder.seriesRails(series, emptySet(), emptySet()).single { it.id == "recent_series" }
        assertEquals(listOf("2", "1"), recent.items.map { it.id })
    }

    // --- genreChips -------------------------------------------------------

    @Test
    fun `genreChips only returns buckets actually present in the given genres`() {
        val chips = builder.genreChips(listOf("Ação", "Drama", null, "Not A Real Genre"))
        val labels = chips.map { it.first }
        assertTrue(labels.contains("Ação"))
        assertTrue(labels.contains("Drama"))
        assertFalse(labels.contains("Terror"))
        assertFalse(labels.contains("Comédia"))
    }

    @Test
    fun `genreChips is empty when nothing matches`() {
        assertTrue(builder.genreChips(listOf("Culinária", null)).isEmpty())
    }
}
