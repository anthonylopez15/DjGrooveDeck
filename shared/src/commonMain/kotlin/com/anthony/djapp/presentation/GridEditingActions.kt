package com.anthony.djapp.presentation

import com.anthony.djapp.data.MixSession
import com.anthony.djapp.domain.BeatGridEditing
import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.domain.TapTempo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToLong
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Ações do painel de BPM e grade de batidas (parte do [DjViewModel]): abrir e fechar o painel, ajustar o
 * BPM, tap tempo, deslocar a grade e ancorá-la na posição atual. As contas são do domínio
 * ([BeatGridEditing], [TapTempo]); a correção é aplicada e guardada pela [MixSession].
 */
internal class GridEditingActions(
    private val session: MixSession,
    private val screen: MutableStateFlow<ScreenState>,
    private val notify: (String) -> Unit,
) {
    private val taps = DeckId.entries.associateWith { TapTempo() }
    private val clock = TimeSource.Monotonic.markNow()

    fun open(id: DeckId) {
        if (!session.deck(id).state.hasTrack) {
            notify("Carregue uma faixa no deck ${id.name} para ajustar o BPM")
            return
        }
        taps.getValue(id).reset()
        screen.update { it.copy(gridEditor = id, tapCount = 0) }
    }

    fun close() = screen.update { it.copy(gridEditor = null, tapCount = 0) }

    fun adjustBpm(id: DeckId, delta: Double) = edit(id) { bpm, first -> BeatGridEditing.adjustBpm(bpm, delta) to first }

    fun nudge(id: DeckId, direction: Int) = edit(id) { bpm, first ->
        bpm to BeatGridEditing.nudge(first, direction * BeatGridEditing.NUDGE_SEC, bpm)
    }

    fun anchorHere(id: DeckId) = edit(id) { bpm, _ ->
        bpm to BeatGridEditing.anchorAt(session.deck(id).positionSec, bpm)
    }

    fun reset(id: DeckId) {
        session.resetGrid(id)
        taps.getValue(id).reset()
        screen.update { it.copy(tapCount = 0) }
    }

    /** Um toque do tap tempo: a partir do segundo, o BPM (com 1 casa) vale já. */
    fun tap(id: DeckId) {
        val tapper = taps.getValue(id)
        val estimate = tapper.tap(clock.elapsedNow().toDouble(DurationUnit.SECONDS))
        screen.update { it.copy(tapCount = tapper.count) }
        if (estimate != null) edit(id) { _, first -> ((estimate * 10).roundToLong() / 10.0) to first }
    }

    /** Aplica [change] (BPM e primeira batida atuais -> novos) à grade do deck; sem BPM, parte de 120. */
    private fun edit(id: DeckId, change: (bpm: Double, first: Double) -> Pair<Double, Double>) {
        val s = session.deck(id).state
        val (bpm, first) = change(s.bpm ?: DEFAULT_BPM, s.firstBeatSec)
        session.setGrid(id, bpm, first)
    }

    private companion object {
        const val DEFAULT_BPM = 120.0
    }
}
