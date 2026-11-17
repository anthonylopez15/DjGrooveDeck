package com.anthony.djapp.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackLimitsTest {
    @Test
    fun tracksUpToTenMinutesAreAllowed() {
        assertFalse(TrackLimits.tooLong(3.5 * 60))
        assertFalse(TrackLimits.tooLong(600.0), "exatamente 10 min ainda vale")
        assertTrue(TrackLimits.tooLong(600.5))
        assertTrue(TrackLimits.tooLong(45 * 60.0))
        assertEquals("Faixa longa demais: o limite é de 10 min", TrackLimits.TOO_LONG_MESSAGE)
    }

    @Test
    fun tenMinutesFitTwiceInAModestHeap() {
        // 10 min = ~106 MB de PCM: com outra de 10 min carregada, cabe num heap de 512 MB
        val mb = 1024L * 1024L
        val tenMinFrames = TrackLimits.MAX_SECONDS.toLong() * 44100
        val limit = MemoryBudget.limit(maxHeap = 512 * mb, usedHeap = 40 * mb + tenMinFrames * 4)
        assertTrue(limit.frames >= tenMinFrames, "limite ${limit.frames} < $tenMinFrames")
    }
}
