package com.anthony.djapp.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Decodificações em paralelo só quando a soma dos picos cabe na memória. */
@OptIn(ExperimentalCoroutinesApi::class)
class DecodeAdmissionTest {
    private val mb = 1024L * 1024L
    private val rate = 44100
    private fun frames(minutes: Int) = minutes * 60L * rate

    // heap grande o bastante para uma faixa de 10 min (106 MB, pico 212 MB) e, ao mesmo tempo, duas
    private fun admission(maxHeap: Long, used: () -> Long = { 0L }) = DecodeAdmission({ maxHeap }, used)

    @Test
    fun twoTracksThatFitTogetherStartInParallel() = runTest {
        val a = admission(maxHeap = 2048 * mb)
        val first = a.admit(frames(10), rate)
        val second = a.admit(frames(10), rate) // não espera: devolve na hora
        first.release()
        second.release()
    }

    @Test
    fun secondWaitsWhenThePeaksDoNotFitTogether() = runTest {
        // 800 MB: livre ~680 MB; uma faixa de 15 min (151 MB, pico 302 MB) cabe, duas somadas (604 MB) ainda cabem...
        // com 600 MB: livre 510 MB; uma cabe (302), a segunda (302 + 302 = 604 > 510) tem que esperar
        val a = admission(maxHeap = 600 * mb)
        val first = a.admit(frames(15), rate)
        var admitted = false
        val second = async { a.admit(frames(15), rate).also { admitted = true } }
        advanceUntilIdle()
        assertFalse(admitted, "a segunda não cabe junto com a primeira")
        first.release()
        advanceUntilIdle()
        assertTrue(admitted, "com a primeira fora, a segunda entra")
        second.await().release()
    }

    @Test
    fun trackThatNeverFitsIsRejectedWithoutWaiting() = runTest {
        val a = admission(maxHeap = 256 * mb)
        assertFailsWith<TrackRejectedException> { a.admit(frames(15), rate) }
    }

    @Test
    fun tooLongIsRejectedByTheAppLimit() = runTest {
        val a = admission(maxHeap = 8192 * mb)
        assertFailsWith<TrackRejectedException> { a.admit(frames(16), rate) }
    }

    @Test
    fun allocatedBufferStopsCountingTwice() = runTest {
        // já alocado o buffer da primeira, só a cópia dela fica pendente: a segunda de 10 min passa
        val used = 106 * mb // o buffer da primeira (10 min) já está no heap
        val a = admission(maxHeap = 800 * mb, used = { used })
        val first = a.admit(frames(10), rate)
        first.bufferAllocated()
        var admitted = false
        val second = async { a.admit(frames(10), rate).also { admitted = true } }
        advanceUntilIdle()
        assertTrue(admitted)
        first.release()
        second.await().release()
    }
}
