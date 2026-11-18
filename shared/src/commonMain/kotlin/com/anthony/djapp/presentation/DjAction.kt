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

    /** Andamento do deck em % (−20 a +20); 0 volta ao original. */
    data class SetTempo(val deck: DeckId, val percent: Float) : DjAction

    data class ChangeCrossfader(val value: Float) : DjAction

    data class ChangeMaster(val value: Float) : DjAction

    data object ToggleKeyLock : DjAction

    /** Liga/desliga a volta suave do crossfader ao meio. */
    data object ToggleAutoFade : DjAction

    data class SelectTab(val tab: Tab) : DjAction

    /** Abre a biblioteca para carregar no [deck] (só se ele não estiver tocando). */
    data class OpenLibrary(val deck: DeckId) : DjAction

    data object CloseLibrary : DjAction

    /** Escolheu um arquivo na biblioteca: carrega no deck de destino. */
    data class PickTrack(val entry: LibraryEntry) : DjAction
}
