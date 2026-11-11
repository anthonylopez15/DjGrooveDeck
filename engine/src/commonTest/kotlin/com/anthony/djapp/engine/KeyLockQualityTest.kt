package com.anthony.djapp.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Qualidade do key lock em material mais parecido com música: graves e acordes, e com a taxa da
 * faixa (44,1 kHz) diferente da saída do aparelho (48 kHz), que é o caso comum no celular.
 */
class KeyLockQualityTest {
    private val chunk = 512

    private fun tonalTrack(rate: Int, freqs: List<Pair<Double, Double>>, seconds: Double = 6.0): Track {
        val n = (rate * seconds).toInt()
        val pcm = ShortArray(n * 2)
        for (i in 0 until n) {
            var v = 0.0
            for ((f, a) in freqs) v += a * sin(2 * PI * f * i / rate)
            val s = (v.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
            pcm[2 * i] = s
            pcm[2 * i + 1] = s
        }
        return Track("tonal", rate, pcm, beatGrid = null)
    }

    private fun play(track: Track, outRate: Int, tempo: Double, keyLock: Boolean, frames: Int): FloatArray {
        val deck = Deck(outRate)
        deck.load(track)
        deck.tempo = tempo
        deck.keyLock = keyLock
        deck.play()
        val out = FloatArray(frames)
        val buf = FloatArray(chunk * 2)
        var done = 0
        while (done < frames) {
            val n = minOf(chunk, frames - done)
            buf.fill(0f)
            deck.renderAdd(buf, n, 1f)
            for (i in 0 until n) out[done + i] = buf[2 * i]
            done += n
        }
        return out
    }

    /** Frequência do pico espectral perto de [expected] (±4%, DFT com passo de 0,1 Hz). */
    private fun peakNear(x: FloatArray, from: Int, to: Int, rate: Int, expected: Double): Double {
        var bestF = expected
        var bestMag = -1.0
        var f = expected * 0.96
        while (f <= expected * 1.04) {
            var re = 0.0
            var im = 0.0
            val w = 2 * PI * f / rate
            for (i in from until to) {
                val win = 0.5 - 0.5 * cos(2 * PI * (i - from) / (to - from)) // Hann
                re += x[i] * win * cos(w * i)
                im -= x[i] * win * sin(w * i)
            }
            val mag = sqrt(re * re + im * im)
            if (mag > bestMag) {
                bestMag = mag
                bestF = f
            }
            f += 0.1
        }
        return bestF
    }

    @Test
    fun pitchIsPreservedAcrossSampleRatesAndTempos() {
        val notes = listOf(55.0 to 0.35, 220.0 to 0.2, 261.63 to 0.2, 329.63 to 0.2)
        val outRate = 48000
        val problems = ArrayList<String>()
        for (trackRate in listOf(44100, 48000)) {
            val track = tonalTrack(trackRate, notes)
            for (tempo in listOf(0.92, 0.97, 1.01, 1.03, 1.08, 1.15)) {
                val out = play(track, outRate, tempo, keyLock = true, frames = outRate * 4)
                val from = 24000
                val to = from + 32768
                for ((f, _) in notes) {
                    val measured = peakNear(out, from, to, outRate, f)
                    val cents = 1200.0 * kotlin.math.ln(measured / f) / kotlin.math.ln(2.0)
                    if (abs(cents) > 10.0) problems += "trackRate=$trackRate tempo=$tempo nota=$f: ${cents.toInt()} cents"
                }
            }
        }
        assertTrue(problems.isEmpty(), "tom fora de ±10 cents: $problems")
    }
}
