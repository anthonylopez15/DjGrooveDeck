package com.anthony.djapp.data

import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.domain.DeckSnapshot
import com.anthony.djapp.domain.IdlePolicy
import com.anthony.djapp.domain.LibraryEntry
import com.anthony.djapp.domain.PlatformEvent
import com.anthony.djapp.domain.SessionSnapshot
import com.anthony.djapp.engine.BeatGrid
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mais de 30 min sem uso: solta o que está na memória e abre com os decks limpos. */
class IdleCleanupTest {
    private val rate = 44100
    private val min = 60_000L
    private val entry = LibraryEntry("t\nA", "A.mp3", false, 1L)

    private fun track() = Track("A.mp3", rate, ShortArray(rate * 2 * 20), beatGrid = BeatGrid(124.0, 0.1))

    private class Rig(
        val audio: FakeAudioSystem = FakeAudioSystem(),
        val mixer: DjMixer = DjMixer(44100),
        val store: InMemorySessionStore = InMemorySessionStore(),
    ) {
        var now = 1_000_000_000L
        val c = testSession(mixer = mixer, audio = audio, store = store).also { s ->
            s.nowMs = { now }
            s.lastActivityMs = now
        }
    }

    private fun Rig.withTracks() {
        c.loadTrack(DeckId.A, track(), entry)
        c.loadTrack(DeckId.B, track(), entry.copy(id = "t\nB"))
        c.a.changeTempo(5f)
        c.a.changeVolume(0.6f)
    }

    // ---------- regra pura ----------

    @Test
    fun policyIsStaleAfterThirtyMinutes() {
        assertFalse(IdlePolicy.isStale(0L, 30 * min), "exatamente 30 min ainda não")
        assertTrue(IdlePolicy.isStale(0L, 30 * min + 1))
        assertEquals(5 * min, IdlePolicy.remainingMs(0L, 25 * min))
        assertEquals(0L, IdlePolicy.remainingMs(0L, 99 * min))
        assertTrue(IdlePolicy.isStale(0L, 1_000L * min), "estado antigo, sem horário (0): sem uso")
    }

    // ---------- processo novo ----------

    private fun snapshotAt(ms: Long) = SessionSnapshot(
        a = DeckSnapshot(entry, 5.0, 3f, 0f, 0.7f, .5f, .5f, .5f, syncOn = false),
        b = null,
        crossfader = 0.2f,
        master = 0.6f,
        keyLock = false,
        savedAtMs = ms,
    )

    @Test
    fun staleSavedStateOpensWithCleanDecksButKeepsTheMixer() {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val c = testSession(store = InMemorySessionStore(snapshotAt(now - 31 * min)))
        assertNull(c.a.state.pendingRestore, "não reabre a última faixa")
        assertFalse(c.a.state.hasTrack)
        assertEquals(0.2f, c.state.value.crossfader, "o mixer volta como estava")
        assertFalse(c.state.value.keyLock)
        assertNotNull(c.state.value.notice, "avisa que descarregou")
    }

    @Test
    fun recentSavedStateStillRestoresTheDecks() {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val c = testSession(store = InMemorySessionStore(snapshotAt(now - 5 * min)))
        assertNotNull(c.a.state.pendingRestore)
        assertNull(c.state.value.notice)
    }

    @Test
    fun staleEmptyStateGivesNoNotice() {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val empty = snapshotAt(now - 99 * min).copy(a = null)
        assertNull(testSession(store = InMemorySessionStore(empty)).state.value.notice, "não havia o que descarregar")
    }

    // ---------- processo vivo, ao voltar à tela ----------

    @Test
    fun returningAfterMoreThanThirtyMinutesClearsTheDecks() {
        val r = Rig()
        r.withTracks()
        r.c.setForeground(false)
        r.c.save() // onStop: último uso
        r.now += 31 * min

        r.c.setForeground(true)

        assertFalse(r.c.a.state.hasTrack)
        assertFalse(r.c.b.state.hasTrack)
        assertNull(r.mixer.deckA.track, "o motor também soltou a faixa")
        assertEquals(0f, r.c.a.state.tempoPercent)
        assertEquals(0.6f, r.c.a.state.volume, "volume e EQ ficam")
        assertNotNull(r.c.state.value.notice)
    }

    @Test
    fun returningWithinThirtyMinutesKeepsEverything() {
        val r = Rig()
        r.withTracks()
        r.c.setForeground(false)
        r.c.save()
        r.now += 29 * min
        r.c.setForeground(true)
        assertTrue(r.c.a.state.hasTrack)
        assertNull(r.c.state.value.notice)
    }

    @Test
    fun savingWhilePlayingInTheBackgroundCountsAsUse() {
        val r = Rig()
        r.withTracks()
        r.c.togglePlay(DeckId.A)
        r.c.setForeground(false)
        r.now += 40 * min
        r.c.save() // o serviço grava a posição a cada ~1,2 s enquanto toca
        r.now += 2 * min
        r.c.pauseAll()
        r.c.setForeground(true)
        assertTrue(r.c.a.state.hasTrack, "o último uso foi há 2 min")
    }

    @Test
    fun aPlayingDeckIsNeverCleared() {
        val r = Rig()
        r.withTracks()
        r.c.togglePlay(DeckId.A)
        r.c.setForeground(false)
        r.c.save()
        r.now += 90 * min
        r.c.setForeground(true)
        assertTrue(r.c.a.state.hasTrack)
        assertTrue(r.c.a.playingNow)
    }

    // ---------- temporizador em segundo plano ----------

    @Test
    fun leavingTheScreenSchedulesTheTimeoutAndReturningCancelsIt() {
        val r = Rig()
        r.c.setForeground(false)
        assertEquals(IdlePolicy.LIMIT_MS, r.audio.idleTimeoutMs)
        r.c.setForeground(true)
        assertNull(r.audio.idleTimeoutMs)
    }

    @Test
    fun timeoutInTheBackgroundClearsTheDecksAndSavesAnEmptyState() {
        val r = Rig()
        r.withTracks()
        r.c.setForeground(false)
        r.c.save()
        r.now += 31 * min

        r.audio.emit(PlatformEvent.IdleTimeout)

        assertFalse(r.c.a.state.hasTrack)
        assertNull(r.mixer.deckA.track)
        assertNull(r.store.load()?.a, "o estado guardado também ficou sem faixas")
        assertEquals(r.now, r.store.load()?.savedAtMs)
    }

    @Test
    fun timeoutBeforeTheLimitSchedulesAgainForWhatIsLeft() {
        val r = Rig()
        r.withTracks()
        r.c.setForeground(false)
        r.c.save()
        r.now += 10 * min

        r.audio.emit(PlatformEvent.IdleTimeout)

        assertTrue(r.c.a.state.hasTrack)
        assertEquals(20 * min, r.audio.idleTimeoutMs)
    }

    @Test
    fun timeoutWhilePlayingNeverClears() {
        val r = Rig()
        r.withTracks()
        r.c.togglePlay(DeckId.A)
        r.c.setForeground(false)
        r.now += 99 * min
        r.audio.emit(PlatformEvent.IdleTimeout)
        assertTrue(r.c.a.state.hasTrack)
        assertEquals(IdlePolicy.LIMIT_MS, r.audio.idleTimeoutMs, "tocando: confere de novo em 30 min")
    }

    @Test
    fun timeoutWithTheScreenOnDoesNothing() {
        val r = Rig()
        r.withTracks()
        r.now += 99 * min
        r.audio.emit(PlatformEvent.IdleTimeout)
        assertTrue(r.c.a.state.hasTrack)
    }
}
