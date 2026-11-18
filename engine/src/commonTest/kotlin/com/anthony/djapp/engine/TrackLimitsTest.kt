package com.anthony.djapp.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackLimitsTest {
    @Test
    fun tracksUpToTenMinutesAreAllowed() {
        assertFalse(TrackLimits.tooLong(3.5 * 60))
        assertFalse(TrackLimits.tooLong(900.0), "exatamente 15 min ainda vale")
        assertTrue(TrackLimits.tooLong(900.5))
        assertTrue(TrackLimits.tooLong(45 * 60.0))
        assertEquals("Faixa longa demais (17:20): o limite é de 15 min", TrackLimits.tooLongMessage(17 * 60 + 20.4))
        assertEquals(
            "Faixa longa demais (45:05): o limite é de 15 min",
            TrackLimits.tooLongMessage(45 * 60 + 5.0),
            "segundos com zero à esquerda",
        )
    }

    @Test
    fun fifteenMinutesFitTwiceInALargeHeap() {
        // 15 min = ~159 MB de PCM: com outra de 15 min carregada, cabe num heap de 768 MB (o do celular
        // de teste, com largeHeap) — o pico da decodificação é o dobro da faixa nova
        val mb = 1024L * 1024L
        val maxFrames = TrackLimits.MAX_SECONDS.toLong() * 44100
        val limit = MemoryBudget.limit(maxHeap = 768 * mb, usedHeap = 40 * mb + maxFrames * 4)
        assertTrue(limit.frames >= maxFrames, "limite ${limit.frames} < $maxFrames")
    }

    @Test
    fun aSmallHeapStillRefusesTrackLongerThanItCanHold() {
        // num heap de 256 MB o limite de memória (30% por faixa = ~7 min) vale antes dos 15 min do app
        val mb = 1024L * 1024L
        val maxFrames = TrackLimits.MAX_SECONDS.toLong() * 44100
        val limit = MemoryBudget.limit(maxHeap = 256 * mb, usedHeap = 30 * mb)
        assertTrue(limit.frames < maxFrames, "256 MB não comporta 15 min, e o orçamento tem de recusar")
    }
}
