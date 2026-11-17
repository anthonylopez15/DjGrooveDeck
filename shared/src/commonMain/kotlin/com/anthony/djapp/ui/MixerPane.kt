package com.anthony.djapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.presentation.DeckMeters
import com.anthony.djapp.presentation.DeckUiState
import com.anthony.djapp.presentation.DjAction
import com.anthony.djapp.presentation.DjUiState
import com.anthony.djapp.presentation.EqBand

// Aba MIXER: volume, EQ, medidores de nível e master.

/** Fader de volume nas bordas, knobs de EQ de cada lado e medidores de nível ao centro. */
@Composable
internal fun MixerPane(
    state: DjUiState,
    meters: (DeckId) -> DeckMeters,
    onAction: (DjAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val knob = min(maxHeight / 3.7f, 60.dp)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween) {
                VolumeFader(state.a, DeckAColor, onAction)
                EqKnobs(state.a, DeckAColor, knob, onAction)
                Row(Modifier.fillMaxHeight().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LevelMeter(level = { meters(DeckId.A).level })
                    LevelMeter(level = { meters(DeckId.B).level })
                }
                EqKnobs(state.b, DeckBColor, knob, onAction)
                VolumeFader(state.b, DeckBColor, onAction)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MASTER", fontSize = 11.sp, color = Idle)
            Slider(
                value = state.master,
                onValueChange = { onAction(DjAction.ChangeMaster(it)) },
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun VolumeFader(deck: DeckUiState, color: Color, onAction: (DjAction) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
        // mostra a porcentagem enquanto o dedo está no fader e enquanto o volume não for 0
        val showPercent = dragging || formatPercent(deck.volume) != "0%"
        Text(
            if (showPercent) formatPercent(deck.volume) else "VOL",
            fontSize = 11.sp,
            color = if (showPercent) color else Idle,
            fontWeight = if (showPercent) FontWeight.Bold else FontWeight.Normal,
        )
        VerticalFader(
            value = deck.volume,
            onChange = { onAction(DjAction.ChangeVolume(deck.id, it)) },
            range = 0f..1f,
            color = color,
            resetTo = 1f,
            ticks = 5,
            onActive = { dragging = it },
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun EqKnobs(deck: DeckUiState, color: Color, knob: Dp, onAction: (DjAction) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxHeight(),
    ) {
        for ((band, label) in listOf(EqBand.High to "HIGH", EqBand.Mid to "MID", EqBand.Low to "LOW")) {
            Knob(
                deck.eq(band),
                { onAction(DjAction.ChangeEq(deck.id, band, it)) },
                color,
                label,
                knob,
                readout = ::formatKnobPercent,
            )
        }
    }
}
