package com.anthony.djapp.presentation

import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.domain.LibraryEntry

/** Tudo o que a tela principal pode pedir ao [DjViewModel]. */
sealed interface DjAction {
    data class TogglePlay(val deck: DeckId) : DjAction

    /** Volta ao início da faixa. */
    data class Restart(val deck: DeckId) : DjAction

    data class ToggleSync(val deck: DeckId) : DjAction

    /** Busca na visão geral: [fraction] 0..1 da faixa. */
    data class Seek(val deck: DeckId, val fraction: Float) : DjAction

    /** Arrastar a onda ampliada: anda [deltaSec] segundos. */
    data class Scrub(val deck: DeckId, val deltaSec: Double) : DjAction

    data class ChangeVolume(val deck: DeckId, val value: Float) : DjAction

    data class ChangeEq(val deck: DeckId, val band: EqBand, val value: Float) : DjAction

    data class ChangeCrossfader(val value: Float) : DjAction

    data class ChangeMaster(val value: Float) : DjAction

    data object ToggleKeyLock : DjAction

    data class SelectTab(val tab: Tab) : DjAction

    /** Abre a biblioteca para carregar no [deck] (só se ele não estiver tocando). */
    data class OpenLibrary(val deck: DeckId) : DjAction

    data object CloseLibrary : DjAction

    /** Abre o painel de BPM e grade de batidas do deck (toque no número do BPM do prato). */
    data class OpenGridEditor(val deck: DeckId) : DjAction

    data object CloseGridEditor : DjAction

    /** Soma [delta] ao BPM do deck (passo fino 0,1 ou grosso 1). */
    data class GridAdjustBpm(val deck: DeckId, val delta: Double) : DjAction

    /** Um toque do tap tempo (o BPM sai da média dos intervalos entre os toques). */
    data class GridTap(val deck: DeckId) : DjAction

    /** Desloca a grade 10 ms: [direction] -1 (mais cedo) ou +1 (mais tarde). */
    data class GridNudge(val deck: DeckId, val direction: Int) : DjAction

    /** "A batida 1 é aqui": ancora a grade na posição atual do deck. */
    data class GridAnchorHere(val deck: DeckId) : DjAction

    /** Volta à grade detectada. */
    data class GridReset(val deck: DeckId) : DjAction

    /** Escolheu um arquivo na biblioteca: carrega no deck de destino. */
    data class PickTrack(val entry: LibraryEntry) : DjAction
}
