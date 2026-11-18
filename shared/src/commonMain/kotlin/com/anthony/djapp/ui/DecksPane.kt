package com.anthony.djapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.domain.SyncRules
import com.anthony.djapp.presentation.DeckMeters
import com.anthony.djapp.presentation.DjAction
import com.anthony.djapp.presentation.DjUiState

// Aba ONDAS: pratos com o botão SYNC e as ondas ampliadas.

@Composable
internal fun DeckPlatter(
    state: DjUiState,
    id: DeckId,
    meters: DeckMeters,
    onAction: (DjAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val deck = state.deck(id)
    Column(modifier.fillMaxHeight().padding(vertical = 6.dp)) {
        Platter(
            deck,
            meters,
            deckColor(id),
            state.bpmMatched,
            onTempoClick = { onAction(DjAction.SetTempo(id, 0f)) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // andamento: um toque = 1%; segurando repete
            RepeatButton("−", deck.hasTrack, {
                onAction(DjAction.SetTempo(id, stepTempo(deck.tempoPercent, -1)))
            }, Modifier.width(44.dp))
            SyncButton(
                state = deck.syncState,
                enabled = deck.canToggleSync,
                onClick = { onAction(DjAction.ToggleSync(id)) },
                modifier = Modifier.weight(1f),
            )
            RepeatButton("+", deck.hasTrack, {
                onAction(DjAction.SetTempo(id, stepTempo(deck.tempoPercent, +1)))
            }, Modifier.width(44.dp))
        }
    }
}

/** Próximo andamento ao apertar − ou + ([direction]): de 1 em 1% (+1,0%, +2,0%...), até ±[SyncRules.MAX_TEMPO_PERCENT]. */
private fun stepTempo(current: Float, direction: Int): Float =
    kotlin.math.round(current + direction * SyncRules.TEMPO_STEP_PERCENT)

@Composable
internal fun WavesPane(
    state: DjUiState,
    meters: (DeckId) -> DeckMeters,
    onAction: (DjAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (id in DeckId.entries) {
            ScrollingWaveform(
                deck = state.deck(id),
                meters = meters(id),
                color = deckColor(id),
                label = id.name,
                onScrub = { onAction(DjAction.Scrub(id, it)) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}
