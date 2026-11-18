package com.anthony.djapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.presentation.DjAction
import com.anthony.djapp.presentation.DjViewModel
import com.anthony.djapp.presentation.LibraryViewModel
import com.anthony.djapp.presentation.Tab

/**
 * Tela principal: lê o estado dos ViewModels e repassa as ações. Sem regra nenhuma aqui (ADR 0001).
 * [libraryViewModel] cuida da lista de arquivos (pastas, BPM); escolher uma faixa vai para o [viewModel].
 */
@Composable
fun DjApp(viewModel: DjViewModel, libraryViewModel: LibraryViewModel) {
    // medidores (posição, nível) acompanham a tela quadro a quadro
    LaunchedEffect(viewModel) {
        while (true) {
            withFrameNanos { }
            viewModel.onFrame()
        }
    }

    val state by viewModel.uiState.collectAsState()
    val libraryState by libraryViewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val meters = viewModel::meters

    MaterialTheme(colorScheme = DjColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Header(state, meters, onAction)
                if (state.browsing) {
                    TransportRow(state, onAction)
                    LibraryBrowser(
                        state = libraryState,
                        status = state.libraryStatus,
                        onAction = libraryViewModel::onAction,
                        onPick = { entry -> onAction(DjAction.PickTrack(entry)) },
                        modifier = Modifier.weight(1f),
                        rowLoading = { entryId ->
                            state.loadingEntries[entryId]?.let { id -> state.deck(id).loadProgress to deckColor(id) }
                        },
                    )
                } else {
                    when (state.tab) {
                        Tab.Waves -> {
                            val editing = state.gridEditor
                            if (editing != null) {
                                // painel de BPM no lugar dos pratos; as ondas (com a grade) seguem à vista
                                BpmPanel(state.deck(editing), state.tapCount, onAction, Modifier.weight(1f))
                            } else {
                                Row(
                                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    for (id in DeckId.entries) {
                                        DeckPlatter(state, id, meters(id), onAction, Modifier.weight(1f))
                                    }
                                }
                            }
                            WavesPane(state, meters, onAction, Modifier.weight(1.25f))
                        }
                        // No mixer as ondas continuam visíveis em cima; os pratos saem.
                        Tab.Mixer -> {
                            WavesPane(state, meters, onAction, Modifier.weight(0.8f))
                            MixerPane(state, meters, onAction, Modifier.weight(1.6f))
                        }
                    }
                    TabBar(state.tab, state.keyLock, { onAction(DjAction.ToggleKeyLock) }) {
                        onAction(DjAction.SelectTab(it))
                    }
                    TransportRow(state, onAction)
                }
            }
        }
    }
}
