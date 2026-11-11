package com.anthony.djapp.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyLockTest {
    private val rate = 44100
    private val chunk = 512

    private fun sineTrack(freq: Double, seconds: Double = 4.0): Track {
        val n = (rate * seconds).toInt()
        val pcm = ShortArray(n * 2)
        for (i in 0 until n) {
            val s = (sin(2 * PI * freq * i / rate) * 0.5 * 32767).toInt().toShort()
            pcm[2 * i] = s
            pcm[2 * i + 1] = s
        }
        return Track("sine", rate, pcm, beatGrid = null)
    }

    /** Toca [frames] frames (em blocos de 512, como a thread de áudio) e devolve o canal esquerdo. */
    private fun play(deck: Deck, frames: Int): FloatArray {
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

    /** Frequência medida por cruzamentos de zero ascendentes entre [from] e [to]. */
    private fun frequency(x: FloatArray, from: Int, to: Int): Double {
        var crossings = 0
        for (i in from + 1 until to) if (x[i - 1] < 0f && x[i] >= 0f) crossings++
        return crossings / ((to - from).toDouble() / rate)
    }

    private fun rms(x: FloatArray, from: Int, to: Int): Double {
        var s = 0.0
        for (i in from until to) s += x[i] * x[i]
        return sqrt(s / (to - from))
    }

    private fun deckAt(freq: Double, tempo: Double, keyLock: Boolean): Deck {
        val d = Deck(rate)
        d.load(sineTrack(freq))
        d.tempo = tempo
        d.keyLock = keyLock
        d.play()
        return d
    }

    @Test
    fun keyLockKeepsPitchWhenSpeedingUp() {
        val out = play(deckAt(440.0, 1.1, keyLock = true), rate * 2)
        val f = frequency(out, 10000, rate * 2 - 2000)
        assertTrue(abs(f - 440.0) < 4.0, "esperado ~440 Hz, medido $f")
    }

    @Test
    fun keyLockKeepsPitchWhenSlowingDown() {
        val out = play(deckAt(440.0, 0.9, keyLock = true), rate * 2)
        val f = frequency(out, 10000, rate * 2 - 2000)
        assertTrue(abs(f - 440.0) < 4.0, "esperado ~440 Hz, medido $f")
    }

    @Test
    fun keyLockOffRaisesPitchLikeAVinyl() {
        val out = play(deckAt(440.0, 1.1, keyLock = false), rate * 2)
        val f = frequency(out, 10000, rate * 2 - 2000)
        assertTrue(abs(f - 484.0) < 5.0, "esperado ~484 Hz, medido $f")
    }

    @Test
    fun keyLockAtUnityIsTransparent() {
        // sinal com duas senoides: depois do fade-in do primeiro bloco a saída é cópia da entrada
        val n = rate * 3
        val pcm = ShortArray(n * 2)
        for (i in 0 until n) {
            val v = 0.3 * sin(2 * PI * 220 * i / rate) + 0.2 * sin(2 * PI * 1370 * i / rate)
            val s = (v * 32767).toInt().toShort()
            pcm[2 * i] = s
            pcm[2 * i + 1] = s
        }
        val deck = Deck(rate)
        deck.load(Track("two", rate, pcm, beatGrid = null))
        deck.play()
        val out = play(deck, rate)
        var maxErr = 0f
        for (i in 2048 until rate - 2048) {
            val expected = pcm[2 * i] / 32768f
            maxErr = maxOf(maxErr, abs(out[i] - expected))
        }
        assertTrue(maxErr < 2e-3f, "erro máximo $maxErr")
    }

    @Test
    fun keyLockPositionAdvancesAtTempo() {
        val deck = deckAt(440.0, 1.1, keyLock = true)
        play(deck, 10240)
        assertEquals(10240 * 1.1, deck.positionFrames, 1e-6)
    }

    @Test
    fun keyLockOutputLevelStaysSteady() {
        val out = play(deckAt(440.0, 1.15, keyLock = true), rate * 2)
        val ref = 0.5 / sqrt(2.0)
        for (w in 0 until 5) {
            val from = 10000 + w * 10000
            val r = rms(out, from, from + 10000)
            assertTrue(abs(r - ref) < ref * 0.1, "janela $w: rms=$r esperado ~$ref")
        }
    }

    @Test
    fun keyLockSeekRestartsFromNewPosition() {
        val deck = deckAt(440.0, 1.0, keyLock = true)
        play(deck, 4096)
        deck.seekToFraction(0.5f)
        val out = play(deck, 8192)
        assertTrue(deck.positionFrames > 0.5 * 4.0 * rate, "deve continuar a partir de 50%: ${deck.positionFrames}")
        assertTrue(rms(out, 2048, 8192) > 0.25, "deve voltar a tocar depois da busca")
    }

    @Test
    fun keyLockHandlesBigBuffers() {
        // a fila cresce sob demanda se pedirem muito de uma vez (não acontece em uso normal)
        val deck = deckAt(440.0, 1.0, keyLock = true)
        deck.renderAdd(FloatArray(chunk * 2), chunk, 1f) // estabiliza a rampa de ganho
        val buf = FloatArray(30000 * 2)
        deck.renderAdd(buf, 30000, 1f)
        assertTrue(rms(buf.filterIndexed { i, _ -> i % 2 == 0 }.toFloatArray(), 4000, 30000) > 0.25)
    }

    @Test
    fun beatPhaseMathAndShortestDelta() {
        val grid = BeatGrid(120.0, 0.25) // batidas a cada 0,5 s a partir de 0,25 s
        assertEquals(0.0, grid.phaseAt(0.25), 1e-9)
        assertEquals(0.0, grid.phaseAt(1.25), 1e-9)
        assertEquals(0.5, grid.phaseAt(0.5), 1e-9)
        assertEquals(0.5, grid.phaseAt(0.0), 1e-9) // antes da primeira batida também funciona

        assertEquals(0.25, beatPhaseDelta(0.5, 0.25), 1e-9)
        assertEquals(-0.25, beatPhaseDelta(0.25, 0.5), 1e-9)
        assertEquals(-0.1, beatPhaseDelta(0.05, 0.95), 1e-9 + 0.2) // caminho mais curto, cruzando 0
        assertTrue(abs(beatPhaseDelta(0.05, 0.95) - 0.1) < 1e-9)
        assertTrue(abs(beatPhaseDelta(0.95, 0.05) + 0.1) < 1e-9)
    }
}
