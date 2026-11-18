package com.anthony.djapp.data

import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.engine.BeatGrid
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.Track
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Play com SYNC larga no tempo 1 do compasso; com o outro deck tocando, espera o tempo 1 dele. */
class BarStartSessionTest {
    private val rate = 44100

    private fun track(bpm: Double?, first: Double) =
        Track("t", rate, ShortArray(rate * 2 * 120), beatGrid = bpm?.let { BeatGrid(it, first) })

    private class Rig(val mixer: DjMixer = DjMixer(44100)) {
        val c = testSession(mixer = mixer)
    }

    private fun rig(bpmB: Double? = 120.0): Rig {
        val r = Rig()
        r.c.loadTrack(DeckId.A, track(120.0, 0.2))
        r.c.loadTrack(DeckId.B, track(bpmB, 1.0))
        return r
    }

    private fun Rig.settle() {
        mixer.render(FloatArray(2 * 512), 512)
        c.syncFromEngine()
    }

    @Test
    fun withSyncOnAndTheOtherStoppedItStartsAtOnceFromTheNearestBarStart() {
        val r = rig()
        r.c.toggleSync(DeckId.B)
        r.c.deck(DeckId.B).seek(0.3f) // 0,3 da faixa de 120 s = 36 s; compassos de 2 s a partir de 1,0 s
        val grid = r.c.b.state.beatGrid()!!
        val expected = grid.nearestBarStart(r.c.b.engineDeck.effectivePositionFrames / rate)

        r.c.togglePlay(DeckId.B)

        assertTrue(r.c.b.playingNow, "toca já: não há líder para esperar")
        assertFalse(r.mixer.deckB.startPending)
        assertEquals(expected, r.c.b.engineDeck.effectivePositionFrames / rate, 1e-6, "partiu do tempo 1 mais próximo")
    }

    @Test
    fun withTheOtherDeckPlayingItWaitsAndThePlayButtonShowsIt() {
        val r = rig()
        r.c.togglePlay(DeckId.A)
        r.settle()
        r.c.toggleSync(DeckId.B)
        r.c.deck(DeckId.B).seek(0.3f)

        r.c.togglePlay(DeckId.B)
        r.c.syncFromEngine()

        // a menos que o tempo 1 do A caia justo agora, B está esperando (parado, mas pendente)
        if (!r.c.b.playingNow) {
            assertTrue(r.c.b.state.startPending, "o estado mostra que está esperando")
            assertFalse(r.c.b.state.playing)
        }
        var blocks = 0
        while (r.mixer.deckB.startPending && blocks++ < 5000) r.settle()
        r.settle()
        assertTrue(r.c.b.playingNow)
        assertFalse(r.c.b.state.startPending)
        assertTrue(r.c.state.value.locked)
    }

    @Test
    fun tappingPlayAgainWhileWaitingCancelsTheStart() {
        val r = rig()
        r.c.togglePlay(DeckId.A)
        r.settle()
        r.c.toggleSync(DeckId.B)
        r.c.deck(DeckId.B).seek(0.3f)
        // coloca o líder logo depois de um tempo 1, para a espera ser longa (quase um compasso inteiro)
        r.mixer.deckA.seekToFrame((0.2 + 2.0 * 10 + 0.05) * rate)
        r.settle()

        r.c.togglePlay(DeckId.B)
        assertTrue(r.mixer.deckB.startPending, "esperando o próximo tempo 1")

        r.c.togglePlay(DeckId.B) // tocar de novo desiste
        assertFalse(r.mixer.deckB.startPending)
        repeat(300) { r.settle() }
        assertFalse(r.c.b.playingNow, "cancelado: não larga")
    }

    @Test
    fun withoutSyncPlayStartsExactlyWhereItWasPaused() {
        val r = rig()
        r.c.deck(DeckId.B).seek(0.3f)
        val before = r.c.b.engineDeck.effectivePositionFrames
        r.c.togglePlay(DeckId.B)
        assertTrue(r.c.b.playingNow)
        assertEquals(before, r.c.b.engineDeck.effectivePositionFrames, 1.0, "sem SYNC, nada de encaixe")
    }

    @Test
    fun aTrackWithoutBpmJustPlays() {
        val r = rig(bpmB = null)
        r.c.toggleSync(DeckId.A) // SYNC do outro deck ligado, mas B não tem grade
        r.c.deck(DeckId.B).seek(0.3f)
        val before = r.c.b.engineDeck.effectivePositionFrames
        r.c.togglePlay(DeckId.B)
        assertTrue(r.c.b.playingNow)
        assertTrue(abs(r.c.b.engineDeck.effectivePositionFrames - before) < 1.0)
    }

    @Test
    fun pausingEverythingWhileWaitingDropsTheWait() {
        val r = rig()
        r.c.togglePlay(DeckId.A)
        r.settle()
        r.c.toggleSync(DeckId.B)
        r.mixer.deckA.seekToFrame((0.2 + 2.0 * 10 + 0.05) * rate)
        r.settle()
        r.c.togglePlay(DeckId.B)
        assertTrue(r.mixer.deckB.startPending)

        r.c.pauseAll() // fone desconectado, ligação, "Pausar" da notificação...

        assertFalse(r.mixer.deckB.startPending)
        repeat(300) { r.settle() }
        assertFalse(r.c.b.playingNow, "a largada pendente não pode tocar depois da pausa")
    }
}
