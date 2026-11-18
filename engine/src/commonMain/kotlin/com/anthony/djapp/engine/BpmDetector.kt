package com.anthony.djapp.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/** Grade de batidas de andamento fixo: batida n acontece em firstBeatSec + n * 60 / bpm. */
class BeatGrid(val bpm: Double, val firstBeatSec: Double) {
    /** Fase da batida em [sec]: 0 = exatamente numa batida, 0,5 = no meio entre duas. */
    fun phaseAt(sec: Double): Double {
        val x = (sec - firstBeatSec) * bpm / 60.0
        return x - floor(x)
    }

    /**
     * Posição dentro do compasso em [sec], em batidas, de 0 (tempo 1, a batida forte) até
     * [BEATS_PER_BAR]. O tempo 1 é o das linhas laranja da onda: uma batida a cada [BEATS_PER_BAR]
     * contadas da primeira batida da grade (o detector acha o ritmo, não qual batida abre o compasso).
     */
    fun barPhaseAt(sec: Double): Double = unitPhaseAt(sec, BEATS_PER_BAR)

    /** Início de compasso (tempo 1) mais próximo de [sec], nunca antes da primeira batida da grade. */
    fun nearestBarStart(sec: Double): Double = nearestUnitStart(sec, BEATS_PER_BAR)

    /** Posição em [sec] dentro de um grupo de [unitBeats] batidas (1 = batida, [BEATS_PER_BAR] = compasso). */
    fun unitPhaseAt(sec: Double, unitBeats: Int): Double {
        val beats = (sec - firstBeatSec) * bpm / 60.0
        return beats - floor(beats / unitBeats) * unitBeats
    }

    /** Início de grupo de [unitBeats] batidas mais próximo de [sec], nunca antes da primeira batida. */
    fun nearestUnitStart(sec: Double, unitBeats: Int): Double {
        val units = round((sec - firstBeatSec) * bpm / 60.0 / unitBeats).coerceAtLeast(0.0)
        return firstBeatSec + units * unitBeats * 60.0 / bpm
    }

    companion object {
        /** Batidas por compasso (4/4: bachata, merengue, salsa, pop...). */
        const val BEATS_PER_BAR = 4
    }
}

/**
 * Quanto (em batidas, de -0,5 a +0,5) o seguidor deve andar para ficar na mesma fase do líder.
 * Sempre o caminho mais curto, para o salto ser o menor possível.
 */
fun beatPhaseDelta(leaderPhase: Double, followerPhase: Double): Double {
    var d = leaderPhase - followerPhase
    if (d > 0.5) d -= 1.0
    if (d < -0.5) d += 1.0
    return d
}

/**
 * Detecta BPM e posição das batidas assumindo andamento constante (típico de música eletrônica).
 *
 * 1. Envelope de energia por trecho de 256 frames → "onsets" (subidas de energia, sem a média local).
 * 2. Busca em duas etapas por pente de batidas: varre BPMs de 80 a 160 num trecho de 30 s; depois
 *    refina com passos de 0,005 BPM num trecho de até 90 s (o erro de BPM acumula como deriva da grade).
 * 3. A fase vem do melhor alinhamento do pente; a grade é estendida até o começo da faixa.
 *
 * Sempre devolve BPM dentro de [80, 160): faixas fora dessa faixa saem na oitava que cabe
 * (ex.: 170 BPM sai como 85). Devolve null se não houver periodicidade clara (sem batida, faixa curta).
 */
object BpmDetector {
    private const val HOP = WaveformDetail.FRAMES_PER_BUCKET
    private const val MIN_BPM = 80.0
    private const val MAX_BPM = 160.0
    private const val MIN_CONFIDENCE = 1.5
    private const val MIN_HIT_RATE = 0.4
    private const val MIN_MODULATION = 0.01

