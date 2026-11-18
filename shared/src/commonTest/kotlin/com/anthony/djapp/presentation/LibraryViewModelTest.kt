package com.anthony.djapp.presentation

import com.anthony.djapp.domain.LibraryEntry
import com.anthony.djapp.domain.TrackRepository
import com.anthony.djapp.engine.Track
import com.anthony.djapp.engine.TrackInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lista de arquivos: navegação, listagem, análise de BPM e pastas vinculadas. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun folder(name: String) = LibraryEntry("f:$name", name, isFolder = true)
    private fun file(name: String) = LibraryEntry("a:$name", name, isFolder = false)

    private val bachata = folder("Bachata")
    private val merengue = folder("Merengue")
    private val sub = folder("Sub")
    private val song1 = file("1.mp3")
    private val song2 = file("2.mp3")
    private val broken = file("ruim.mp3")

    /** Repositório falso: conteúdo por pasta, cache e análise controlados pelo teste. */
    private class FakeRepo(
        val contents: MutableMap<String, List<LibraryEntry>>,
        val cached: Map<String, TrackInfo> = emptyMap(),
        val analysis: Map<String, TrackInfo> = emptyMap(),
    ) : TrackRepository {
        override val folders = MutableStateFlow<List<LibraryEntry>>(emptyList())
        var addRequests = 0
        val analyzed = mutableListOf<String>()

        /** Se definido, a listagem espera por ele (para ver o estado "carregando"). */
        var listGate: CompletableDeferred<Unit>? = null

        override fun requestAddFolder() {
            addRequests++
        }

        override fun removeFolder(folder: LibraryEntry) {
            folders.value = folders.value - folder
        }

        override suspend fun list(folder: LibraryEntry): List<LibraryEntry> {
            listGate?.await()
            listCalls++
            return contents[folder.id] ?: error("sem acesso")
        }

        /** Avisos de mudança na pasta (o teste emite como se outro app tivesse mexido nela). */
        val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        var listCalls = 0

        override fun changes(folder: LibraryEntry): Flow<Unit> = changed

        override fun cachedInfo(file: LibraryEntry): TrackInfo? = cached[file.id]

        override suspend fun analyze(file: LibraryEntry): TrackInfo? {
            analyzed += file.name
            return analysis[file.id] ?: error("não analisa")
        }

        override suspend fun decode(file: LibraryEntry, onProgress: (Float) -> Unit): Track = error("não usado")
    }

    private fun repo(
        cached: Map<String, TrackInfo> = emptyMap(),
        analysis: Map<String, TrackInfo> = emptyMap(),
    ) = FakeRepo(
        contents = mutableMapOf(
            bachata.id to listOf(sub, song1, song2, broken),
            sub.id to listOf(song2),
            merengue.id to emptyList(),
        ),
        cached = cached,
        analysis = analysis,
    ).apply { folders.value = listOf(bachata, merengue) }

    @Test
    fun rootShowsLinkedFolders() = runTest(dispatcher) {
        val vm = LibraryViewModel(repo())
        val s = vm.uiState.value
        assertTrue(s.atRoot)
        assertEquals(listOf(bachata, merengue), s.visible)
        assertEquals(0, s.pendingAnalysis)
    }

    @Test
    fun openingAFolderListsItAndShowsLoadingMeanwhile() = runTest(dispatcher) {
        val r = repo(analysis = mapOf(song1.id to TrackInfo(200.0, 124.0), song2.id to TrackInfo(180.0, null)))
        r.listGate = CompletableDeferred()
        val vm = LibraryViewModel(r)

        vm.onAction(LibraryAction.Open(bachata))
        assertEquals(bachata, vm.uiState.value.current)
        assertTrue(vm.uiState.value.loading)

        r.listGate!!.complete(Unit)
        val s = vm.uiState.value
        assertFalse(s.loading)
        assertEquals(listOf(sub, song1, song2, broken), s.visible)
        assertEquals(124.0, s.infos[song1.id]?.bpm)
        assertNull(s.infos[song2.id]?.bpm, "sem ritmo claro")
        assertEquals(TrackInfo(0.0, null), s.infos[broken.id], "falha na análise: mostra sem BPM")
        assertEquals(0, s.pendingAnalysis)
    }

    @Test
    fun newFileAddedByAnotherAppShowsUpWithoutReloadingTheFolder() = runTest(dispatcher) {
        val r = repo(analysis = mapOf(song1.id to TrackInfo(200.0, 124.0), song2.id to TrackInfo(180.0, 128.0)))
        r.contents[bachata.id] = listOf(song1)
        val vm = LibraryViewModel(r)
        vm.onAction(LibraryAction.Open(bachata))
        assertEquals(listOf(song1), vm.uiState.value.visible)
        r.analyzed.clear()

        r.contents[bachata.id] = listOf(song1, song2) // o gerenciador de arquivos copiou 2.mp3
        r.changed.emit(Unit)
        advanceTimeBy(600)

        val s = vm.uiState.value
        assertEquals(listOf(song1, song2), s.visible)
        assertFalse(s.loading, "sem tela de carregamento")
        assertEquals(listOf("2.mp3"), r.analyzed, "só a faixa nova é analisada")
        assertEquals(128.0, s.infos[song2.id]?.bpm)
        assertEquals(124.0, s.infos[song1.id]?.bpm, "o que já estava analisado continua")
    }

    @Test
    fun fileRemovedByAnotherAppDisappears() = runTest(dispatcher) {
        val r = repo(analysis = mapOf(song1.id to TrackInfo(200.0, 124.0), song2.id to TrackInfo(180.0, 128.0)))
        r.contents[bachata.id] = listOf(song1, song2)
        val vm = LibraryViewModel(r)
        vm.onAction(LibraryAction.Open(bachata))

        r.contents[bachata.id] = listOf(song2)
        r.changed.emit(Unit)
        advanceTimeBy(600)

        assertEquals(listOf(song2), vm.uiState.value.visible)
    }

    @Test
    fun burstOfChangesIsListedOnce() = runTest(dispatcher) {
        val r = repo()
        r.contents[bachata.id] = listOf(song1)
        val vm = LibraryViewModel(r)
        vm.onAction(LibraryAction.Open(bachata))
        val before = r.listCalls

        repeat(5) { r.changed.emit(Unit) }
        advanceTimeBy(600)

        assertEquals(before + 1, r.listCalls)
    }

    @Test
    fun noWatchingAfterLeavingTheFolder() = runTest(dispatcher) {
        val r = repo()
        val vm = LibraryViewModel(r)
        vm.onAction(LibraryAction.Open(bachata))
        vm.onAction(LibraryAction.Back)
        val before = r.listCalls

        r.changed.emit(Unit)
        advanceTimeBy(600)

        assertEquals(before, r.listCalls)
    }

    @Test
    fun refreshKeepsTheListWhenListingFails() = runTest(dispatcher) {
        val r = repo()
        val vm = LibraryViewModel(r)
        vm.onAction(LibraryAction.Open(bachata))
        val shown = vm.uiState.value.visible

        r.contents.remove(bachata.id) // a listagem passa a falhar (cartão SD fora, permissão perdida...)
        vm.onAction(LibraryAction.Refresh)

        assertEquals(shown, vm.uiState.value.visible)
    }

    @Test
    fun cachedInfoIsUsedWithoutAnalyzingAgain() = runTest(dispatcher) {
        val r = repo(
            cached = mapOf(song1.id to TrackInfo(200.0, 124.0)),
            analysis = mapOf(song2.id to TrackInfo(180.0, 128.0), broken.id to TrackInfo(10.0, null)),
        )
        val vm = LibraryViewModel(r)
        vm.onAction(LibraryAction.Open(bachata))
        assertEquals(listOf("2.mp3", "ruim.mp3"), r.analyzed, "só o que não estava no cache")
        assertEquals(124.0, vm.uiState.value.infos[song1.id]?.bpm)
    }

    @Test
    fun navigatingIntoAndBack() = runTest(dispatcher) {
        val vm = LibraryViewModel(repo())
        vm.onAction(LibraryAction.Open(bachata))
        vm.onAction(LibraryAction.Open(sub))
        assertEquals(listOf(bachata, sub), vm.uiState.value.path)
        assertEquals(listOf(song2), vm.uiState.value.visible)

        vm.onAction(LibraryAction.Back)
        assertEquals(bachata, vm.uiState.value.current)
        assertEquals(4, vm.uiState.value.visible.size)

        vm.onAction(LibraryAction.Back)
        assertTrue(vm.uiState.value.atRoot)
        assertEquals(listOf(bachata, merengue), vm.uiState.value.visible)
    }

    @Test
    fun openingAFileDoesNothing() = runTest(dispatcher) {
        val vm = LibraryViewModel(repo())
        vm.onAction(LibraryAction.Open(song1))
        assertTrue(vm.uiState.value.atRoot, "faixa não é pasta: escolher faixa é do DjViewModel")
    }

    @Test
    fun inaccessibleFolderShowsEmpty() = runTest(dispatcher) {
        val vm = LibraryViewModel(repo())
        vm.onAction(LibraryAction.Open(folder("Sumiu")))
        assertFalse(vm.uiState.value.loading)
        assertTrue(vm.uiState.value.visible.isEmpty())
    }

    @Test
    fun leavingAFolderBeforeItListsDoesNotShowItsContents() = runTest(dispatcher) {
        val r = repo()
        r.listGate = CompletableDeferred()
        val vm = LibraryViewModel(r)
        vm.onAction(LibraryAction.Open(bachata)) // fica esperando
        r.listGate = null
        vm.onAction(LibraryAction.Back)
        vm.onAction(LibraryAction.Open(merengue)) // lista na hora
        assertEquals(merengue, vm.uiState.value.current)
        assertTrue(vm.uiState.value.visible.isEmpty(), "o conteúdo do Bachata não pode aparecer no Merengue")
    }

    @Test
    fun addAndRemoveLinkedFolders() = runTest(dispatcher) {
        val r = repo()
        val vm = LibraryViewModel(r)
        vm.onAction(LibraryAction.AddFolder)
        assertEquals(1, r.addRequests)
        vm.onAction(LibraryAction.RemoveFolder(merengue))
        assertEquals(listOf(bachata), vm.uiState.value.folders)
    }
}
