package com.anthony.djapp.data

import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.engine.BeatGrid
import com.anthony.djapp.engine.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** O play passa pela plataforma ([com.anthony.djapp.domain.AudioSystem]) antes de tocar. */
class PlaybackGateTest {
    private val rate = 44100
    private fun track() = Track("t", rate, ShortArray(rate * 2 * 20), beatGrid = BeatGrid(120.0, 0.0))

    @Test
    fun deniedPlaybackBlocksPlayAndNotifies() {
        val c = testSession(audio = FakeAudioSystem(grant = false))
        c.loadTrack(DeckId.A, track())
        c.togglePlay(DeckId.A)
        assertFalse(c.a.playingNow)
        assertEquals("Não dá para tocar agora (ligação em andamento?)", c.state.value.notice)
    }

    @Test
    fun playbackIsRequestedOnlyWhenStartingARealTrack() {
        val audio = FakeAudioSystem()
        val c = testSession(audio = audio)
        c.togglePlay(DeckId.A) // deck vazio: nada a pedir
        assertEquals(0, audio.requests)
        c.loadTrack(DeckId.A, track())
        c.togglePlay(DeckId.A)
        assertTrue(c.a.playingNow)
        assertEquals(1, audio.requests)
        c.togglePlay(DeckId.A) // pausar não pede
        assertFalse(c.a.playingNow)
        assertEquals(1, audio.requests)
    }
}
