package com.anthony.djapp.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Time-stretch em tempo real (WSOLA): muda o andamento sem mudar o tom ("key lock").
 *
 * A cada bloco de [HOP] amostras de saída, pega-se da entrada um trecho (janela de Hann de
 * 2*[HOP]) cuja posição é a "natural" (avança tempo*HOP por bloco) ajustada em até ±[TOL]
 * amostras para casar com a continuação do trecho anterior; os trechos são somados com 50% de
 * sobreposição. Com tempo 1,0 a continuação exata é escolhida e a saída é idêntica à entrada.
 *
 * A saída fica numa fila (FIFO) estéreo na taxa da faixa; o consumidor lê com [left]/[right],
 * garante dados com [ensure] e descarta o já lido com [consume]. Sem alocação no uso normal
 * (a fila só cresce se pedirem mais que ~8k frames de uma vez).
 */
class WsolaStretcher {
    private val window = FloatArray(WIN) { (0.5 - 0.5 * cos(2.0 * PI * it / WIN)).toFloat() }
    private val template = FloatArray(HOP)
    private val overlapL = FloatArray(HOP)
    private val overlapR = FloatArray(HOP)

    private var pcm = ShortArray(0)
    private var frames = 0
    private var prevPos = -1
    private var natural = 0.0

    private var fifoL = FloatArray(HOP * 16)
    private var fifoR = FloatArray(HOP * 16)
    private var rd = 0
    private var wr = 0

    val available: Int get() = wr - rd

    fun setSource(pcm: ShortArray) {
        this.pcm = pcm
        frames = pcm.size / 2
        reset(0.0)
    }

    /** Recomeça a partir de [positionFrames] (início, busca, cue). Sem trecho anterior para alinhar. */
    fun reset(positionFrames: Double) {
        prevPos = -1
        natural = positionFrames
        overlapL.fill(0f)
        overlapR.fill(0f)
        rd = 0
        wr = 0
    }

    /** Garante pelo menos [n] frames disponíveis, gerando blocos no [tempo] dado. */
    fun ensure(n: Int, tempo: Double) {
        if (n + HOP > fifoL.size) grow(n + HOP)
        while (wr - rd < n) generateBlock(tempo)
    }

    fun left(i: Int): Float = fifoL[rd + i]
    fun right(i: Int): Float = fifoR[rd + i]
    fun consume(n: Int) {
        rd += n
    }

    private fun grow(minSize: Int) {
        var size = fifoL.size
        while (size < minSize) size *= 2
        fifoL = fifoL.copyOf(size)
        fifoR = fifoR.copyOf(size)
    }

    private fun generateBlock(tempo: Double) {
        // abre espaço descartando o já consumido
        if (wr + HOP > fifoL.size) {
            val n = wr - rd
            fifoL.copyInto(fifoL, 0, rd, wr)
            fifoR.copyInto(fifoR, 0, rd, wr)
            rd = 0
            wr = n
        }

        val center = natural.roundToInt()
        val p = if (prevPos < 0) center.coerceAtLeast(0) else findBest(center)

        for (i in 0 until HOP) {
            val w = window[i]
            fifoL[wr + i] = inL(p + i) * w + overlapL[i]
            fifoR[wr + i] = inR(p + i) * w + overlapR[i]
        }
        for (i in 0 until HOP) {
            val w = window[HOP + i]
            overlapL[i] = inL(p + HOP + i) * w
            overlapR[i] = inR(p + HOP + i) * w
        }
        wr += HOP
        prevPos = p
        natural += HOP * tempo
    }

    /** Posição de início do trecho que melhor continua o anterior, perto de [center]. */
    private fun findBest(center: Int): Int {
        val refStart = prevPos + HOP
        for (i in 0 until HOP) template[i] = mono(refStart + i)

        // busca grossa: passo de 4 nas posições, 1 em cada 4 amostras
        var bestD = 0
        var bestScore = if (center >= 0) score(center, 4) else Double.NEGATIVE_INFINITY
        var d = -TOL
        while (d <= TOL) {
            val p = center + d
            if (p >= 0) {
                val s = score(p, 4)
                if (s > bestScore) {
                    bestScore = s
                    bestD = d
                }
            }
            d += 4
        }

        // refino: todas as posições perto do melhor, 1 em cada 2 amostras
        var best = (center + bestD).coerceAtLeast(0)
        var bestFine = Double.NEGATIVE_INFINITY
        for (dd in -4..4) {
            val p = center + bestD + dd
            if (p < 0) continue
            val s = score(p, 2)
            if (s > bestFine) {
                bestFine = s
                best = p
            }
        }
        // a continuação exata (cópia fiel da entrada) entra sempre na disputa
        if (refStart >= 0 && refStart in (center - TOL)..(center + TOL)) {
            val s = score(refStart, 2)
            if (s >= bestFine) best = refStart
        }
        return best
    }

    /** Correlação cruzada normalizada entre o modelo e a entrada a partir de [p]. */
    private fun score(p: Int, stride: Int): Double {
        var dot = 0.0
        var energy = 0.0
        var i = 0
        while (i < HOP) {
            val c = mono(p + i)
            dot += template[i] * c
            energy += c * c
            i += stride
        }
        return dot / sqrt(energy + 1e-9)
    }

    private fun mono(idx: Int): Float =
        if (idx < 0 || idx >= frames) 0f else (pcm[2 * idx] + pcm[2 * idx + 1]) * (0.5f / 32768f)

    private fun inL(idx: Int): Float = if (idx < 0 || idx >= frames) 0f else pcm[2 * idx] * (1f / 32768f)
    private fun inR(idx: Int): Float = if (idx < 0 || idx >= frames) 0f else pcm[2 * idx + 1] * (1f / 32768f)

    private companion object {
        const val WIN = 2048
        const val HOP = WIN / 2
        const val TOL = 768
    }
}
