package com.anthony.djapp.presentation

import com.anthony.djapp.data.DeckState
import com.anthony.djapp.data.MixState
import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.domain.SyncState
import com.anthony.djapp.engine.Track

/** Abas da tela principal. */
enum class Tab(val label: String) { Waves("ONDAS"), Mixer("MIXER") }

/** Bandas do EQ de 3 bandas. */
enum class EqBand { Low, Mid, High }

/** O que a tela mostra de um deck (imutável). Posição e nível ficam em [DeckMeters]. */
data class DeckUiState(
    val id: DeckId,
    /** Só para desenhar as ondas (picos, detalhe, taxa); numa faixa liberada não há áudio. */
    val track: Track?,
    val title: String,
    val hasTrack: Boolean,
    val durationSec: Double,
    /** BPM na velocidade original e com o ajuste de tempo; null sem ritmo detectado. */
    val bpm: Double?,
    val effectiveBpm: Double?,
    val firstBeatSec: Double,
    val tempoPercent: Float,
    val volume: Float,
    val low: Float,
    val mid: Float,
    val high: Float,
    val playing: Boolean,
    /** Nome da faixa em decodificação; null sem carregamento. */
    val loadingName: String?,
    val loadProgress: Float,
    val syncState: SyncState,
    val canToggleSync: Boolean,
    /** Esperando o tempo 1 do outro deck para largar (play com SYNC). */
    val waitingStart: Boolean,
) {
    val loading: Boolean get() = loadingName != null
    val durationTenths: Int get() = (durationSec * 10).toInt()

    fun eq(band: EqBand): Float = when (band) {
        EqBand.Low -> low
        EqBand.Mid -> mid
        EqBand.High -> high
    }
}

/** Estado completo da tela principal (imutável). */
data class DjUiState(
    val a: DeckUiState,
    val b: DeckUiState,
    val crossfader: Float,
    val master: Float,
    val keyLock: Boolean,
    /** BPM efetivo igual nos dois decks (números em azul). */
    val bpmMatched: Boolean,
    val tab: Tab,
    /** Biblioteca aberta, carregando no deck [target]. */
    val browsing: Boolean,
    val target: DeckId,
    /** Aviso curto no topo; some sozinho. */
    val notice: String?,
    /** Mensagem na lista da biblioteca (erro ao abrir, deck tocando...). */
    val libraryStatus: String?,
    /** Arquivos em carregamento: id do arquivo -> deck de destino (preenchimento na lista). */
    val loadingEntries: Map<String, DeckId>,
) {
    fun deck(id: DeckId): DeckUiState = if (id == DeckId.A) a else b
}

/** Estado que é só da tela (não da sessão de áudio). */
internal data class ScreenState(
    val tab: Tab = Tab.Waves,
    val browsing: Boolean = false,
    val target: DeckId = DeckId.A,
    val notice: String? = null,
    val libraryStatus: String? = null,
    val loadingEntries: Map<String, DeckId> = emptyMap(),
)

internal fun uiStateOf(mix: MixState, screen: ScreenState): DjUiState = DjUiState(
    a = deckUiOf(DeckId.A, mix),
    b = deckUiOf(DeckId.B, mix),
    crossfader = mix.crossfader,
    master = mix.master,
    keyLock = mix.keyLock,
    bpmMatched = mix.bpmMatched,
    tab = screen.tab,
    browsing = screen.browsing,
    target = screen.target,
    notice = screen.notice,
    libraryStatus = screen.libraryStatus,
    loadingEntries = screen.loadingEntries,
)

private fun deckUiOf(id: DeckId, mix: MixState): DeckUiState {
    val d: DeckState = mix.deck(id)
    return DeckUiState(
        id = id,
        track = d.track,
        title = d.title,
        hasTrack = d.hasTrack,
        durationSec = d.durationSec,
        bpm = d.bpm,
        effectiveBpm = d.effectiveBpm,
        firstBeatSec = d.firstBeatSec,
        tempoPercent = d.tempoPercent,
        volume = d.volume,
        low = d.low,
        mid = d.mid,
        high = d.high,
        playing = d.playing,
        loadingName = d.loadingName,
        loadProgress = d.loadProgress,
        syncState = mix.syncState(id),
        canToggleSync = mix.canToggleSync(id),
        waitingStart = d.startPending,
    )
}
