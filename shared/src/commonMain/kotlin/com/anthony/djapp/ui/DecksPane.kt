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
            // andamento: um toque = 0,1%; segurando repete e acelera
            RepeatButton("−", deck.hasTrack, { fast ->
                onAction(DjAction.SetTempo(id, stepTempo(deck.tempoPercent, -1, fast)))
            }, Modifier.width(44.dp))
            SyncButton(
                state = deck.syncState,
                enabled = deck.canToggleSync,
                onClick = { onAction(DjAction.ToggleSync(id)) },
                modifier = Modifier.weight(1f),
            )
            RepeatButton("+", deck.hasTrack, { fast ->
                onAction(DjAction.SetTempo(id, stepTempo(deck.tempoPercent, +1, fast)))
            }, Modifier.width(44.dp))
        }
    }
}

/** Próximo andamento ao apertar − ou + ([direction]): passo de 0,1% (0,5% se [fast]), com 1 casa. */
private fun stepTempo(current: Float, direction: Int, fast: Boolean): Float {
    val step = if (fast) 0.5f else 0.1f
    return kotlin.math.round((current + direction * step) * 10f) / 10f
}

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
