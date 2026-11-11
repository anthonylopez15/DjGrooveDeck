package com.anthony.djapp.engine

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReleaseAudioTest {
    private val rate = 44100

    private fun pcm(seconds: Int) = ShortArray(rate * seconds * 2) { i ->
        (sin(2 * PI * 440 * (i / 2) / rate) * 8000).toInt().toShort()
    }

    private fun track(seconds: Int = 4) = Track("t", rate, pcm(seconds), beatGrid = BeatGrid(120.0, 0.1))

    @Test
    fun releaseKeepsStateAndFreesPcm() {
        val deck = Deck(rate)
        deck.load(track())
        deck.tempo = 1.05
        deck.lowDb = -6.0
        deck.seekToFrame(88200.0)
        val frames = deck.track!!.frames
        assertTrue(deck.releaseAudio())
        val t = assertNotNull(deck.track)
        assertTrue(t.released)
        assertEquals(0, t.pcm.size)
        assertEquals(frames, t.frames)
        assertNotNull(t.beatGrid)
        assertTrue(t.peaks.isNotEmpty())
        assertEquals(88200.0, deck.positionFrames)
        assertEquals(1.05, deck.tempo)
        assertEquals(-6.0, deck.lowDb)
        deck.play()
        assertFalse(deck.playing, "sem áudio não toca")
    }

    @Test
    fun cannotReleasePlayingDeck() {
        val deck = Deck(rate)
        deck.load(track())
        deck.play()
        assertFalse(deck.releaseAudio())
        assertFalse(deck.track!!.released)
    }

    @Test
    fun restoreBringsAudioBackAtSamePosition() {
        val deck = Deck(rate)
        deck.load(track())
        deck.seekToFrame(44100.0)
        deck.releaseAudio()
        assertFalse(deck.restoreAudio(pcm(2)), "tamanho diferente é recusado")
        assertTrue(deck.restoreAudio(pcm(4)))
        assertFalse(deck.track!!.released)
        assertEquals(44100.0, deck.positionFrames)
        deck.play()
        assertTrue(deck.playing)
        val out = FloatArray(1024)
        deck.renderAdd(out, 512, 1f)
        assertTrue(out.any { it != 0f })
        assertTrue(deck.positionFrames > 44100.0)
    }

    @Test
    fun releasedDeckRendersSilenceWithoutCrash() {
        val deck = Deck(rate)
        deck.load(track())
        deck.play()
        val out = FloatArray(1024)
        deck.renderAdd(out, 512, 1f)
        deck.pause()
        deck.releaseAudio()
        deck.renderAdd(out, 512, 1f)
        assertTrue(out.all { !it.isNaN() })
    }
}
