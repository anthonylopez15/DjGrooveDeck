package com.anthony.djapp.data

import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.domain.DeckSnapshot
import com.anthony.djapp.domain.LibraryEntry
import com.anthony.djapp.domain.SessionSnapshot
import com.anthony.djapp.engine.BeatGrid
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.Track
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Guardar o estado da sessão em texto e voltar a ele num processo novo. */
class SessionRestoreTest {
    private val rate = 44100
    private val entryA = LibraryEntry("content://tree/x\\y\nprimary:Música/A=1.mp3", "A=1.mp3", false, 77L)
    private val entryB = LibraryEntry("content://tree/x\nprimary:B.mp3", "B.mp3", false, 5L)

    private fun track(name: String, bpm: Double) =
        Track(name, rate, ShortArray(rate * 2 * 40), beatGrid = BeatGrid(bpm, 0.2))

    private fun settle(mixer: DjMixer, c: MixSession) {
        mixer.render(FloatArray(2 * 512), 512)
        c.syncFromEngine()
    }

    @Test
    fun encodeDecodeRoundTripKeepsEverything() {
        val s = SessionSnapshot(
            a = DeckSnapshot(entryA, 12.5, 3.2f, -1f, 0.66f, 0.1f, 0.5f, 0.9f, syncOn = true),
            b = null,
            crossfader = 0.3f,
            master = 0.7f,
            keyLock = false,
            savedAtMs = 1_763_000_000_123L,
        )
        assertEquals(s, SessionSnapshotCodec.decode(SessionSnapshotCodec.encode(s)))
        // estado de versões antigas não tem horário: vale 0 (sem uso)
        assertEquals(0L, SessionSnapshotCodec.decode("v=1\ncrossfader=0.5")?.savedAtMs)
    }

    @Test
    fun corruptOrMissingStateIsIgnored() {
        assertNull(SessionSnapshotCodec.decode(null))
        assertNull(SessionSnapshotCodec.decode(""))
        assertNull(SessionSnapshotCodec.decode("lixo sem formato"))
        assertNull(SessionSnapshotCodec.decode("v=999\ncrossfader=0.1"))
        // valores fora da faixa são limitados, não quebram
        val d = assertNotNull(SessionSnapshotCodec.decode("v=1\ncrossfader=7\na.id=x\na.name=n\na.tempo=90\na.volume=NaN"))
        assertEquals(1f, d.crossfader)
        assertEquals(20f, d.a!!.tempoPercent)
        assertEquals(1f, d.a.volume)
    }

    @Test
    fun controllerStateSurvivesANewProcess() {
        // "processo 1": duas faixas, ajustes, SYNC e posição
        val m1 = DjMixer(rate)
        val c1 = testSession(m1)
        c1.nowMs = { 1_000L } // o texto salvo traz o horário: relógio fixo nas duas sessões
        assertTrue(c1.loadTrack(DeckId.A, track("A", 124.0), entryA))
        assertTrue(c1.loadTrack(DeckId.B, track("B", 128.0), entryB))
        c1.a.changeTempo(2.5f)
        c1.a.changeVolume(0.6f)
        c1.a.changeLow(0.2f)
        c1.toggleSync(DeckId.B)
        c1.a.seek(0.5f)
        c1.changeCrossfader(0.25f)
        c1.changeMaster(0.6f)
        c1.toggleKeyLock()
        settle(m1, c1)
        val saved = SessionSnapshotCodec.encode(c1.snapshot())

        // "processo 2": sessão nova lendo o texto guardado
        val m2 = DjMixer(rate)
        val c2 = testSession(m2)
        c2.nowMs = { 1_000L }
        c2.restore(assertNotNull(SessionSnapshotCodec.decode(saved)))
        assertEquals(0.25f, c2.state.value.crossfader)
        assertEquals(0.6f, c2.state.value.master)
        assertFalse(c2.state.value.keyLock)
        val pa = assertNotNull(c2.a.state.pendingRestore)
        val pb = assertNotNull(c2.b.state.pendingRestore)
        assertFalse(c2.a.state.hasTrack, "a faixa só volta depois de recarregar do arquivo")
        // antes de recarregar, guardar de novo não perde nada
        assertEquals(saved, SessionSnapshotCodec.encode(c2.snapshot()))

        // a tela recarrega os arquivos
        assertTrue(c2.a.applyRestore(track("A", 124.0), pa))
        assertTrue(c2.b.applyRestore(track("B", 128.0), pb))
        settle(m2, c2)
        assertNull(c2.a.state.pendingRestore)
        assertEquals(entryA, c2.a.state.source)
        assertEquals(2.5f, c2.a.state.tempoPercent)
        assertEquals(0.6f, c2.a.state.volume)
        assertEquals(0.2f, c2.a.state.low)
        assertTrue(c2.b.state.syncOn)
        assertFalse(c2.a.state.playing, "volta pausado")
        assertTrue(abs(c2.a.positionSec - c1.a.positionSec) < 0.01, "posição ${c2.a.positionSec} vs ${c1.a.positionSec}")
        assertFalse(m2.keyLock)
        assertFalse(m2.deckA.keyLock)

        // desligar o SYNC restaurado devolve o tempo de antes de sincronizar
        c2.toggleSync(DeckId.B)
        assertEquals(0f, c2.b.state.tempoPercent)
    }

    @Test
    fun loadingAnotherTrackCancelsPendingRestore() {
        val c = testSession(DjMixer(rate))
        c.restore(SessionSnapshot(DeckSnapshot(entryA, 5.0, 0f, 0f, 1f, .5f, .5f, .5f, false), null, .5f, .8f, true))
        val pending = assertNotNull(c.a.state.pendingRestore)
        assertTrue(c.loadTrack(DeckId.A, track("Outra", 120.0), entryB))
        assertNull(c.a.state.pendingRestore)
        assertFalse(c.a.applyRestore(track("A", 124.0), pending), "recarga atrasada não sobrescreve a escolha do usuário")
        assertEquals(entryB, c.a.state.source)
    }

    @Test
    fun trackWithoutSourceIsNotSaved() {
        val c = testSession(DjMixer(rate))
        c.loadTrack(DeckId.A, track("A", 124.0))
        assertNull(c.snapshot().a)
    }
}
