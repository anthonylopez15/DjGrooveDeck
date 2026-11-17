package com.anthony.djapp.engine

import kotlin.math.min

/**
 * Quanto de faixa cabe na memória antes de decodificar (a faixa inteira fica em memória como PCM de
 * 16 bits estéreo: ~10,6 MB por minuto a 44,1 kHz).
 *
 * Duas travas, vale a menor:
 * - **por faixa** ([MAX_TRACK_HEAP_FRACTION] do heap): uma faixa sozinha não pode tomar a memória
 *   que o outro deck vai precisar;
 * - **memória livre agora**: o que sobra do heap descontando o que já está em uso (inclusive a faixa
 *   do outro deck) e uma reserva para o resto do app ([RESERVE_HEAP_FRACTION]). Durante a
 *   decodificação a faixa existe duas vezes (buffer + cópia final), então o pico é o dobro.
 */
object MemoryBudget {
    const val MAX_TRACK_HEAP_FRACTION = 0.30
    const val RESERVE_HEAP_FRACTION = 0.15
    private const val BYTES_PER_FRAME = 4 // 16 bits x 2 canais
    private const val PEAK_COPIES = 2

    /** Máximo de [frames] que dá para decodificar; [byFreeMemory]: a trava foi a memória já em uso. */
    data class Limit(val frames: Long, val byFreeMemory: Boolean)

    /** [maxHeap] e [usedHeap] em bytes (Android: `Runtime.maxMemory()` e `totalMemory() - freeMemory()`). */
    fun limit(maxHeap: Long, usedHeap: Long): Limit {
        val perTrack = (maxHeap * MAX_TRACK_HEAP_FRACTION).toLong() / BYTES_PER_FRAME
        val free = maxHeap - usedHeap - (maxHeap * RESERVE_HEAP_FRACTION).toLong()
        val fitsNow = (free / (BYTES_PER_FRAME * PEAK_COPIES)).coerceAtLeast(0)
        return Limit(frames = min(perTrack, fitsNow), byFreeMemory = fitsNow < perTrack)
    }

    /** Mensagem para quem tentou carregar uma faixa maior que [limit] (em minutos, à taxa [sampleRate]). */
    fun tooLongMessage(limit: Limit, sampleRate: Int): String {
        val minutes = limit.frames / sampleRate / 60
        return if (limit.byFreeMemory) {
            "Faixa longa demais para a memória livre agora (cabem ~$minutes min com o que já está carregado)"
        } else {
            "Faixa muito longa para a memória do aparelho (máx. ~$minutes min)"
        }
    }
}