    fun detect(pcm: ShortArray, sampleRate: Int): BeatGrid? {
        val n = pcm.size / 2 / HOP
        val rate = sampleRate.toDouble() / HOP // buckets por segundo
        if (n < (10 * rate).toInt()) return null

        val env = rmsEnvelope(pcm, n)
        val onset = onsetEnvelope(env, rate)

        val winLen = min(n, (90 * rate).toInt())
        val from = if (n > (150 * rate).toInt()) (n - winLen) / 2 else 0
        val to = from + winLen
        val coarseTo = from + min(winLen, (30 * rate).toInt())

        // etapa 1: grosso (0,05 BPM) no trecho curto
        var bestBpm = MIN_BPM
        var bestScore = -1.0
        var bpm = MIN_BPM
        while (bpm < MAX_BPM) {
            val period = 60.0 * rate / bpm
            var ph = 0.0
            while (ph < period) {
                val s = comb(onset, from, coarseTo, period, ph)
                if (s > bestScore) {
                    bestScore = s
                    bestBpm = bpm
                }
                ph += 1.0
            }
            bpm += 0.05
        }

        // etapa 2: fino (0,005 BPM, fase em 1/4 de bucket) no trecho longo
        var fineBpm = bestBpm
        var finePhase = 0.0
        var fineScore = -1.0
        var cand = bestBpm - 0.1
        while (cand <= bestBpm + 0.1 + 1e-9) {
            val period = 60.0 * rate / cand
            var ph = 0.0
            while (ph < period) {
                val s = comb(onset, from, to, period, ph)
                if (s > fineScore) {
                    fineScore = s
                    fineBpm = cand
                    finePhase = ph
                }
                ph += 0.25
            }
            cand += 0.005
        }

        var sum = 0.0
        var envSum = 0.0
        for (i in from until to) {
            sum += onset[i]
            envSum += env[i]
        }
        val mean = sum / (to - from)
        val envMean = envSum / (to - from)
        // sem modulação de energia relevante (silêncio, tom contínuo) não há ritmo a detectar
        if (mean < 1e-9 || mean < MIN_MODULATION * envMean) return null
        if (fineScore / mean < MIN_CONFIDENCE) return null

        // Uma única transiente (ex.: o início de um tom) não é ritmo: exige que boa parte das
        // batidas da grade caia sobre um onset de verdade.
        val period = 60.0 * rate / fineBpm
        var hits = 0
        var beats = 0
        var pos = from + finePhase
        while (pos < to - 2) {
            val i = pos.toInt().coerceAtLeast(1)
            val local = max(onset[i - 1], max(onset[i], onset[i + 1]))
            if (local > 2.0 * mean) hits++
            beats++
            pos += period
        }
        if (beats == 0 || hits.toDouble() / beats < MIN_HIT_RATE) return null

        val p0 = from + finePhase
        val first = p0 - floor(p0 / period) * period
        return BeatGrid(fineBpm, first / rate)
    }

    private fun rmsEnvelope(pcm: ShortArray, n: Int): DoubleArray {
        val env = DoubleArray(n)
        for (b in 0 until n) {
            var sum = 0.0
            val s = b * HOP
            for (f in s until s + HOP) {
                val m = (pcm[2 * f] + pcm[2 * f + 1]) * (0.5 / 32768.0)
                sum += m * m
            }
            env[b] = sqrt(sum / HOP)
        }
        return env
    }

    /** Subidas de energia, menos a média local (~0,5 s), levemente suavizadas. */
    private fun onsetEnvelope(env: DoubleArray, rate: Double): DoubleArray {
        val n = env.size
        val flux = DoubleArray(n)
        for (b in 1 until n) flux[b] = max(0.0, env[b] - env[b - 1])

        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + flux[i]
        val half = max(2, (0.25 * rate).toInt())
        val adj = DoubleArray(n)
        for (i in 0 until n) {
            val lo = max(0, i - half)
            val hi = min(n, i + half + 1)
            val mean = (prefix[hi] - prefix[lo]) / (hi - lo)
            adj[i] = max(0.0, flux[i] - mean)
        }

        val out = DoubleArray(n)
        for (i in 0 until n) {
            val a = if (i > 0) adj[i - 1] else 0.0
            val c = if (i < n - 1) adj[i + 1] else 0.0
            out[i] = 0.25 * a + 0.5 * adj[i] + 0.25 * c
        }
        return out
    }

    /** Média do onset nas posições phase + k*period dentro de [from, to), com interpolação linear. */
    private fun comb(onset: DoubleArray, from: Int, to: Int, period: Double, phase: Double): Double {
        var s = 0.0
        var count = 0
        var pos = from + phase
        val last = (to - 1).toDouble()
        while (pos < last) {
            val i = pos.toInt()
            val f = pos - i
            s += onset[i] * (1 - f) + onset[i + 1] * f
            count++
            pos += period
        }
        return if (count > 0) s / count else 0.0
    }
}
