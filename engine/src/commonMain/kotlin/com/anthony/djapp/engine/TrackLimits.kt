package com.anthony.djapp.engine

/**
 * Faixa recusada **de propósito** (longa demais ou sem memória para ela): a mensagem já é para o
 * usuário, sem prefixo técnico. Diferente de um erro de arquivo/codec.
 */
class TrackRejectedException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * Regra do app: faixas de até [MAX_MINUTES] minutos. A faixa inteira fica em memória (~10,6 MB por
 * minuto), então faixas longas (mixes, sets gravados) poderiam esgotar a memória com dois decks
 * carregados. O [MemoryBudget] continua valendo por cima disto, para aparelhos com pouca memória.
 */
object TrackLimits {
    const val MAX_MINUTES = 15
    const val MAX_SECONDS = MAX_MINUTES * 60.0

    /** A duração passa do limite. */
    fun tooLong(durationSec: Double): Boolean = durationSec > MAX_SECONDS

    /** "Faixa longa demais (17:20): o limite é de 15 min" — com a duração da faixa recusada. */
    fun tooLongMessage(durationSec: Double): String {
        val total = durationSec.toInt()
        val mm = total / 60
        val ss = total % 60
        return "Faixa longa demais ($mm:${if (ss < 10) "0" else ""}$ss): o limite é de $MAX_MINUTES min"
    }
}
