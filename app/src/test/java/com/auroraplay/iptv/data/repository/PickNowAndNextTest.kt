package com.auroraplay.iptv.data.repository

import com.auroraplay.iptv.domain.model.EpgProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit #15: "agora / a seguir" from an EPG timeline, with exact time
 * boundaries.
 */
class PickNowAndNextTest {

    private fun p(id: String, start: Long, end: Long) = EpgProgram(id, id, "", start, end)

    private val timeline = listOf(
        p("a", 0, 100),
        p("b", 100, 200),   // starts exactly when "a" ends
        p("c", 200, 300),
    )

    @Test
    fun `current is the program whose window contains now`() {
        val (cur, next) = pickNowAndNext(timeline, nowMillis = 150)
        assertEquals("b", cur?.id)
        assertEquals("c", next?.id)
    }

    @Test
    fun `a next program starting exactly at the current end is not skipped`() {
        val (cur, next) = pickNowAndNext(timeline, nowMillis = 50)
        assertEquals("a", cur?.id)
        assertEquals("b", next?.id) // b.start == a.end
    }

    @Test
    fun `start of a program is inclusive, end is exclusive`() {
        assertEquals("b", pickNowAndNext(timeline, nowMillis = 100).first?.id)   // b.start
        assertEquals("c", pickNowAndNext(timeline, nowMillis = 200).first?.id)   // b.end -> c
    }

    @Test
    fun `no current program is not invented from the first row`() {
        // now is before the whole timeline
        val (curBefore, nextBefore) = pickNowAndNext(timeline, nowMillis = -50)
        assertNull(curBefore)
        assertEquals("a", nextBefore?.id)

        // now is after the whole timeline
        val (curAfter, nextAfter) = pickNowAndNext(timeline, nowMillis = 999)
        assertNull(curAfter)
        assertNull(nextAfter)
    }

    @Test
    fun `now sitting in a gap between programs has no current and points at the next`() {
        val gapped = listOf(p("x", 0, 100), p("y", 500, 600))
        val (cur, next) = pickNowAndNext(gapped, nowMillis = 300)
        assertNull(cur)
        assertEquals("y", next?.id)
    }

    @Test
    fun `an unordered timeline is handled`() {
        val shuffled = listOf(p("c", 200, 300), p("a", 0, 100), p("b", 100, 200))
        val (cur, next) = pickNowAndNext(shuffled, nowMillis = 150)
        assertEquals("b", cur?.id)
        assertEquals("c", next?.id)
    }

    @Test
    fun `empty timeline yields nulls`() {
        assertEquals(null to null, pickNowAndNext(emptyList(), nowMillis = 0))
    }
}
