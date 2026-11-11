package com.anthony.djapp.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Waveform de alta resolução para a visão ampliada: um "balde" a cada [framesPerBucket] frames.
 * [peak] é a amplitude (0..1, normalizada) e [bass] a fração de energia abaixo de ~250 Hz (0..1),
 * usada para colorir (grave = vermelho, agudo = azul).
 */
class WaveformDetail(val framesPerBucket: Int, val peak: FloatArray, val bass: FloatArray) {
    val size: Int get() = peak.size

    companion object {
        const val FRAMES_PER_BUCKET = 256

        fun compute(pcm: ShortArray, sampleRate: Int): WaveformDetail {
            val frames = pcm.size / 2
            val n = (frames + FRAMES_PER_BUCKET - 1) / FRAMES_PER_BUCKET
            val peak = FloatArray(n)
            val bass = FloatArray(n)
            val a = 1.0 - exp(-2.0 * PI * 250.0 / sampleRate)
            var lp = 0.0
            var globalMax = 1f
            for (b in 0 until n) {
                val start = b * FRAMES_PER_BUCKET
                val end = min(frames, start + FRAMES_PER_BUCKET)
                var pk = 0
                var eLow = 0.0
                var eAll = 0.0
                for (f in start until end) {
                    val l = pcm[2 * f].toInt()
                    val r = pcm[2 * f + 1].toInt()
                    val m = (l + r) * 0.5
                    lp += a * (m - lp)
                    eLow += lp * lp
                    eAll += m * m
                    pk = max(pk, max(abs(l), abs(r)))
                }
                peak[b] = pk.toFloat()
                if (pk > globalMax) globalMax = pk.toFloat()
                bass[b] = if (eAll < 1.0) 0.5f else sqrt(eLow / eAll).coerceIn(0.0, 1.0).toFloat()
            }
            for (i in peak.indices) peak[i] /= globalMax
            return WaveformDetail(FRAMES_PER_BUCKET, peak, bass)
        }
    }
}

/**
 * Faixa decodificada em memória: PCM 16 bits, estéreo intercalado (L,R,L,R...).
 * Guardar em Short (e não Float) corta a RAM pela metade: ~53 MB para 5 min a 44,1 kHz.
 */
class Track(
    val name: String,
    val sampleRate: Int,
    val pcm: ShortArray,
    val detail: WaveformDetail = WaveformDetail.compute(pcm, sampleRate),
    /** Picos normalizados (0..1) da faixa inteira, para a visão geral; derivados de [detail]. */
    val peaks: FloatArray = overviewPeaks(detail),
    /** BPM e batidas detectados (andamento fixo); null se não houve periodicidade clara. */
    val beatGrid: BeatGrid? = BpmDetector.detect(pcm, sampleRate),
    /** Tamanho da faixa em frames; só difere do PCM numa faixa [released] (sem áudio, só onda e grade). */
    val frames: Int = pcm.size / 2,
) {
    init {
        require(pcm.size % 2 == 0) { "PCM deve ser estéreo intercalado" }
        require(pcm.isEmpty() || pcm.size / 2 == frames) { "frames não bate com o PCM" }
    }

    /** Sem o áudio em memória (liberado por falta de RAM): mantém duração, onda e grade de batidas. */
    val released: Boolean get() = pcm.isEmpty() && frames > 0

    /** Cópia leve, sem o PCM, para o app não segurar dezenas de MB com a tela fora de cena. */
    fun releasedCopy(): Track = Track(name, sampleRate, ShortArray(0), detail, peaks, beatGrid, frames)

    /** Esta faixa (liberada) com o áudio [pcm] de volta, mantendo onda e grade. */
    fun withPcm(pcm: ShortArray): Track {
        require(pcm.size / 2 == frames) { "o áudio recarregado tem outro tamanho" }
        return Track(name, sampleRate, pcm, detail, peaks, beatGrid, frames)
    }

    val durationSec: Double get() = frames.toDouble() / sampleRate

    companion object {
        /** Reduz os picos detalhados a [buckets] picos (máximo de cada grupo), sem reler o PCM. */
        fun overviewPeaks(detail: WaveformDetail, buckets: Int = 1000): FloatArray {
            val size = detail.size
            if (size == 0) return FloatArray(0)
            val n = minOf(buckets, size)
            val out = FloatArray(n)
            for (b in 0 until n) {
                val from = (b.toLong() * size / n).toInt()
                val to = ((b + 1).toLong() * size / n).toInt().coerceAtLeast(from + 1)
                var m = 0f
                for (i in from until to) if (detail.peak[i] > m) m = detail.peak[i]
                out[b] = m
            }
            return out
        }
    }
}
