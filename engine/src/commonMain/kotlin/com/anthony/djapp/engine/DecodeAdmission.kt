package com.anthony.djapp.engine

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Decide quando uma decodificação completa pode começar. Cada decodificação ocupa o dobro da faixa no
 * pico (buffer + cópia final, [MemoryBudget]); duas ao mesmo tempo só são seguras se a **soma** dos
 * picos cabe na memória livre. Então:
 * - cabe agora (contando o que as decodificações em andamento ainda vão alocar): começa já, em paralelo;
 * - não cabe agora, mas caberia sozinha: espera as outras terminarem e confere de novo;
 * - não cabe nem sozinha: recusada com a mensagem do [MemoryBudget].
 *
 * [maxHeap] e [usedHeap] em bytes; [collect] pede uma coleta de lixo (lixo ainda não coletado não conta).
 */
class DecodeAdmission(
    private val maxHeap: () -> Long,
    private val usedHeap: () -> Long,
    private val collect: () -> Unit = {},
) {
    private val mutex = Mutex()
    private val active = mutableListOf<Ticket>()
    private val finished = MutableStateFlow(0)

    /** Reserva de uma decodificação em andamento; [release] ao terminar (com sucesso ou não). */
    inner class Ticket internal constructor(private val frames: Long, internal val limit: MemoryBudget.Limit) {
        /** Quanto ainda vai ser alocado: buffer + cópia até o buffer existir, depois só a cópia. */
        @kotlin.concurrent.Volatile
        internal var pendingBytes: Long = frames * BYTES_PER_FRAME * PEAK_COPIES

        /** O buffer da faixa já foi alocado (e já conta na memória em uso). */
        fun bufferAllocated() {
            pendingBytes = frames * BYTES_PER_FRAME
        }

        suspend fun release() {
            withContext(NonCancellable) {
                mutex.withLock { active.remove(this@Ticket) }
                finished.value += 1
            }
        }

        /** O limite de frames que vale para esta decodificação (conferido também durante o decode). */
        val maxFrames: MemoryBudget.Limit get() = limit
    }

    /** Espera a vez e reserva [frames] frames (taxa [sampleRate]); lança [TrackRejectedException] se recusada. */
    suspend fun admit(frames: Long, sampleRate: Int): Ticket {
        val durationSec = frames.toDouble() / sampleRate
        if (TrackLimits.tooLong(durationSec)) throw TrackRejectedException(TrackLimits.tooLongMessage(durationSec))
        while (true) {
            val seen = finished.value
            val decision = mutex.withLock {
                val limit = limitNow()
                when {
                    frames <= limit.frames -> Ticket(frames, limit).also { active += it }
                    active.isEmpty() -> throw TrackRejectedException(MemoryBudget.tooLongMessage(limit, sampleRate))
                    else -> null
                }
            }
            if (decision != null) return decision
            finished.first { it != seen } // alguma terminou: confere de novo
        }
    }

    private fun limitNow(): MemoryBudget.Limit {
        fun measure() = MemoryBudget.limit(maxHeap(), usedHeap() + active.sumOf { it.pendingBytes })
        val first = measure()
        if (!first.byFreeMemory) return first
        collect()
        return measure()
    }

    private companion object {
        const val BYTES_PER_FRAME = 4L
        const val PEAK_COPIES = 2L
    }
}
