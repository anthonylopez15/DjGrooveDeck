package com.anthony.djapp.domain

/**
 * Quando o app fica sem uso por [LIMIT_MS] (30 min), as faixas carregadas são soltas e ele abre limpo:
 * ninguém espera encontrar a música de ontem nos decks, e isso libera a memória (~150 MB por faixa de 15 min).
 * "Uso" = o último momento em que o estado foi guardado (sair da tela, pausar, tocando em segundo plano).
 * Pura: o relógio é de quem chama (milissegundos de época).
 */
object IdlePolicy {
    const val LIMIT_MS = 30 * 60 * 1000L

    /** Passou do limite sem uso. [lastActivityMs] 0 (estado antigo, sem horário) conta como sem uso. */
    fun isStale(lastActivityMs: Long, nowMs: Long): Boolean = nowMs - lastActivityMs > LIMIT_MS

    /** Quanto falta para [isStale] valer (0 se já passou). */
    fun remainingMs(lastActivityMs: Long, nowMs: Long): Long = (LIMIT_MS - (nowMs - lastActivityMs)).coerceAtLeast(0L)
}
