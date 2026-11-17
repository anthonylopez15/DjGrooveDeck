package com.anthony.djapp.domain

/**
 * Estado de um deck que dá para guardar em disco: de onde veio a faixa e como o deck estava.
 * O áudio não entra (é recarregado do arquivo); a grade de batidas sai de novo da análise.
 */
data class DeckSnapshot(
    val source: LibraryEntry,
    val positionSec: Double,
    val tempoPercent: Float,
    val tempoBeforeSync: Float,
    val volume: Float,
    val low: Float,
    val mid: Float,
    val high: Float,
    val syncOn: Boolean,
)

/**
 * Estado da sessão de DJ para sobreviver à morte do processo: os dois decks (null = vazio ou faixa
 * sem origem conhecida) e o que é do mixer.
 */
data class SessionSnapshot(
    val a: DeckSnapshot?,
    val b: DeckSnapshot?,
    val crossfader: Float,
    val master: Float,
    val keyLock: Boolean,
)

/**
 * Porta (ADR 0001): onde o estado da sessão fica guardado entre processos.
 * Android: SharedPreferences. iOS: a implementar (NSUserDefaults).
 */
interface SessionStore {
    /** Estado guardado; null se não há nada (ou se estava corrompido). */
    fun load(): SessionSnapshot?

    /** Guarda [snapshot] (pode ser assíncrono e deve ser barato). */
    fun save(snapshot: SessionSnapshot)
}
