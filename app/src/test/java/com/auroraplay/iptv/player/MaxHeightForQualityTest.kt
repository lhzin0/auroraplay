package com.auroraplay.iptv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit #13: the "Qualidade" setting maps to a video-height cap the player's
 * adaptive selector honours. "auto" (and anything unknown) means no cap.
 */
class MaxHeightForQualityTest {

    @Test
    fun `each named quality maps to its height cap`() {
        assertEquals(480, maxHeightForQuality("low"))
        assertEquals(720, maxHeightForQuality("medium"))
        assertEquals(1080, maxHeightForQuality("high"))
    }

    @Test
    fun `auto means no cap`() {
        assertNull(maxHeightForQuality("auto"))
    }

    @Test
    fun `an unrecognised value never throttles playback`() {
        assertNull(maxHeightForQuality(""))
        assertNull(maxHeightForQuality("ultra"))
    }
}
