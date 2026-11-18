package com.anthony.djapp.domain

import kotlin.math.floor
import kotlin.math.roundToLong

/** Grade de batidas corrigida pelo usuário para uma faixa (vale no lugar da detectada). */
data class GridOverride(val bpm: Double, val firstBeatSec: Double)

/**
 * Porta (ADR 0001): onde ficam as grades corrigidas, por arquivo. A chave inclui o `stamp` do arquivo
 * ([LibraryEntry.stamp]): se o arquivo for alterado, a correção antiga deixa de valer.
 * Android: SharedPreferences. iOS: a implementar.
 */
interface TrackGridStore {
    fun get(source: LibraryEntry): GridOverride?

    fun put(source: LibraryEntry, grid: GridOverride)

    fun remove(source: LibraryEntry)
}

/** Regras de edição da grade de batidas (puras). */
object BeatGridEditing {
    const val MIN_BPM = 40.0
    const val MAX_BPM = 220.0

    /** Passo fino do BPM (0,1) e grosso (1). */
    const val FINE_STEP = 0.1
    const val COARSE_STEP = 1.0

    /** Passo do deslocamento da grade, em segundos (10 ms). */
    const val NUDGE_SEC = 0.010

    /** BPM dentro dos limites e com 2 casas decimais (o que a tela mostra e o que se guarda). */
    fun clampBpm(bpm: Double): Double = (bpm.coerceIn(MIN_BPM, MAX_BPM) * 100).roundToLong() / 100.0

    fun adjustBpm(current: Double, delta: Double): Double = clampBpm(current + delta)

    /**
     * Primeira batida equivalente dentro de uma batida a partir do início da faixa: a grade se repete a
     * cada `60/bpm` s, então qualquer batida serve de âncora; guardar a primeira garante linhas na faixa
     * toda (inclusive antes do ponto marcado).
     */
    fun normalizeFirstBeat(firstBeatSec: Double, bpm: Double): Double {
        val period = 60.0 / bpm
        return firstBeatSec - floor(firstBeatSec / period) * period
    }

    /** "A batida 1 é aqui": âncora da grade na posição atual do deck. */
    fun anchorAt(positionSec: Double, bpm: Double): Double = normalizeFirstBeat(positionSec, bpm)

    /** Desloca a grade em [deltaSec] (positivo = batidas mais tarde). */
    fun nudge(firstBeatSec: Double, deltaSec: Double, bpm: Double): Double =
        normalizeFirstBeat(firstBeatSec + deltaSec, bpm)
}

/**
 * Tap tempo: o usuário toca no ritmo e o BPM sai do intervalo médio entre os toques. [nowSec] é um
 * relógio monotônico em segundos, informado por quem chama (testável sem relógio). Uma pausa maior que
 * [maxGapSec] recomeça a contagem; só os últimos [keep] toques contam.
 */
class TapTempo(private val maxGapSec: Double = 2.0, private val keep: Int = 8) {
    private val taps = ArrayDeque<Double>()

    /** Registra um toque. Devolve o BPM estimado, ou null enquanto há só um toque. */
    fun tap(nowSec: Double): Double? {
        if (taps.isNotEmpty() && nowSec - taps.last() > maxGapSec) taps.clear()
        taps.addLast(nowSec)
        while (taps.size > keep) taps.removeFirst()
        if (taps.size < 2) return null
        val intervals = taps.size - 1
        return 60.0 * intervals / (taps.last() - taps.first())
    }

    /** Quantos toques já contam. */
    val count: Int get() = taps.size

    fun reset() = taps.clear()
}
