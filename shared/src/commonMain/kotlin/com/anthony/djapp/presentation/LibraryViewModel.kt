package com.anthony.djapp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anthony.djapp.domain.LibraryEntry
import com.anthony.djapp.domain.TrackRepository
import com.anthony.djapp.engine.TrackInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** O que a lista de arquivos mostra (imutável). */
data class LibraryUiState(
    /** Pastas vinculadas pelo usuário (a raiz da lista). */
    val folders: List<LibraryEntry> = emptyList(),
    /** Pastas abertas, da raiz até a atual; vazio = raiz. */
    val path: List<LibraryEntry> = emptyList(),
    /** Conteúdo da pasta atual (subpastas e áudios). */
    val entries: List<LibraryEntry> = emptyList(),
    /** Listando a pasta atual. */
    val loading: Boolean = false,
    /** BPM e duração já conhecidos, por id de arquivo (do cache ou da análise). */
    val infos: Map<String, TrackInfo> = emptyMap(),
) {
    val current: LibraryEntry? get() = path.lastOrNull()
    val atRoot: Boolean get() = current == null

    /** O que listar: as pastas vinculadas na raiz; o conteúdo da pasta aberta fora dela. */
    val visible: List<LibraryEntry> get() = if (atRoot) folders else entries

    /** Arquivos da pasta atual ainda sem BPM/duração. */
    val pendingAnalysis: Int
        get() = if (atRoot || loading) 0 else entries.count { !it.isFolder && it.id !in infos }
}

/** O que a lista de arquivos pode pedir. Escolher uma música é do [DjViewModel] ([DjAction.PickTrack]). */
sealed interface LibraryAction {
    /** Abre uma pasta. */
    data class Open(val folder: LibraryEntry) : LibraryAction

    /** Volta uma pasta. */
    data object Back : LibraryAction

    /** Vincula uma pasta nova (abre o seletor da plataforma). */
    data object AddFolder : LibraryAction

    /** Lista de novo a pasta aberta, sem tela de carregamento (ex.: ao abrir a biblioteca). */
    data object Refresh : LibraryAction

    /** Desvincula uma pasta da raiz. */
    data class RemoveFolder(val folder: LibraryEntry) : LibraryAction
}

/**
 * ViewModel da lista de arquivos (ADR 0001): navega pelas pastas e analisa BPM e duração em segundo
 * plano, uma faixa por vez (o que já foi analisado vem do cache do repositório). A pasta aberta fica
 * lembrada enquanto a tela existe; a análise da pasta atual segue até o fim mesmo com a lista fechada
 * (o resultado vai para o cache).
 */
class LibraryViewModel(private val repo: TrackRepository) : ViewModel() {
    private val local = MutableStateFlow(LibraryUiState())

    val uiState: StateFlow<LibraryUiState> =
        combine(local, repo.folders) { s, folders -> s.copy(folders = folders) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, local.value.copy(folders = repo.folders.value))

    private var listJob: Job? = null
    private var analysisJob: Job? = null
    private var watchJob: Job? = null

    fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.Open -> if (action.folder.isFolder) navigate(local.value.path + action.folder)
            LibraryAction.Back -> navigate(local.value.path.dropLast(1))
            LibraryAction.AddFolder -> repo.requestAddFolder()
            LibraryAction.Refresh -> refresh()
            is LibraryAction.RemoveFolder -> repo.removeFolder(action.folder)
        }
    }

    private fun navigate(path: List<LibraryEntry>) {
        listJob?.cancel()
        watchJob?.cancel()
        val folder = path.lastOrNull()
        local.update { it.copy(path = path, loading = folder != null) }
        if (folder == null) return
        listJob = viewModelScope.launch {
            val list = try {
                repo.list(folder)
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) { // pasta inacessível: lista vazia
                emptyList()
            }
            local.update { it.copy(entries = list, loading = false) }
            analyze(list)
        }
        watch(folder)
    }

    /** Enquanto a pasta está aberta, lista de novo quando outro app mexe nela (arquivos novos ou apagados). */
    @OptIn(FlowPreview::class)
    private fun watch(folder: LibraryEntry) {
        watchJob = viewModelScope.launch {
            repo.changes(folder).debounce(CHANGE_DEBOUNCE_MS).collect { refresh() }
        }
    }

    /**
     * Atualiza a lista da pasta aberta sem recarregar do zero: não mostra "Carregando", mantém o que já foi
     * analisado e só analisa o que é novo. Se a listagem falha, mantém a lista que está na tela.
     */
    private fun refresh() {
        val folder = local.value.current ?: return
        if (local.value.loading) return // a listagem inicial ainda está em andamento
        listJob?.cancel()
        listJob = viewModelScope.launch {
            val list = try {
                repo.list(folder)
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) { // sem acesso agora: segue com o que está na tela
                return@launch
            }
            if (local.value.current != folder) return@launch // o usuário navegou enquanto listava
            if (list == local.value.entries) return@launch
            local.update { it.copy(entries = list) }
            analyze(list)
        }
    }

    /** BPM/duração da pasta listada: primeiro o cache, depois uma faixa por vez. */
    private fun analyze(list: List<LibraryEntry>) {
        analysisJob?.cancel()
        val files = list.filter { !it.isFolder }
        val cached = files.mapNotNull { f -> repo.cachedInfo(f)?.let { f.id to it } }
        local.update { it.copy(infos = it.infos + cached) }
        analysisJob = viewModelScope.launch {
            for (f in files) {
                if (f.id in local.value.infos) continue
                val info = try {
                    repo.analyze(f) ?: UNKNOWN
                } catch (e: CancellationException) {
                    throw e
                } catch (ignored: Exception) { // arquivo que não analisa: mostra sem BPM
                    UNKNOWN
                }
                local.update { it.copy(infos = it.infos + (f.id to info)) }
            }
        }
    }

    private companion object {
        /** Copiar vários arquivos gera uma rajada de avisos: junta numa só atualização. */
        const val CHANGE_DEBOUNCE_MS = 500L

        /** Sem ritmo/duração conhecidos (análise falhou ou não achou batida). */
        val UNKNOWN = TrackInfo(0.0, null)
    }
}
