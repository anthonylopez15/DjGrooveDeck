package com.anthony.djapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anthony.djapp.domain.BeatGridEditing
import com.anthony.djapp.presentation.DeckUiState
import com.anthony.djapp.presentation.DjAction

/**
 * Painel para corrigir o BPM e alinhar a grade de batidas de um deck. Não é um diálogo: fica na tela
 * (no lugar dos pratos) até tocar em OK, então não fecha ao tocar fora nem some quando o layout muda.
 * As linhas de batida das ondas, logo abaixo, mostram a grade mexendo enquanto você ajusta.
 */
@Composable
internal fun BpmPanel(deck: DeckUiState, tapCount: Int, onAction: (DjAction) -> Unit, modifier: Modifier = Modifier) {
    val id = deck.id
    val color = deckColor(id)
    val hasBpm = deck.bpm != null
    Column(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "BPM · Deck ${id.name}" + if (deck.gridEdited) " · corrigido" else "",
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { onAction(DjAction.CloseGridEditor) }) { Text("OK", fontWeight = FontWeight.Bold) }
        }
        BpmStepper(deck, onAction)
        TapAndNudge(deck, tapCount, onAction)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Step("Batida 1 aqui", Modifier.weight(1f), hasBpm) { onAction(DjAction.GridAnchorHere(id)) }
            Step("Redefinir", Modifier.weight(1f), deck.gridEdited) { onAction(DjAction.GridReset(id)) }
        }
        Text(
            "Toque no ritmo (TAP) ou ajuste o número. Com a música tocando, toque em \"Batida 1 aqui\" numa batida forte; " +
                "os traços laranja e brancos das ondas mostram a grade.",
            color = Idle,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun BpmStepper(deck: DeckUiState, onAction: (DjAction) -> Unit) {
    val id = deck.id
    val hasBpm = deck.bpm != null
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Step("−1", Modifier.weight(1f), hasBpm) { onAction(DjAction.GridAdjustBpm(id, -BeatGridEditing.COARSE_STEP)) }
        Step("−0,1", Modifier.weight(1f), hasBpm) { onAction(DjAction.GridAdjustBpm(id, -BeatGridEditing.FINE_STEP)) }
        Text(
            deck.bpm?.let { formatBpm(it) } ?: "--",
            color = BpmText,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            modifier = Modifier.weight(1.4f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Step("+0,1", Modifier.weight(1f), hasBpm) { onAction(DjAction.GridAdjustBpm(id, BeatGridEditing.FINE_STEP)) }
        Step("+1", Modifier.weight(1f), hasBpm) { onAction(DjAction.GridAdjustBpm(id, BeatGridEditing.COARSE_STEP)) }
    }
}

@Composable
private fun TapAndNudge(deck: DeckUiState, tapCount: Int, onAction: (DjAction) -> Unit) {
    val id = deck.id
    val color = deckColor(id)
    val hasBpm = deck.bpm != null
    Row(
        Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { onAction(DjAction.GridTap(id)) },
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color(0xFF0B0F14)),
            modifier = Modifier.weight(1f).fillMaxSize(),
        ) {
            Text(if (tapCount > 0) "TAP · $tapCount" else "TAP", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Step("◂ 10 ms", Modifier.weight(0.8f).fillMaxSize(), hasBpm) { onAction(DjAction.GridNudge(id, -1)) }
        Step("10 ms ▸", Modifier.weight(0.8f).fillMaxSize(), hasBpm) { onAction(DjAction.GridNudge(id, +1)) }
    }
}

@Composable
private fun Step(label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(label, fontSize = 13.sp, maxLines = 1)
    }
}
