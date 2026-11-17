package com.anthony.djapp.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Quanto de faixa cabe na memória (a faixa inteira fica em memória: ~10,6 MB/min a 44,1 kHz). */
class MemoryBudgetTest {
    private val mb = 1024L * 1024L
    private val rate = 44100

    private fun minutes(limit: MemoryBudget.Limit) = limit.frames / rate / 60

    @Test
    fun emptyAppIsLimitedPerTrack() {
        // heap de 768 MB (largeHeap num celular grande), quase nada em uso: vale a trava por faixa (30%)
        val l = MemoryBudget.limit(maxHeap = 768 * mb, usedHeap = 40 * mb)
        assertFalse(l.byFreeMemory)
        assertEquals(22, minutes(l)) // 30% de 768 MB = 230,4 MB / (4 bytes x 44100 x 60) = 22,8 min
    }

    @Test
    fun otherDeckWithALongTrackLowersTheLimit() {
        // o outro deck já segura ~210 MB (20 min): a próxima faixa precisa caber em dobro no que sobra
        val l = MemoryBudget.limit(maxHeap = 768 * mb, usedHeap = 260 * mb)
        assertTrue(l.byFreeMemory)
        // livre: 768 - 260 - 15% (115,2) = 392,8 MB; pico em dobro: 196,4 MB de PCM = 19,4 min
        assertEquals(19, minutes(l))
    }

    @Test
    fun noRoomMeansZero() {
        val l = MemoryBudget.limit(maxHeap = 256 * mb, usedHeap = 250 * mb)
        assertEquals(0, l.frames)
        assertTrue(l.byFreeMemory)
    }

    @Test
    fun peakOfTheNewTrackPlusWhatIsInUseFitsInTheHeap() {
        // a garantia que importa: em uso + 2x a faixa nova + reserva <= heap
        for (heapMb in listOf(256L, 512L, 768L)) {
            for (usedMb in listOf(20L, 100L, 200L)) {
                val heap = heapMb * mb
                val used = usedMb * mb
                val l = MemoryBudget.limit(heap, used)
                val peak = used + l.frames * 4 * 2 + (heap * MemoryBudget.RESERVE_HEAP_FRACTION).toLong()
                assertTrue(peak <= heap, "heap $heapMb MB, em uso $usedMb MB: pico $peak > $heap")
            }
        }
    }

    @Test
    fun messagesTellWhyItDoesNotFit() {
        val device = MemoryBudget.tooLongMessage(MemoryBudget.Limit(rate * 60L * 21, byFreeMemory = false), rate)
        assertEquals("Faixa muito longa para a memória do aparelho (máx. ~21 min)", device)
        val free = MemoryBudget.tooLongMessage(MemoryBudget.Limit(rate * 60L * 8, byFreeMemory = true), rate)
        assertEquals("Faixa longa demais para a memória livre agora (cabem ~8 min com o que já está carregado)", free)
    }
}
