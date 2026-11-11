package com.anthony.djapp.ui

import com.anthony.djapp.engine.BeatGrid
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.Track
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Comportamento do SYNC (modelo djay): estados do botão, andamento, batidas e desligar. */
class SyncTest {
    private val rate = 44100

    private fun track(name: String, bpm: Double, firstBeat: Double) =
        Track(name, rate, ShortArray(rate * 2 * 40), beatGrid = BeatGrid(bpm, firstBeat))

    private class Rig {
        val mixer = DjMixer(44100)
        val c = DjController(mixer)
    }

    /** A = 126 BPM, B = 122 BPM, os dois carregados. */
    private fun rig(): Rig {
        val r = Rig()
        assertTrue(r.c.loadTrack(DeckId.A, track("A", 126.0, 0.10)))
        assertTrue(r.c.loadTrack(DeckId.B, track("B", 122.0, 0.30)))
        return r
    }

    /** Roda um bloco de áudio (o alinhamento de batidas acontece lá) e atualiza o estado da tela. */
    private fun Rig.settle() {
        mixer.render(FloatArray(2 * 512), 512)
        c.poll()
    }

    private fun Rig.bothPlaying() {
        c.togglePlay(DeckId.A)
        // B começa em outro ponto, fora de fase em relação ao A
        c.deck(DeckId.B).seek(0.2f)
        c.togglePlay(DeckId.B)
        settle()
    }

    private fun phaseGap(r: Rig): Double {
        val pa = r.c.a.beatGrid()!!.phaseAt(r.c.a.engineDeck.effectivePositionFrames / rate)
        val pb = r.c.b.beatGrid()!!.phaseAt(r.c.b.engineDeck.effectivePositionFrames / rate)
        val d = abs(pa - pb)
        return minOf(d, 1.0 - d)
    }

    @Test
    fun tapWithBothPlayingMatchesTempoAndLocksBeats() {
        val r = rig()
        r.bothPlaying()
        assertFalse(r.c.bpmMatched)

        r.c.toggleSync(DeckId.B) // B segue A
        r.settle()

        assertEquals(126.0, r.c.b.effectiveBpm!!, 0.2) // 122 * (1 + 3,3%) ≈ 126
        assertTrue(r.c.bpmMatched)
        assertTrue(r.c.locked)
        assertEquals(SyncState.Locked, r.c.syncState(DeckId.A)) // os dois botões acendem
        assertEquals(SyncState.Locked, r.c.syncState(DeckId.B))
        assertTrue(phaseGap(r) < 0.01, "batidas alinhadas: ${phaseGap(r)}")
        assertEquals(0f, r.c.a.tempoPercent) // o líder não muda
    }

    @Test
    fun turningOffTheFollowerRestoresItsOriginalTempo() {
        val r = rig()
        r.bothPlaying()
        r.c.toggleSync(DeckId.B)
        assertTrue(r.c.b.tempoPercent > 3f)

        r.c.toggleSync(DeckId.B) // desliga o seguidor
        r.settle()

        assertEquals(0f, r.c.b.tempoPercent)
        assertEquals(122.0, r.c.b.effectiveBpm!!, 1e-9)
        assertFalse(r.c.b.syncOn)
        assertFalse(r.c.locked)
        assertEquals(SyncState.Off, r.c.syncState(DeckId.A))
        assertEquals(SyncState.Off, r.c.syncState(DeckId.B))
        assertFalse(r.c.bpmMatched)
    }

    @Test
    fun pausingKeepsSyncOnButUnlocksAndKeepsTempo() {
        val r = rig()
        r.bothPlaying()
        r.c.toggleSync(DeckId.B)
        val tempo = r.c.b.tempoPercent

        r.c.togglePlay(DeckId.A) // pausa o líder
        r.settle()

        assertFalse(r.c.locked)
        assertEquals(SyncState.Armed, r.c.syncState(DeckId.B)) // contorno: ligado, sem travar
        assertEquals(SyncState.Off, r.c.syncState(DeckId.A))   // o líder (flag desligada) apaga
        assertEquals(tempo, r.c.b.tempoPercent)
        assertTrue(r.c.b.syncOn)
    }

    @Test
    fun resumingTheLeaderRelocksBeats() {
        val r = rig()
        r.bothPlaying()
        r.c.toggleSync(DeckId.B)
        r.c.togglePlay(DeckId.A)       // pausa
        r.c.deck(DeckId.A).seek(0.31f) // e volta noutro ponto
        r.c.togglePlay(DeckId.A)       // play: largada alinhada
        r.settle()

        assertTrue(r.c.locked)
        assertTrue(phaseGap(r) < 0.01, "batidas alinhadas: ${phaseGap(r)}")
        assertEquals(SyncState.Locked, r.c.syncState(DeckId.B))
    }

