package com.auroraplay.iptv.data.repository

import com.auroraplay.iptv.domain.repository.SyncStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit #10: a sync is only "complete" when every section reached the server.
 * A section returning an empty list still counts as reached ("nothing here");
 * only a failed call leaves it pending.
 */
class SyncOutcomeTest {

    @Test
    fun `all sections reached is DONE`() {
        assertEquals(SyncStage.DONE, syncOutcome(channelsReached = true, moviesReached = true, seriesReached = true))
    }

    @Test
    fun `one section failing is PARTIAL, never DONE`() {
        assertEquals(SyncStage.PARTIAL, syncOutcome(true, true, false))
        assertEquals(SyncStage.PARTIAL, syncOutcome(true, false, true))
        assertEquals(SyncStage.PARTIAL, syncOutcome(false, true, true))
    }

    @Test
    fun `only one section reaching is still PARTIAL`() {
        assertEquals(SyncStage.PARTIAL, syncOutcome(true, false, false))
    }

    @Test
    fun `no section reaching is an outage (null)`() {
        assertNull(syncOutcome(false, false, false))
    }
}
