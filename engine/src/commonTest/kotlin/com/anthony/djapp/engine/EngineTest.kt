package com.anthony.djapp.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineTest {

    private fun sineTrack(freq: Double, rate: Int = 48000, seconds: Double = 1.0, amp: Double = 0.5): Track {
        val n = (rate * seconds).toInt()
        val pcm = ShortArray(n * 2)
        for (i in 0 until n) {
            val s = (sin(2 * PI * freq * i / rate) * amp * 32767).toInt().toShort()
            pcm[2 * i] = s
            pcm[2 * i + 1] = s
        }
        return Track("sine", rate, pcm)
    }

    private fun rms(buf: FloatArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (i in from until to) sum += buf[i] * buf[i]
        return sqrt(sum / (to - from))
    }

    /** Toca um seno por um deck com o EQ dado e devolve o RMS do trecho estável. */
    private fun rmsThroughEq(freq: Double, low: Double, mid: Double, high: Double): Double {
        val deck = Deck(48000)
        deck.load(sineTrack(freq))
        deck.lowDb = low; deck.midDb = mid; deck.highDb = high
        deck.play()
        // blocos de 512 frames, como a thread de áudio real (a rampa de ganho dura 1 bloco)
        val chunk = 512
        val out = FloatArray(chunk * 2)
        val all = FloatArray(chunk * 90 * 2)
        for (c in 0 until 90) {
            out.fill(0f)
            deck.renderAdd(out, chunk, 1f)
            out.copyInto(all, c * chunk * 2)
        }
        // ignora o início (transiente do filtro)
        return rms(all, 24000, all.size - 2000)
    }

    @Test
    fun crossfaderIsConstantPower() {
        for (i in 0..10) {
            val x = i / 10f
            val a = Crossfader.gainA(x)
            val b = Crossfader.gainB(x)
            assertTrue(abs(a * a + b * b - 1f) < 1e-5f, "potência deve ser 1 em x=$x")
        }
        assertEquals(1f, Crossfader.gainA(0f), 1e-6f)
        assertEquals(0f, Crossfader.gainB(0f), 1e-6f)
        assertEquals(1f, Crossfader.gainB(1f), 1e-6f)
    }

    @Test
    fun eqFlatPassesSignalThrough() {
        val flat = rmsThroughEq(1000.0, 0.0, 0.0, 0.0)
        val expected = 0.5 / sqrt(2.0)
        assertEquals(expected, flat, 0.01)
    }

    @Test
    fun eqLowKillRemovesBassButKeepsTreble() {
        val bass = rmsThroughEq(60.0, Eq3.KILL_DB, 0.0, 0.0)
        val treble = rmsThroughEq(8000.0, Eq3.KILL_DB, 0.0, 0.0)
        val ref = 0.5 / sqrt(2.0)
        assertTrue(bass < ref * 0.1, "graves devem cair >20 dB, rms=$bass")
        assertEquals(ref, treble, 0.02)
    }

    @Test
    fun eqHighKillRemovesTrebleButKeepsBass() {
        val treble = rmsThroughEq(10000.0, 0.0, 0.0, Eq3.KILL_DB)
        val bass = rmsThroughEq(100.0, 0.0, 0.0, Eq3.KILL_DB)
        val ref = 0.5 / sqrt(2.0)
        assertTrue(treble < ref * 0.1, "agudos devem cair >20 dB, rms=$treble")
        assertEquals(ref, bass, 0.02)
    }

    @Test
    fun knobMappingIsNeutralInTheMiddle() {
        assertEquals(0.0, Eq3.knobToDb(0.5f), 1e-9)
        assertEquals(Eq3.KILL_DB, Eq3.knobToDb(0f), 1e-9)
        assertEquals(Eq3.MAX_DB, Eq3.knobToDb(1f), 1e-9)
    }

    @Test
    fun deckAdvancesAtTempoAndSampleRateRatio() {
        // faixa a 44,1 kHz tocando em saída de 48 kHz, tempo 1.0 e depois 1.1
        val deck = Deck(48000)
        deck.load(sineTrack(440.0, rate = 44100, seconds = 3.0))
        deck.play()
        val out = FloatArray(4800 * 2)
        deck.renderAdd(out, 4800, 1f)
        assertEquals(4800 * 44100.0 / 48000, deck.positionFrames, 1e-6)

        val before = deck.positionFrames
        deck.tempo = 1.1
        deck.renderAdd(out, 4800, 1f)
        assertEquals(before + 4800 * 44100.0 / 48000 * 1.1, deck.positionFrames, 1e-6)
    }

    @Test
    fun deckStopsAtEndOfTrack() {
        val deck = Deck(48000)
        deck.load(sineTrack(440.0, seconds = 0.01))
        deck.play()
        deck.renderAdd(FloatArray(48000 * 2), 48000, 1f)
        assertFalse(deck.playing)
        assertEquals(0.0, deck.positionFrames, 0.0)
    }

    @Test
    fun cueSetsPointWhenStoppedAndReturnsWhenPlaying() {
        val deck = Deck(48000)
        deck.load(sineTrack(440.0, seconds = 2.0))
        deck.seekToFraction(0.25f)
        deck.cue() // parado: define o cue em 25%
        val cuePoint = deck.cueFrame
        assertTrue(cuePoint > 0)

        deck.play()
        deck.renderAdd(FloatArray(4800 * 2), 4800, 1f)
        assertTrue(deck.positionFrames > cuePoint)

        deck.cue() // tocando: volta ao cue e para
        assertFalse(deck.playing)
        assertEquals(cuePoint, deck.positionFrames, 1e-6)
    }

    @Test
    fun seekIsAppliedByAudioThread() {
        val deck = Deck(48000)
        deck.load(sineTrack(440.0, seconds = 2.0))
        deck.play()
        deck.seekToFraction(0.5f)
        deck.renderAdd(FloatArray(480 * 2), 480, 1f)
        assertTrue(deck.positionFrames > 0.5 * 96000, "deve continuar a partir de 50%")
    }

    @Test
    fun mixerOutputNeverExceedsOne() {
        val mixer = DjMixer(48000)
        mixer.deckA.load(sineTrack(440.0, amp = 1.0))
        mixer.deckB.load(sineTrack(440.0, amp = 1.0))
        mixer.deckA.lowDb = Eq3.MAX_DB
        mixer.deckA.play(); mixer.deckB.play()
        mixer.masterVolume = 1f
        mixer.crossfader = 0.5f
        val out = FloatArray(2048 * 2)
        mixer.render(out, 2048)
        assertTrue(out.all { abs(it) <= 1f }, "soft clip deve limitar em ±1")
    }

    @Test
    fun peaksAreNormalized() {
        val t = sineTrack(100.0, amp = 0.25)
        assertTrue(t.peaks.isNotEmpty())
        assertEquals(1f, t.peaks.max(), 1e-6f)
        assertTrue(t.peaks.all { it in 0f..1f })
    }
}
