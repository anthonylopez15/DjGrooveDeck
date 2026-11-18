package com.anthony.djapp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anthony.djapp.data.DeckSession
import com.anthony.djapp.data.MixSession
import com.anthony.djapp.data.MixState
import com.anthony.djapp.data.applyRestore
import com.anthony.djapp.data.dropRestore
import com.anthony.djapp.data.restoreAudio
import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.domain.LibraryEntry
import com.anthony.djapp.domain.TrackRepository
import com.anthony.djapp.engine.TrackRejectedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel da tela principal (ADR 0001): expõe [uiState] imutável e recebe [DjAction] em [onAction].
 *
 * Vive com a tela; a [MixSession] vive com o processo e continua tocando sem ele. Aqui ficam o estado
 * que é só da tela (aba, biblioteca, avisos), os carregamentos de arquivo e os medidores por quadro.
 */
class DjViewModel(
    private val session: MixSession,
    private val library: TrackRepository,
) : ViewModel() {
    private val screen = MutableStateFlow(ScreenState())

    val uiState: StateFlow<DjUiState> =
        combine(session.state, screen, ::uiStateOf)
            .stateIn(viewModelScope, SharingStarted.Eagerly, uiStateOf(session.state.value, screen.value))

    /** Posição e nível a cada quadro (fora do [uiState]; ver [DeckMeters]). */
    val metersA = DeckMeters()
    val metersB = DeckMeters()

    fun meters(id: DeckId): DeckMeters = if (id == DeckId.A) metersA else metersB

    private var noticeJob: Job? = null
    private val reloadJobs = mutableMapOf<DeckId, Job>()

    /** Última recarga tentada por deck, para não repetir sem parar uma que falhou. */
    private val reloadAttempted = mutableMapOf<DeckId, Any>()

    init {
        // avisos que vêm da sessão (fone desconectado, outro app...) entram no aviso do topo
        viewModelScope.launch {
            session.state.map { it.notice }.distinctUntilChanged().filterNotNull().collect {
                showNotice(it)
                session.consumeNotice()
            }
        }
        viewModelScope.launch { session.state.collect(::reloadFromFilesIfNeeded) }
    }

    /** Chamado a cada quadro de tela: medidores e o que o motor mudou sozinho. */
    fun onFrame() {
        session.syncFromEngine()
        metersA.update(session.a.positionSec, session.a.level)
        metersB.update(session.b.positionSec, session.b.level)
    }

    fun onAction(action: DjAction) {
        when (action) {
            is DjAction.TogglePlay -> session.togglePlay(action.deck)
            is DjAction.Restart -> session.restart(action.deck)
            is DjAction.ToggleSync -> session.toggleSync(action.deck)
            is DjAction.Seek -> session.deck(action.deck).seek(action.fraction)
            is DjAction.Scrub -> session.deck(action.deck).scrubBySeconds(action.deltaSec)
            is DjAction.ChangeVolume -> session.deck(action.deck).changeVolume(action.value)
            is DjAction.ChangeEq -> changeEq(session.deck(action.deck), action.band, action.value)
            is DjAction.SetTempo -> session.setTempo(action.deck, action.percent)
            is DjAction.ChangeCrossfader -> session.changeCrossfader(action.value)
            is DjAction.ChangeMaster -> session.changeMaster(action.value)
            DjAction.ToggleKeyLock -> session.toggleKeyLock()
            DjAction.ToggleAutoFade -> session.toggleAutoFade()
            is DjAction.SelectTab -> screen.update { it.copy(tab = action.tab) }
            is DjAction.OpenLibrary -> openLibrary(action.deck)
            DjAction.CloseLibrary -> screen.update { it.copy(browsing = false, libraryStatus = null) }
            is DjAction.PickTrack -> loadFromLibrary(action.entry)
        }
    }

    private fun changeEq(deck: DeckSession, band: EqBand, value: Float) = when (band) {
        EqBand.Low -> deck.changeLow(value)
        EqBand.Mid -> deck.changeMid(value)
        EqBand.High -> deck.changeHigh(value)
    }

    // ---------- biblioteca ----------

    private fun openLibrary(id: DeckId) {
        if (!session.canReplaceTrack(id)) {
            showNotice(mustPauseMessage(id))
            return
        }
        screen.update { it.copy(browsing = true, target = id) }
    }

    /** Carrega [entry] no deck de destino (regra: só com o deck parado). */
    // decodificar pode lançar qualquer coisa (codec, arquivo, permissão): vira mensagem para o usuário
    @Suppress("TooGenericExceptionCaught")
    private fun loadFromLibrary(entry: LibraryEntry) {
        val id = screen.value.target
        val deck = session.deck(id)
        if (!session.canReplaceTrack(id)) {
            setLibraryStatus(mustPauseMessage(id))
            return
        }
        if (deck.state.loading) return
        setLibraryStatus(null)
        deck.startLoading(entry.name)
        screen.update { it.copy(loadingEntries = it.loadingEntries + (entry.id to id)) }
        viewModelScope.launch {
            try {
                val loaded = session.loadTrack(id, library.decode(entry, deck::updateLoadProgress), entry)
                if (!loaded) { // o deck começou a tocar durante a decodificação
                    deck.cancelLoading()
                    setLibraryStatus(mustPauseMessage(id))
                }
            } catch (e: CancellationException) {
                deck.cancelLoading()
                throw e
            } catch (e: TrackRejectedException) {
                deck.cancelLoading()
                setLibraryStatus(e.message) // recusa de propósito (longa demais, sem memória): mensagem pronta
            } catch (e: Exception) {
                deck.cancelLoading()
                setLibraryStatus("Não consegui abrir: ${e.message}")
            } finally {
                screen.update { it.copy(loadingEntries = it.loadingEntries - entry.id) }
            }
        }
    }

    // ---------- recarga do arquivo ----------

    /**
     * Com a tela à vista, recarrega do arquivo: o áudio solto por falta de memória em segundo plano, ou a
     * faixa que estava no deck quando o sistema encerrou o processo (volta pausada, na mesma posição).
     * Sair de cena cancela a recarga; ela é tentada de novo ao voltar.
     */
    private fun reloadFromFilesIfNeeded(mix: MixState) {
        for (id in DeckId.entries) reloadDeckIfNeeded(id, mix)
    }

    // decodificar pode lançar qualquer coisa (codec, arquivo, permissão): vira mensagem para o usuário
    @Suppress("TooGenericExceptionCaught")
    private fun reloadDeckIfNeeded(id: DeckId, mix: MixState) {
        if (!mix.foreground) {
            reloadJobs.remove(id)?.cancel()
            reloadAttempted.remove(id)
            return
        }
        val d = mix.deck(id)
        if (reloadJobs[id]?.isActive == true || d.loading) return
        val pending = d.pendingRestore
        val entry = pending?.source ?: d.source?.takeIf { d.audioReleased } ?: return
        val key: Any = pending ?: entry
        if (reloadAttempted[id] == key) return // já tentou (e falhou); espera mudar algo
        reloadAttempted[id] = key
        val deck = session.deck(id)
        deck.startLoading(entry.name)
        reloadJobs[id] = viewModelScope.launch {
            try {
                val track = library.decode(entry, deck::updateLoadProgress)
                if (pending != null) deck.applyRestore(track, pending) else deck.restoreAudio(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (pending != null) deck.dropRestore()
                showNotice("Não consegui recarregar ${entry.name}: ${e.message}")
            } finally {
                if (deck.state.loadingName == entry.name) deck.cancelLoading()
            }
        }
    }

    // ---------- avisos ----------

    /** Aviso curto no topo; some sozinho depois de [NOTICE_MS]. */
    private fun showNotice(message: String) {
        screen.update { it.copy(notice = message) }
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            delay(NOTICE_MS)
            screen.update { if (it.notice == message) it.copy(notice = null) else it }
        }
    }

    private fun setLibraryStatus(message: String?) = screen.update { it.copy(libraryStatus = message) }

    private companion object {
        const val NOTICE_MS = 2500L

        fun mustPauseMessage(id: DeckId) = "Pause o deck ${id.name} para trocar a faixa"
    }
}
