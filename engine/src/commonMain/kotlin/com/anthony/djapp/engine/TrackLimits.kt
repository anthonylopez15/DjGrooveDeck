package com.anthony.djapp.engine

/**
 * Regra do app: faixas de até [MAX_MINUTES] minutos. A faixa inteira fica em memória (~10,6 MB por
 * minuto), então faixas longas (mixes, sets gravados) poderiam esgotar a memória com dois decks
 * carregados. O [MemoryBudget] continua valendo por cima disto, para aparelhos com pouca memória.
 */
object TrackLimits {
    const val MAX_MINUTES = 10
    const val MAX_SECONDS = MAX_MINUTES * 60.0

    /** A duração passa do limite. */
    fun tooLong(durationSec: Double): Boolean = durationSec > MAX_SECONDS

    val TOO_LONG_MESSAGE = "Faixa longa demais: o limite é de $MAX_MINUTES min"
}