    @Test
    fun pausedFollowerKeepsItsTempoUntilPlay() {
        val r = rig()
        r.c.togglePlay(DeckId.A) // só o A toca
        r.settle()

        r.c.toggleSync(DeckId.B) // B parado: arma o SYNC, mas não muda de andamento
        assertTrue(r.c.b.syncOn)
        assertEquals(0f, r.c.b.tempoPercent)
        assertEquals(SyncState.Armed, r.c.syncState(DeckId.B))
        assertFalse(r.c.locked)

        r.c.deck(DeckId.B).seek(0.17f)
        r.c.togglePlay(DeckId.B) // play: entra no andamento do A, na batida certa
        r.settle()

        assertEquals(126.0, r.c.b.effectiveBpm!!, 0.2)
        assertTrue(r.c.locked)
        assertTrue(phaseGap(r) < 0.01)
    }

    @Test
    fun tappingTheLeaderWhileLockedDissolvesLinkAndKeepsTempo() {
        val r = rig()
        r.bothPlaying()
        r.c.toggleSync(DeckId.B)
        val tempo = r.c.b.tempoPercent

        r.c.toggleSync(DeckId.A) // botão do líder, aceso só pelo vínculo
        r.settle()

        assertFalse(r.c.locked)
        assertFalse(r.c.a.syncOn)
        assertFalse(r.c.b.syncOn)
        assertEquals(tempo, r.c.b.tempoPercent) // cada um fica como está
        assertTrue(r.c.bpmMatched)              // o BPM segue igual (azul)
        assertEquals(SyncState.Off, r.c.syncState(DeckId.B))
    }

    @Test
    fun syncDoesNotLockWhenTheTempoCannotMatch() {
        val r = Rig()
        r.c.loadTrack(DeckId.A, track("A", 85.0, 0.1))
        r.c.loadTrack(DeckId.B, track("B", 128.0, 0.3))
        r.bothPlaying()

        r.c.toggleSync(DeckId.B) // 85/128 pede -34%: passa do limite de ±16%
        r.settle()

        assertEquals(-16f, r.c.b.tempoPercent)
        assertFalse(r.c.bpmMatched)
        assertFalse(r.c.locked, "BPM diferentes não podem aparecer como travados")
        assertEquals(SyncState.Armed, r.c.syncState(DeckId.B)) // ligado, mas sem travar
    }

    @Test
    fun syncIsRefusedWithoutBpm() {
        val r = Rig()
        r.c.loadTrack(DeckId.A, Track("A", rate, ShortArray(rate * 2 * 40), beatGrid = null))
        r.c.loadTrack(DeckId.B, track("B", 122.0, 0.3))
        assertFalse(r.c.canToggleSync(DeckId.B))
        r.c.toggleSync(DeckId.B)
        assertFalse(r.c.b.syncOn)
    }

    @Test
    fun loadingATrackResetsSyncAndTempo() {
        val r = rig()
        r.bothPlaying()
        r.c.toggleSync(DeckId.B)
        r.c.togglePlay(DeckId.B) // pausa B para poder trocar a faixa
        assertTrue(r.c.loadTrack(DeckId.B, track("B2", 124.0, 0.2)))
        assertFalse(r.c.b.syncOn)
        assertEquals(0f, r.c.b.tempoPercent)
        assertNotNull(r.c.b.bpm)
        assertEquals(124.0, r.c.b.bpm!!, 1e-9)
    }

    @Test
    fun releasedAudioKeepsDeckStateAndReloads() {
        val r = Rig()
        val entry = LibraryEntry("t\nA", "A", false, 1L)
        assertTrue(r.c.loadTrack(DeckId.A, track("A", 126.0, 0.10), entry))
        assertTrue(r.c.loadTrack(DeckId.B, track("B", 122.0, 0.30), LibraryEntry("t\nB", "B", false, 2L)))
        r.c.toggleSync(DeckId.B)
        r.c.deck(DeckId.A).seek(0.5f)
        r.c.deck(DeckId.A).changeTempo(3f)
        r.settle()
        val pos = r.c.deck(DeckId.A).positionSec
        assertEquals(2, r.c.releaseIdleAudio())
        val a = r.c.deck(DeckId.A)
        assertTrue(a.audioReleased)
        assertTrue(a.hasTrack)
        assertEquals(pos, a.positionSec)
        assertEquals(3f, a.tempoPercent)
        assertTrue(r.c.deck(DeckId.B).syncOn)
        a.togglePlay()
        assertFalse(a.playingNow, "sem áudio não toca")
        assertTrue(a.restoreAudio(track("A", 126.0, 0.10)))
        assertFalse(a.audioReleased)
        a.togglePlay()
        assertTrue(a.playingNow)
    }

    @Test
    fun playingDeckAndUnknownSourceAreNotReleased() {
        val r = Rig()
        r.c.loadTrack(DeckId.A, track("A", 126.0, 0.1), LibraryEntry("t\nA", "A", false, 1L))
        r.c.loadTrack(DeckId.B, track("B", 122.0, 0.3)) // sem origem: não dá para recarregar
        r.c.togglePlay(DeckId.A)
        assertEquals(0, r.c.releaseIdleAudio())
    }
}
