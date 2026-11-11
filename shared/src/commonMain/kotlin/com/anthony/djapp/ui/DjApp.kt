package com.anthony.djapp.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Tela principal: junta cabeçalho, biblioteca, abas e transporte, e cuida dos avisos. */
@Composable
fun DjApp(controller: DjController, library: Library) {
    // Posição vinda da thread de áudio, atualizada a cada frame de tela.
    LaunchedEffect(controller) {
        while (true) {
            withFrameNanos { }
            controller.poll()
        }
    }

    var browsing by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(DeckId.A) }
    var tab by remember { mutableStateOf(Tab.Waves) }
    var status by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    // faixas em carregamento: id do arquivo -> deck de destino (para o preenchimento na lista)
    val loadingEntries = remember { mutableStateMapOf<String, DeckId>() }
    val scope = rememberCoroutineScope()

    ReloadDecksFromFiles(controller, library) { notice = it }

    // avisos do sistema (fone desconectado...) entram no mesmo aviso do topo
    LaunchedEffect(controller.systemNotice) {
        controller.systemNotice?.let {
            notice = it
            controller.systemNotice = null
        }
    }

    // aviso curto no topo; some sozinho
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2500)
            notice = null
        }
    }

    MaterialTheme(colorScheme = DjColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Header(
                    controller = controller,
                    browsing = browsing,
                    target = target,
                    notice = notice,
                    onLoad = { id ->
                        // regra: não troca a faixa de um deck que está tocando
                        if (controller.deck(id).playingNow) {
                            notice = mustPauseMessage(id)
                        } else {
                            target = id
                            browsing = true
                        }
                    },
                    onDone = { browsing = false; status = null },
                )
                if (browsing) {
                    TransportRow(controller)
                    LibraryBrowser(
                        library,
                        status,
                        Modifier.weight(1f),
                        rowLoading = { entryId ->
                            loadingEntries[entryId]?.let { id -> controller.deck(id).loadProgress to deckColor(id) }
                        },
                    ) { entry ->
                        scope.loadFromLibrary(controller, library, target, entry, loadingEntries) { status = it }
                    }
                } else {
                    when (tab) {
                        Tab.Waves -> {
                            Row(
                                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                DeckPlatter(controller, DeckId.A, DeckAColor, Modifier.weight(1f))
                                DeckPlatter(controller, DeckId.B, DeckBColor, Modifier.weight(1f))
                            }
                            WavesPane(controller, Modifier.weight(1.25f))
                        }
                        // No mixer as ondas continuam visíveis em cima; os pratos saem.
                        Tab.Mixer -> {
                            WavesPane(controller, Modifier.weight(0.8f))
                            MixerPane(controller, Modifier.weight(1.6f))
                        }
                    }
                    TabBar(tab, controller.keyLock, controller::toggleKeyLock) { tab = it }
                    TransportRow(controller)
                }
            }
        }
    }
}
