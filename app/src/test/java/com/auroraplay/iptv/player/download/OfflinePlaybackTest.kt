package com.auroraplay.iptv.player.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit #8: the decisions that gate offline playback of a download. The real
 * resolution reads the Media3 index (needs an instrumented test), but the
 * branch logic — file missing, blocked for this profile, download not finished
 * — is pure and covered here.
 */
class OfflinePlaybackTest {

    @Test
    fun `a complete, allowed, existing download plays`() {
        assertNull(offlineLoadFailure(exists = true, allowedForProfile = true, isComplete = true))
    }

    @Test
    fun `a download that no longer exists is reported, not played`() {
        assertEquals(
            "Este download não está mais disponível.",
            offlineLoadFailure(exists = false, allowedForProfile = false, isComplete = false),
        )
    }

    @Test
    fun `a download blocked for the active profile is refused`() {
        assertEquals(
            "Este conteúdo não está disponível neste perfil.",
            offlineLoadFailure(exists = true, allowedForProfile = false, isComplete = true),
        )
    }

    @Test
    fun `an unfinished download is refused rather than streamed`() {
        assertEquals(
            "Este download ainda não terminou. Conecte-se à internet para concluí-lo.",
            offlineLoadFailure(exists = true, allowedForProfile = true, isComplete = false),
        )
    }

    @Test
    fun `sortKey round-trips to season and episode`() {
        assertEquals(2 to 5, seasonEpisodeFromSortKey(2 * 1000 + 5))
        assertEquals(1 to 12, seasonEpisodeFromSortKey(1012))
    }

    @Test
    fun `a movie sortKey of zero has no season or episode`() {
        assertNull(seasonEpisodeFromSortKey(0))
        assertNull(seasonEpisodeFromSortKey(-3))
    }
}
