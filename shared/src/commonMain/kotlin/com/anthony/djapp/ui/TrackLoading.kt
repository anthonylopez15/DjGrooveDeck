package com.anthony.djapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Carregar faixas da biblioteca e recarregar do arquivo as que foram liberadas ou restauradas.

internal fun mustPauseMessage(id: DeckId) = "Pause o deck ${id.name} para trocar a faixa"

/**
 * Recarrega do arquivo, com a tela à vista: o áudio solto por falta de memória em segundo plano, ou a
 * faixa que estava no deck quando o sistema encerrou o processo (volta pausada, na mesma posição).
 */
@Composable
internal fun ReloadDecksFromFiles(controller: DjController, library: Library, onError: (String) -> Unit) {
    for (id in DeckId.entries) {
        val deck = controller.deck(id)
        val pending = deck.pendingRestore
        LaunchedEffect(deck.audioReleased, pending, controller.foreground) {
            if (!controller.foreground || deck.loading) return@LaunchedEffect
            val entry = pending?.source ?: deck.source?.takeIf { deck.audioReleased } ?: return@LaunchedEffect
            deck.startLoading(entry.name)
            try {
                val track = library.decode(entry, deck::updateLoadProgress)
                if (pending != null) deck.applyRestore(track, pending) else deck.restoreAudio(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (pending != null) deck.dropRestore()
                onError("Não consegui recarregar ${entry.name}: ${e.message}")
            } finally {
                if (deck.loadingName == entry.name) deck.cancelLoading()
            }
        }
    }
}

/**
 * Carrega [entry] da biblioteca no deck [deckId] (regra: só com o deck parado). [loadingEntries]
 * marca a linha da lista em carregamento; [onStatus] recebe o aviso para a lista (null = limpo).
 */
internal fun CoroutineScope.loadFromLibrary(
    controller: DjController,
    library: Library,
    deckId: DeckId,
    entry: LibraryEntry,
    loadingEntries: SnapshotStateMap<String, DeckId>,
    onStatus: (String?) -> Unit,
) {
    val deck = controller.deck(deckId)
    if (deck.playingNow) {
        onStatus(mustPauseMessage(deckId))
        return
    }
    if (deck.loading) return
    launch {
        onStatus(null)
        deck.startLoading(entry.name)
        loadingEntries[entry.id] = deckId
        try {
            val loaded = controller.loadTrack(deckId, library.decode(entry, deck::updateLoadProgress), entry)
            if (!loaded) { // o deck começou a tocar durante a decodificação
                deck.cancelLoading()
                onStatus(mustPauseMessage(deckId))
            }
        } catch (e: CancellationException) {
            deck.cancelLoading()
            throw e
        } catch (e: Exception) {
            deck.cancelLoading()
            onStatus("Não consegui abrir: ${e.message}")
        } finally {
            loadingEntries.remove(entry.id)
        }
    }
}
