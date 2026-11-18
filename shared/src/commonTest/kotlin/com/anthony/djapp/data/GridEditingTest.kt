package com.anthony.djapp.data

import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.domain.LibraryEntry
import com.anthony.djapp.engine.BeatGrid
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Corrigir BPM e grade de batidas de uma faixa: vale na hora, fica guardado e volta ao carregar. */
class GridEditingTest {
    private val rate = 44100
    private val entryA = LibraryEntry("t\nA", "A.mp3", false, 1L)
    private val entryB = LibraryEntry("t\nB", "B.mp3", false, 2L)

    private fun track(bpm: Double?, first: Double = 0.1) =
        Track("t", rate, ShortArray(rate * 2 * 40), beatGrid = bpm?.let { BeatGrid(it, first) })

    private class Rig(val mixer: DjMixer = DjMixer(44100), val grids: InMemoryTrackGridStore = InMemoryTrackGridStore()) {
        val c = testSession(mixer = mixer, grids = grids)
    }

    private fun Rig.settle() {
        mixer.render(FloatArray(2 * 512), 512)
        c.syncFromEngine()
    }

    @Test
    fun correctedGridAppliesAtOnceAndIsMarkedAsEdited() {
        val r = Rig()
        r.c.loadTrack(DeckId.A, track(67.0), entryA) // detectou metade do andamento
        assertFalse(r.c.a.state.gridEdited)

        r.c.setGrid(DeckId.A, bpm = 134.0, firstBeatSec = 12.3)

        val s = r.c.a.state
        assertEquals(134.0, s.bpm)
        assertTrue(s.gridEdited)
        // a primeira batida fica dentro do primeiro período (60/134 = 0,448 s), mantendo a mesma fase
        assertTrue(s.firstBeatSec in 0.0..(60.0 / 134.0), "primeira batida ${s.firstBeatSec}")
        val period = 60.0 / 134.0
        val phase = ((12.3 - s.firstBeatSec) / period).let { it - kotlin.math.floor(it) }
        assertTrue(phase < 1e-6 || phase > 1 - 1e-6, "a fase da grade se manteve: $phase")
    }

    @Test
    fun correctionIsRememberedWhenTheSameFileIsLoadedAgain() {
        val r = Rig()
        r.c.loadTrack(DeckId.A, track(67.0), entryA)
        r.c.setGrid(DeckId.A, 134.0, 0.2)

        r.c.loadTrack(DeckId.B, track(90.0), entryB) // outra faixa, outro deck: sem correção
        assertFalse(r.c.b.state.gridEdited)
        assertEquals(90.0, r.c.b.state.bpm)

        // carrega o arquivo A de novo (mesmo id e stamp), com a detecção original errada
        r.c.loadTrack(DeckId.B, track(67.0), entryA)
        assertEquals(134.0, r.c.b.state.bpm)
        assertTrue(r.c.b.state.gridEdited)
    }

    @Test
    fun aChangedFileDoesNotInheritTheOldCorrection() {
        val r = Rig()
        r.c.loadTrack(DeckId.A, track(67.0), entryA)
        r.c.setGrid(DeckId.A, 134.0, 0.2)
        r.c.loadTrack(DeckId.B, track(70.0), entryA.copy(stamp = 99)) // arquivo alterado
        assertEquals(70.0, r.c.b.state.bpm)
        assertFalse(r.c.b.state.gridEdited)
    }

    @Test
    fun resetGoesBackToTheDetectedGridAndForgetsTheCorrection() {
        val r = Rig()
        r.c.loadTrack(DeckId.A, track(67.0, first = 0.3), entryA)
        r.c.setGrid(DeckId.A, 134.0, 5.0)
        r.c.resetGrid(DeckId.A)

        val s = r.c.a.state
        assertEquals(67.0, s.bpm)
        assertEquals(0.3, s.firstBeatSec, 1e-9)
        assertFalse(s.gridEdited)
        assertNull(r.grids.get(entryA))
    }

    @Test
    fun aTrackWithoutDetectedBpmCanGetAGrid() {
        val r = Rig()
        r.c.loadTrack(DeckId.A, track(null), entryA)
        assertNull(r.c.a.state.bpm)
        r.c.setGrid(DeckId.A, 120.0, 0.0) // por exemplo, vindo do tap tempo
        assertEquals(120.0, r.c.a.state.bpm)
        assertNotNull(r.c.a.state.beatGrid())
    }

    @Test
    fun bpmIsKeptWithinTheLimits() {
        val r = Rig()
        r.c.loadTrack(DeckId.A, track(120.0), entryA)
        r.c.setGrid(DeckId.A, 900.0, 0.0)
        assertEquals(220.0, r.c.a.state.bpm)
    }

    @Test
    fun editingWithoutATrackOrWithoutASourceDoesNothing() {
        val r = Rig()
        r.c.setGrid(DeckId.A, 120.0, 0.0) // deck vazio
        assertNull(r.c.a.state.bpm)
        r.c.loadTrack(DeckId.B, track(100.0)) // sem origem: não dá para guardar a correção
        r.c.setGrid(DeckId.B, 200.0, 0.0)
        assertEquals(100.0, r.c.b.state.bpm)
    }

    @Test
    fun editingUndoesTheBeatLockAndRematchesTheFollowerTempo() {
        val r = Rig()
        r.c.loadTrack(DeckId.A, track(126.0, 0.10), entryA)
        r.c.loadTrack(DeckId.B, track(122.0, 0.30), entryB)
        r.c.togglePlay(DeckId.A)
        r.c.deck(DeckId.B).seek(0.2f)
        r.c.togglePlay(DeckId.B)
        r.settle()
        r.c.toggleSync(DeckId.B)
        r.settle()
        assertTrue(r.c.state.value.locked)
        assertEquals(3.28f, r.c.b.state.tempoPercent, 0.05f) // 122 -> 126

        // o líder estava com o BPM errado: o certo é 120
        r.c.setGrid(DeckId.A, 120.0, 0.10)
        r.settle()

        assertEquals(120.0, r.c.a.state.bpm)
        assertEquals(-1.64f, r.c.b.state.tempoPercent, 0.05f) // 122 -> 120
        assertTrue(r.c.state.value.bpmMatched, "o seguidor igualou o novo BPM")
    }
}
