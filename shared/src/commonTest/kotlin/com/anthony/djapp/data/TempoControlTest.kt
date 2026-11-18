package com.anthony.djapp.data

import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.engine.BeatGrid
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Andamento manual (− e +): vale no motor, respeita o limite e convive com o SYNC como no djay. */
class TempoControlTest {
    private val rate = 44100

    private fun track(bpm: Double, first: Double) = Track("t", rate, ShortArray(rate * 2 * 40), beatGrid = BeatGrid(bpm, first))

    private class Rig(val mixer: DjMixer = DjMixer(44100)) {
        val c = testSession(mixer = mixer)
    }

    private fun rig(): Rig {
        val r = Rig()
        r.c.loadTrack(DeckId.A, track(126.0, 0.10))
        r.c.loadTrack(DeckId.B, track(122.0, 0.30))
        return r
    }

    private fun Rig.settle() {
        mixer.render(FloatArray(2 * 512), 512)
        c.syncFromEngine()
    }

    @Test
    fun tempoChangesTheStateAndTheEngine() {
        val r = rig()
        r.c.setTempo(DeckId.A, 3.5f)
        assertEquals(3.5f, r.c.a.state.tempoPercent)
        assertEquals(1.035, r.mixer.deckA.tempo, 1e-9)
        assertEquals(126.0 * 1.035, r.c.a.state.effectiveBpm!!, 1e-9)

        r.c.setTempo(DeckId.A, 0f) // tocar no percentual: volta ao original
        assertEquals(1.0, r.mixer.deckA.tempo, 1e-9)
    }

    @Test
    fun tempoIsLimitedToTwentyPercent() {
        val r = rig()
        r.c.setTempo(DeckId.A, 40f)
        assertEquals(20f, r.c.a.state.tempoPercent)
        r.c.setTempo(DeckId.A, -40f)
        assertEquals(-20f, r.c.a.state.tempoPercent)
    }

    @Test
    fun emptyDeckIgnoresTempo() {
        val c = testSession()
        c.setTempo(DeckId.A, 5f)
        assertEquals(0f, c.a.state.tempoPercent)
    }

    @Test
    fun changingTheTempoOfAFollowerTurnsItsSyncOff() {
        val r = rig()
        r.c.toggleSync(DeckId.B) // B segue A
        assertTrue(r.c.b.state.syncOn)

        r.c.setTempo(DeckId.B, 5f)

        assertFalse(r.c.b.state.syncOn, "mexer no andamento de quem segue desliga o SYNC dele")
        assertEquals(5f, r.c.b.state.tempoPercent, "e o andamento fica onde o usuário pôs")
    }

    @Test
    fun changingTheTempoOfTheLeaderMakesThePlayingFollowerKeepUp() {
        val r = rig()
        r.c.togglePlay(DeckId.A)
        r.c.deck(DeckId.B).seek(0.2f)
        r.c.togglePlay(DeckId.B)
        r.settle()
        r.c.toggleSync(DeckId.B)
        r.settle()
        assertTrue(r.c.state.value.locked)

        r.c.setTempo(DeckId.A, 4f) // acelera o líder
        r.settle()

        assertEquals(126.0 * 1.04, r.c.a.state.effectiveBpm!!, 1e-9)
        assertTrue(r.c.state.value.bpmMatched, "o seguidor acompanhou o novo andamento")
        assertTrue(r.c.b.state.syncOn, "o SYNC do seguidor continua ligado")
        assertTrue(r.c.state.value.locked, "e as batidas se alinham de novo")
    }

    @Test
    fun aLeaderChangeWhileTheFollowerIsStoppedLeavesItAlone() {
        val r = rig()
        r.c.toggleSync(DeckId.B) // B armado, parado
        r.c.setTempo(DeckId.A, 4f)
        assertEquals(0f, r.c.b.state.tempoPercent, "parado, só iguala quando der play")
    }
}
