package com.anthony.djapp.engine

import kotlin.math.abs
import kotlin.math.max

/**
 * Faixa decodificada em memória: PCM 16 bits, estéreo intercalado (L,R,L,R...).
 * Guardar em Short (e não Float) corta a RAM pela metade: ~53 MB para 5 min a 44,1 kHz.
 */
class Track(
    val name: String,
    val sampleRate: Int,
    val pcm: ShortArray,
    val peaks: FloatArray = computePeaks(pcm),
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
