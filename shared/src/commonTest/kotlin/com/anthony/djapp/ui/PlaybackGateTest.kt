package com.anthony.djapp.ui

import com.anthony.djapp.engine.BeatGrid
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** O play passa pela plataforma (foco de áudio) antes de tocar. */
class PlaybackGateTest {
    private val rate = 44100
    private fun track() = Track("t", rate, ShortArray(rate * 2 * 20), beatGrid = BeatGrid(120.0, 0.0))

    @Test
    fun deniedGateBlocksPlay() {
        val c = DjController(DjMixer(rate))
        c.loadTrack(DeckId.A, track())
        c.playbackGate = { false }
        c.togglePlay(DeckId.A)
        assertFalse(c.a.playingNow)
    }

    @Test
    fun gateIsAskedOnlyWhenStartingARealTrack() {
        val c = DjController(DjMixer(rate))
        var asked = 0
        c.playbackGate = { asked++; true }
        c.togglePlay(DeckId.A) // deck vazio: nada a pedir
        assertEquals(0, asked)
        c.loadTrack(DeckId.A, track())
        c.togglePlay(DeckId.A)
        assertTrue(c.a.playingNow)
        assertEquals(1, asked)
        c.togglePlay(DeckId.A) // pausar não pede
        assertFalse(c.a.playingNow)
        assertEquals(1, asked)
    }
}
