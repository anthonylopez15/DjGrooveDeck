package com.anthony.djapp.presentation

import com.anthony.djapp.data.MixSession
import com.anthony.djapp.data.releaseIdleAudio
import com.anthony.djapp.data.restore
import com.anthony.djapp.data.testSession
import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.domain.DeckSnapshot
import com.anthony.djapp.domain.Interruption
import com.anthony.djapp.domain.LibraryEntry
import com.anthony.djapp.domain.SessionSnapshot
import com.anthony.djapp.domain.TrackRepository
import com.anthony.djapp.engine.BeatGrid
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.Track
import com.anthony.djapp.engine.TrackInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ViewModel da tela principal: ações viram estado, biblioteca, avisos e recarga do arquivo. */
@OptIn(ExperimentalCoroutinesApi::class)
class DjViewModelTest {
    private val rate = 44100
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun track(name: String, bpm: Double = 124.0) =
        Track(name, rate, ShortArray(rate * 2 * 20), beatGrid = BeatGrid(bpm, 0.1))

    private val entryA = LibraryEntry("t\nA", "A.mp3", false, 1L)

    /** Biblioteca falsa: devolve faixas prontas (ou falha) e conta as decodificações. */
    private class FakeLibrary(private val decodeWith: suspend (LibraryEntry) -> Track) : TrackRepository {
        var decodes = 0
        override val folders: StateFlow<List<LibraryEntry>> = MutableStateFlow(emptyList())
        override fun requestAddFolder() = Unit
        override fun removeFolder(folder: LibraryEntry) = Unit
        override suspend fun list(folder: LibraryEntry): List<LibraryEntry> = emptyList()
        override fun cachedInfo(file: LibraryEntry): TrackInfo? = null
        override suspend fun analyze(file: LibraryEntry): TrackInfo? = null
        override suspend fun decode(file: LibraryEntry, onProgress: (Float) -> Unit): Track {
            decodes++
            onProgress(0.5f)
            return decodeWith(file)
        }
    }

    private class Rig(val session: MixSession, val library: FakeLibrary, val vm: DjViewModel) {
        val state get() = vm.uiState.value
    }

    private fun rig(
        library: FakeLibrary = FakeLibrary { Track(it.name, 44100, ShortArray(44100 * 2 * 20), beatGrid = BeatGrid(124.0, 0.1)) },
        prepare: (MixSession) -> Unit = {},
    ): Rig {
        val session = testSession(DjMixer(rate))
        prepare(session)
        return Rig(session, library, DjViewModel(session, library))
    }

    @Test
    fun actionsBecomeState() = runTest(dispatcher) {
        val r = rig()
        r.session.loadTrack(DeckId.A, track("A"))
        r.vm.onAction(DjAction.ChangeVolume(DeckId.A, 0.4f))
        r.vm.onAction(DjAction.ChangeEq(DeckId.A, EqBand.Low, 0.1f))
        r.vm.onAction(DjAction.SetTempo(DeckId.A, 3.5f))
        r.vm.onAction(DjAction.ChangeCrossfader(0.25f))
        r.vm.onAction(DjAction.ToggleKeyLock)
        r.vm.onAction(DjAction.SelectTab(Tab.Mixer))
        r.vm.onAction(DjAction.TogglePlay(DeckId.A))
        r.vm.onFrame()

        val s = r.state
        assertEquals("A", s.a.title)
        assertEquals(0.4f, s.a.volume)
        assertEquals(0.1f, s.a.low)
        assertEquals(3.5f, s.a.tempoPercent)
        assertEquals(0.25f, s.crossfader)
        assertFalse(s.keyLock)
        assertEquals(Tab.Mixer, s.tab)
        assertTrue(s.a.playing)
    }

    @Test
    fun libraryOpensOnlyForAPausedDeck() = runTest(dispatcher) {
        val r = rig()
        r.session.loadTrack(DeckId.A, track("A"))
        r.vm.onAction(DjAction.TogglePlay(DeckId.A))

        r.vm.onAction(DjAction.OpenLibrary(DeckId.A))
        assertFalse(r.state.browsing)
        assertEquals("Pause o deck A para trocar a faixa", r.state.notice)

        r.vm.onAction(DjAction.OpenLibrary(DeckId.B))
        assertTrue(r.state.browsing)
        assertEquals(DeckId.B, r.state.target)

        r.vm.onAction(DjAction.CloseLibrary)
        assertFalse(r.state.browsing)
    }

    @Test
    fun pickingATrackLoadsItIntoTheTargetDeck() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val r = rig(
            FakeLibrary {
                gate.await()
                track(it.name)
            },
        )
        r.vm.onAction(DjAction.OpenLibrary(DeckId.B))
        r.vm.onAction(DjAction.PickTrack(entryA))

