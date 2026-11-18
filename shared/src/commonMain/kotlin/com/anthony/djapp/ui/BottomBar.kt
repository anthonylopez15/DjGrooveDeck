package com.anthony.djapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.presentation.DjAction
import com.anthony.djapp.presentation.DjUiState
import com.anthony.djapp.presentation.Tab

// Rodapé: abas (ONDAS, MIXER, KEY) e transporte (voltar ao início, play, crossfader).

@Composable
internal fun TabBar(selected: Tab, keyLock: Boolean, onToggleKey: () -> Unit, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF10151B)).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Tab.entries.forEach { t ->
            val c = if (t == selected) Color(0xFF3B82F6) else Idle
            TabItem(t.label, c, if (t == Tab.Waves) TabIconKind.Waves else TabIconKind.Mixer, 28.dp) { onSelect(t) }
        }
        // KEY: mantém o tom ao mudar o andamento (tempo e SYNC); cadeado fechado = ligado
        TabItem(
            "KEY",
            if (keyLock) Color(0xFF3B82F6) else Idle,
            if (keyLock) TabIconKind.KeyLock else TabIconKind.KeyUnlock,
            20.dp,
            onToggleKey,
        )
    }
}

@Composable
private fun TabItem(label: String, color: Color, icon: TabIconKind, hPad: Dp, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = hPad, vertical = 6.dp),
    ) {
        TabIcon(icon, color)
        Text(label, textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/** Voltar ao início e play de cada deck, com o crossfader entre eles. */
@Composable
internal fun TransportRow(state: DjUiState, onAction: (DjAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF1A2028)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RestartButton(state.a.hasTrack, { onAction(DjAction.Restart(DeckId.A)) })
        PlayButton(state.a.playing, state.a.hasTrack, DeckAColor, {
            onAction(DjAction.TogglePlay(DeckId.A))
        }, waiting = state.a.waitingStart)
        CrossfaderSlider(state.crossfader, { onAction(DjAction.ChangeCrossfader(it)) }, Modifier.weight(1f))
        PlayButton(state.b.playing, state.b.hasTrack, DeckBColor, {
            onAction(DjAction.TogglePlay(DeckId.B))
        }, waiting = state.b.waitingStart)
        RestartButton(state.b.hasTrack, { onAction(DjAction.Restart(DeckId.B)) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrossfaderSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Slider(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        thumb = {
            Box(Modifier.size(width = 16.dp, height = 40.dp).background(Color(0xFFDDE3EA), RoundedCornerShape(5.dp)))
        },
        track = {
            // guia + 5 marcações (0, 25, 50, 75, 100%) acima e abaixo, alinhadas ao curso da alça
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                val half = 8.dp.toPx() // metade da largura da alça
                val usable = size.width - 2 * half
                val cy = size.height / 2f
                drawLine(Groove, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                val gap = 6.dp.toPx()
                for (i in 0..4) {
                    val x = half + usable * i / 4f
                    val major = i % 2 == 0
                    val len = (if (major) 9.dp else 5.dp).toPx()
                    val c = if (major) Color(0xFFC3CCD6) else Idle
                    val w = 1.5.dp.toPx()
                    drawLine(c, Offset(x, cy - gap - len), Offset(x, cy - gap), strokeWidth = w)
                    drawLine(c, Offset(x, cy + gap), Offset(x, cy + gap + len), strokeWidth = w)
                }
            }
        },
    )
}
