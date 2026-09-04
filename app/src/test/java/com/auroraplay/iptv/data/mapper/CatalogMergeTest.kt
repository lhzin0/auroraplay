package com.auroraplay.iptv.data.mapper

import com.auroraplay.iptv.data.database.entity.MovieEntity
import com.auroraplay.iptv.data.database.entity.SeriesEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit #9: a catalog re-sync must not wipe metadata that get_vod_info /
 * get_series_info / TMDB filled in earlier. [mergedWith] keeps a stored value
 * whenever the provider's fresh listing omits that field.
 */
class CatalogMergeTest {

    private fun freshMovie() = MovieEntity(
        id = "42", connectionId = "c", name = "Filme (novo nome)", posterUrl = "poster-new",
        backdropUrl = null, categoryId = "g", categoryName = "Ação", year = null, genre = null,
        plot = null, durationLabel = null, rating = null, containerExtension = "mp4",
        audioLabel = null, addedAtMillis = 200,
    )

    private fun enrichedMovie() = MovieEntity(
        id = "42", connectionId = "c", name = "Filme", posterUrl = "poster-old",
        backdropUrl = "backdrop", categoryId = "g", categoryName = "Geral", year = "2019",
        genre = "Ação", plot = "Uma sinopse.", durationLabel = "1h 50m", rating = 7.4,
        containerExtension = "mkv", audioLabel = null, addedAtMillis = 100,
    )

    @Test
    fun `enrichment survives a listing that omits it`() {
        val merged = freshMovie().mergedWith(enrichedMovie())
        assertEquals("backdrop", merged.backdropUrl)
        assertEquals("2019", merged.year)
        assertEquals("Ação", merged.genre)
        assertEquals("Uma sinopse.", merged.plot)
        assertEquals("1h 50m", merged.durationLabel)
        assertEquals(7.4, merged.rating!!, 0.0001)
    }

    @Test
    fun `provider-owned fields always take the fresh value`() {
        val merged = freshMovie().mergedWith(enrichedMovie())
        assertEquals("Filme (novo nome)", merged.name)
        assertEquals("poster-new", merged.posterUrl)
        assertEquals("Ação", merged.categoryName)
        assertEquals("mp4", merged.containerExtension)
        assertEquals(200L, merged.addedAtMillis)
    }

    @Test
    fun `a fresh non-null field wins over the stored one`() {
        val newerPlot = freshMovie().copy(plot = "Sinopse atualizada.")
        assertEquals("Sinopse atualizada.", newerPlot.mergedWith(enrichedMovie()).plot)
    }

    @Test
    fun `a blank incoming string does not overwrite a stored value`() {
        val blankGenre = freshMovie().copy(genre = "  ")
        assertEquals("Ação", blankGenre.mergedWith(enrichedMovie()).genre)
    }

    @Test
    fun `no existing row leaves the fresh entity untouched`() {
        val fresh = freshMovie()
        assertEquals(fresh, fresh.mergedWith(null))
        assertNull(fresh.mergedWith(null).plot)
    }

    @Test
    fun `series merge keeps the episode-fetch timestamp`() {
        val fresh = SeriesEntity(
            id = "7", connectionId = "c", name = "Série", posterUrl = "p", backdropUrl = null,
            categoryId = "g", categoryName = "Drama", year = null, genre = null, plot = null,
            rating = null, audioLabel = null, addedAtMillis = 9, episodesSyncedAtMillis = 0,
        )
        val stored = fresh.copy(
            backdropUrl = "bd", year = "2020", plot = "sinopse", rating = 8.0,
            episodesSyncedAtMillis = 1_726_000_000_000L,
        )
        val merged = fresh.mergedWith(stored)
        assertEquals("bd", merged.backdropUrl)
        assertEquals("sinopse", merged.plot)
        assertEquals(1_726_000_000_000L, merged.episodesSyncedAtMillis)
    }
}
