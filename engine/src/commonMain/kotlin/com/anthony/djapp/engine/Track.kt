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
    val peaks: FloatArray = computePeaks(pcm),
    val detail: WaveformDetail = WaveformDetail.compute(pcm, sampleRate),
    /** BPM e batidas detectados (andamento fixo); null se não houve periodicidade clara. */
    val beatGrid: BeatGrid? = BpmDetector.detect(pcm, sampleRate),
) {
    init {
        require(pcm.size % 2 == 0) { "PCM deve ser estéreo intercalado" }
    }

    val frames: Int get() = pcm.size / 2
    val durationSec: Double get() = frames.toDouble() / sampleRate

    companion object {
        /** Picos normalizados (0..1) por "balde", usados para desenhar a waveform. */
        fun computePeaks(pcm: ShortArray, buckets: Int = 1000): FloatArray {
            val frames = pcm.size / 2
            if (frames == 0) return FloatArray(0)
            val n = minOf(buckets, frames)
            val out = FloatArray(n)
            var globalMax = 1
            for (b in 0 until n) {
                val start = (b.toLong() * frames / n).toInt()
                val end = ((b + 1).toLong() * frames / n).toInt().coerceAtLeast(start + 1)
                var m = 0
                for (f in start until end) {
                    m = max(m, max(abs(pcm[2 * f].toInt()), abs(pcm[2 * f + 1].toInt())))
                }
                out[b] = m.toFloat()
                if (m > globalMax) globalMax = m
            }
            for (i in out.indices) out[i] /= globalMax
            return out
        }
    }
}
