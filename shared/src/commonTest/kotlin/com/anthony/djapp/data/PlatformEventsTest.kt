package com.anthony.djapp.data

import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.domain.DeckSnapshot
import com.anthony.djapp.domain.Interruption
import com.anthony.djapp.domain.LibraryEntry
import com.anthony.djapp.domain.PlatformEvent
import com.anthony.djapp.domain.SessionSnapshot
import com.anthony.djapp.engine.BeatGrid
import com.anthony.djapp.engine.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "A plataforma detecta, a sessão decide": eventos do [com.anthony.djapp.domain.AudioSystem] e o estado guardado. */
class PlatformEventsTest {
    private val rate = 44100
    private val entry = LibraryEntry("t\nA", "A.mp3", false, 1L)
    private fun track() = Track("A.mp3", rate, ShortArray(rate * 2 * 20), beatGrid = BeatGrid(120.0, 0.0))

    @Test
    fun sessionRestoresWhatTheStoreHasWhenItIsCreated() {
        val saved = SessionSnapshot(
            a = DeckSnapshot(entry, 3.0, 1f, 0f, 0.5f, .5f, .5f, .5f, syncOn = false),
            b = null,
            crossfader = 0.2f,
            master = 0.6f,
            keyLock = false,
        )
        val c = testSession(store = InMemorySessionStore(saved))
        assertEquals(0.2f, c.state.value.crossfader)
        assertFalse(c.state.value.keyLock)
        assertNotNull(c.a.state.pendingRestore)
    }

    @Test
    fun interruptionWhilePlayingPausesSavesAndNotifies() {
        val audio = FakeAudioSystem()
        val store = InMemorySessionStore()
        val c = testSession(audio = audio, store = store)
        c.loadTrack(DeckId.A, track(), entry)
        c.togglePlay(DeckId.A)

        audio.emit(PlatformEvent.Interrupted(Interruption.OutputDisconnected))

        assertFalse(c.a.playingNow)
        assertEquals(1, store.saves)
        assertEquals(entry, store.load()?.a?.source)
        assertEquals("Saída de áudio desconectada: música pausada", c.state.value.notice)
    }

    @Test
    fun interruptionWhileStoppedDoesNothing() {
        val audio = FakeAudioSystem()
        val store = InMemorySessionStore()
        testSession(audio = audio, store = store)
        audio.emit(PlatformEvent.Interrupted(Interruption.OtherAppPlaying))
        assertEquals(0, store.saves)
    }

    @Test
    fun memoryPressureOnlyActsWithTheScreenAway() {
        val audio = FakeAudioSystem()
        val store = InMemorySessionStore()
        val c = testSession(audio = audio, store = store)
        c.loadTrack(DeckId.A, track(), entry)

        audio.emit(PlatformEvent.MemoryPressure) // tela à vista: nada
        assertFalse(c.a.state.audioReleased)
        assertEquals(0, store.saves)

        c.setForeground(false)
        audio.emit(PlatformEvent.MemoryPressure)
        assertTrue(c.a.state.audioReleased, "solta o áudio do deck parado")
        assertEquals(1, store.saves, "guarda antes: o processo pode ser encerrado")
    }

    @Test
    fun explicitPauseAlwaysSaves() {
        val store = InMemorySessionStore()
        val c = testSession(store = store)
        c.interrupt(Interruption.PauseRequested)
        assertEquals(1, store.saves)
        assertNull(c.state.value.notice)
    }

    @Test
    fun deckThatStopsAfterMemoryPressureInBackgroundReleasesItsAudio() {
        val audio = FakeAudioSystem()
        val c = testSession(audio = audio)
        c.loadTrack(DeckId.A, track(), entry)
        c.togglePlay(DeckId.A)
        c.setForeground(false)

        audio.emit(PlatformEvent.MemoryPressure) // tocando: não pode soltar agora
        assertFalse(c.a.state.audioReleased)

        c.interrupt(Interruption.PauseRequested) // "Pausar" da notificação (ou fim da faixa, pelo serviço)
        assertFalse(c.a.playingNow)
        assertTrue(c.a.state.audioReleased, "parou depois do pedido de memória: solta o áudio")
    }

    @Test
    fun withoutMemoryPressureAPauseInBackgroundKeepsTheAudio() {
        val c = testSession()
        c.loadTrack(DeckId.A, track(), entry)
        c.togglePlay(DeckId.A)
        c.setForeground(false)
        c.interrupt(Interruption.PauseRequested)
        assertFalse(c.a.state.audioReleased, "sem pedido de memória, voltar ao app é instantâneo")
    }

    @Test
    fun returningToTheScreenForgetsThePreviousMemoryPressure() {
        val audio = FakeAudioSystem()
        val c = testSession(audio = audio)
        c.loadTrack(DeckId.A, track(), entry)
        c.togglePlay(DeckId.A)
        c.setForeground(false)
        audio.emit(PlatformEvent.MemoryPressure)

        c.setForeground(true) // voltou ao app tocando
        c.setForeground(false) // saiu de novo
        c.interrupt(Interruption.PauseRequested)
        assertFalse(c.a.state.audioReleased, "o pedido antigo não vale para esta saída")
    }
}
