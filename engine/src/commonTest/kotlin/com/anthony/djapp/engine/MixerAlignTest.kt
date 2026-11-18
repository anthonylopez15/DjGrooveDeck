package com.anthony.djapp.engine

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MixerAlignTest {
    private val rate = 44100

    private fun silence(seconds: Double) = Track("t", rate, ShortArray((rate * seconds).toInt() * 2), beatGrid = null)

    private fun gap(a: Double, b: Double): Double {
        val d = abs(a - b)
        return minOf(d, 1.0 - d)
    }

    @Test
    fun alignedStartMatchesPhaseAtTheBlockBoundary() {
        val mixer = DjMixer(rate)
        val a = mixer.deckA
        val b = mixer.deckB
        a.load(silence(30.0))
        b.load(silence(30.0))
        a.seekToFrame(1.0 * rate)
        a.play()
        b.seekToFrame(3.3 * rate) // fora de fase em relação ao A
        val ga = BeatGrid(120.0, 0.0)
        val gb = BeatGrid(120.0, 0.1)

        mixer.requestAlign(follower = b, leader = a, followerGrid = gb, leaderGrid = ga, mode = DjMixer.AlignMode.StartOnBeat)
        assertFalse(b.playing, "só começa quando a thread de áudio resolver o pedido")
        mixer.render(FloatArray(1024), 512)

        assertTrue(b.playing)
        val pa = ga.phaseAt(a.positionFrames / rate)
        val pb = gb.phaseAt(b.positionFrames / rate)
        assertTrue(gap(pa, pb) < 0.001, "fases alinhadas, diferença=${gap(pa, pb)}")
    }

    @Test
    fun alignWithoutStartKeepsTheFollowerState() {
        val mixer = DjMixer(rate)
        mixer.deckA.load(silence(30.0))
        mixer.deckB.load(silence(30.0))
        mixer.deckA.seekToFrame(2.0 * rate)
        mixer.deckA.play()
        mixer.deckB.seekToFrame(5.2 * rate)
        mixer.deckB.play()
        val ga = BeatGrid(126.0, 0.05)
        val gb = BeatGrid(126.0, 0.31)

        mixer.requestAlign(mixer.deckB, mixer.deckA, gb, ga, mode = DjMixer.AlignMode.PhaseOnly)
        mixer.render(FloatArray(1024), 512)

        assertTrue(mixer.deckB.playing)
        assertTrue(gap(ga.phaseAt(mixer.deckA.positionFrames / rate), gb.phaseAt(mixer.deckB.positionFrames / rate)) < 0.001)
    }

    @Test
    fun aLaterRequestDoesNotLoseAnEarlierStart() {
        val mixer = DjMixer(rate)
        mixer.deckA.load(silence(30.0))
        mixer.deckB.load(silence(30.0))
        mixer.deckA.play()
        val ga = BeatGrid(120.0, 0.0)
        val gb = BeatGrid(120.0, 0.1)

        mixer.requestAlign(mixer.deckB, mixer.deckA, gb, ga, mode = DjMixer.AlignMode.StartOnBeat)
        mixer.requestAlign(mixer.deckB, mixer.deckA, gb, ga, mode = DjMixer.AlignMode.PhaseOnly) // chega antes do bloco
        mixer.render(FloatArray(1024), 512)

        assertTrue(mixer.deckB.playing, "a largada do primeiro pedido não pode se perder")
    }

    @Test
    fun theLastSeekWinsAndNothingIsErasedBetweenBlocks() {
        val deck = Deck(rate)
        deck.load(silence(10.0))
        deck.play()
        deck.seekToFrame(1000.0)
        deck.seekToFrame(2000.0) // duas buscas antes do bloco: vale a última
        deck.renderAdd(FloatArray(1024), 512, 1f)
        assertEquals(2512.0, deck.positionFrames, 1e-6)

        deck.seekToFrame(4000.0) // busca nova depois de uma aplicada
        assertEquals(4000.0, deck.effectivePositionFrames, 0.0)
        deck.renderAdd(FloatArray(1024), 512, 1f)
        assertEquals(4512.0, deck.positionFrames, 1e-6)
    }

    @Test
    fun effectivePositionPrefersAPendingSeek() {
        val deck = Deck(rate)
        deck.load(silence(10.0))
        deck.play()
        deck.renderAdd(FloatArray(1024), 512, 1f)
        assertEquals(512.0, deck.positionFrames, 1e-6)

        deck.seekToFrame(5000.0) // ainda não aplicada pela thread de áudio
        assertEquals(5000.0, deck.effectivePositionFrames, 0.0)

        deck.renderAdd(FloatArray(1024), 512, 1f) // aplica a busca e avança um bloco
        assertEquals(5512.0, deck.positionFrames, 1e-6)
        assertEquals(5512.0, deck.effectivePositionFrames, 1e-6)
    }

    @Test
    fun overviewPeaksAreDerivedFromTheDetail() {
        val pcm = ShortArray(rate * 2 * 5) { if (it % 2000 < 400) 12000 else 300 }
        val t = Track("p", rate, pcm, beatGrid = null)
        assertTrue(t.peaks.isNotEmpty() && t.peaks.size <= 1000)
        assertEquals(1f, t.peaks.max(), 1e-6f)
        assertTrue(t.peaks.all { it in 0f..1f })
    }
}
