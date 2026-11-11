package com.anthony.djapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Aba ONDAS: pratos com o botão SYNC e as ondas ampliadas.

@Composable
internal fun DeckPlatter(controller: DjController, id: DeckId, color: Color, modifier: Modifier = Modifier) {
    val deck = controller.deck(id)
    Column(modifier.fillMaxHeight().padding(vertical = 6.dp)) {
        Platter(deck, color, controller.bpmMatched, Modifier.weight(1f).fillMaxWidth())
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SyncButton(
                state = controller.syncState(id),
                enabled = controller.canToggleSync(id),
                onClick = { controller.toggleSync(id) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun WavesPane(controller: DjController, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ScrollingWaveform(controller.a, DeckAColor, "A", Modifier.weight(1f).fillMaxWidth())
        ScrollingWaveform(controller.b, DeckBColor, "B", Modifier.weight(1f).fillMaxWidth())
    }
}