        // decodificando: deck e linha da lista marcados
        assertEquals("A.mp3", r.state.b.loadingName)
        assertEquals(0.5f, r.state.b.loadProgress)
        assertEquals(DeckId.B, r.state.loadingEntries[entryA.id])

        gate.complete(Unit)
        assertNull(r.state.b.loadingName)
        assertTrue(r.state.loadingEntries.isEmpty())
        assertEquals("A.mp3", r.state.b.title)
        assertEquals(entryA, r.session.b.state.source)
    }

    @Test
    fun failedLoadShowsStatusInTheList() = runTest(dispatcher) {
        val r = rig(FakeLibrary { error("arquivo corrompido") })
        r.vm.onAction(DjAction.OpenLibrary(DeckId.A))
        r.vm.onAction(DjAction.PickTrack(entryA))
        assertEquals("Não consegui abrir: arquivo corrompido", r.state.libraryStatus)
        assertNull(r.state.a.loadingName)
        assertFalse(r.state.a.hasTrack)
    }

    @Test
    fun sessionNoticesShowAndDisappear() = runTest(dispatcher) {
        val r = rig()
        r.session.loadTrack(DeckId.A, track("A"))
        r.vm.onAction(DjAction.TogglePlay(DeckId.A))
        r.session.onInterruption(Interruption.OutputDisconnected)

        assertEquals("Saída de áudio desconectada: música pausada", r.state.notice)
        assertNull(r.session.state.value.notice, "a sessão entrega o aviso uma vez só")
        r.vm.onFrame()
        assertFalse(r.state.a.playing)

        advanceTimeBy(2600)
        assertNull(r.state.notice)
    }

    private fun snapshotOf(entry: LibraryEntry) = SessionSnapshot(
        a = DeckSnapshot(
            entry,
            positionSec = 5.0,
            tempoPercent = 2f,
            tempoBeforeSync = 0f,
            volume = 0.7f,
            low = .5f,
            mid = .5f,
            high = .5f,
            syncOn = true,
        ),
        b = null,
        crossfader = 0.5f,
        master = 0.8f,
        keyLock = true,
    )

    @Test
    fun restoredDeckIsReloadedWhenTheScreenIsVisible() = runTest(dispatcher) {
        val r = rig { s ->
            s.setForeground(false) // processo novo, tela ainda fora de cena
            s.restore(snapshotOf(entryA))
        }
        assertEquals(0, r.library.decodes, "só recarrega com a tela à vista")

        r.session.setForeground(true)
        assertEquals(1, r.library.decodes)
        val a = r.session.a.state
        assertNull(a.pendingRestore)
        assertEquals("A.mp3", a.title)
        assertEquals(2f, a.tempoPercent)
        assertEquals(0.7f, a.volume)
        assertTrue(a.syncOn)
        assertEquals(5.0, r.session.a.positionSec, 0.01)
        assertFalse(r.session.a.playingNow, "volta pausado")
    }

    @Test
    fun failedReloadIsNotRetriedInALoop() = runTest(dispatcher) {
        val r = rig(FakeLibrary { error("arquivo sumiu") }) { s -> s.restore(snapshotOf(entryA)) }
        assertEquals(1, r.library.decodes)
        assertNull(r.session.a.state.pendingRestore, "desiste: o deck fica vazio")
        assertNotNull(r.state.notice)
        // outras mudanças de estado não disparam nova tentativa
        r.vm.onAction(DjAction.ChangeCrossfader(0.3f))
        r.vm.onAction(DjAction.ChangeMaster(0.5f))
        assertEquals(1, r.library.decodes)
    }

    @Test
    fun releasedAudioIsReloadedOnReturnAndRetriedAfterLeaving() = runTest(dispatcher) {
        var fail = true
        val r = rig(FakeLibrary { if (fail) error("sem acesso") else track(it.name) })
        r.session.loadTrack(DeckId.A, track("A.mp3"), entryA)
        r.session.setForeground(false)
        assertEquals(1, r.session.releaseIdleAudio())

        r.session.setForeground(true) // volta: tenta recarregar e falha
        assertEquals(1, r.library.decodes)
        assertTrue(r.session.a.state.audioReleased)
        r.vm.onAction(DjAction.ChangeMaster(0.5f))
        assertEquals(1, r.library.decodes, "não insiste sem algo mudar")

        fail = false
        r.session.setForeground(false) // sai e volta: tenta de novo
        r.session.setForeground(true)
        assertEquals(2, r.library.decodes)
        assertFalse(r.session.a.state.audioReleased)
    }
}
