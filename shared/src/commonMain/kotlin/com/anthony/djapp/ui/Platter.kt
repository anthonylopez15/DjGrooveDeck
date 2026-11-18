package com.anthony.djapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.anthony.djapp.presentation.DeckMeters
import com.anthony.djapp.presentation.DeckUiState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Prato do deck: anel com marcador que gira com a faixa (33⅓ rpm × tempo) e, dentro,
 * tempo/decorrido/restante. Por enquanto só visual.
 */
@Composable
fun Platter(
    deck: DeckUiState,
    meters: DeckMeters,
    color: Color,
    bpmMatched: Boolean,
    onBpmClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val d: Dp = min(maxWidth, maxHeight)
        Box(Modifier.size(d), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(d)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color(0xFF05070A), r, c)
                drawCircle(Groove, r - 1.5f, c, style = Stroke(3f))
                drawCircle(Color(0xFF151B23), r * 0.72f, c)
                drawCircle(Groove, r * 0.72f, c, style = Stroke(2f))
                val ang = (meters.positionSec * 200.0 % 360.0) * PI / 180.0 - PI / 2
                val on = if (deck.playing) color else Idle
                drawLine(
                    on,
                    Offset(c.x + cos(ang).toFloat() * r * 0.80f, c.y + sin(ang).toFloat() * r * 0.80f),
                    Offset(c.x + cos(ang).toFloat() * r * 0.96f, c.y + sin(ang).toFloat() * r * 0.96f),
                    strokeWidth = r * 0.07f,
                    cap = StrokeCap.Round,
                )
            }
            val big = (d.value * 0.14f).sp
            val small = (d.value * 0.095f).sp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatTempo(deck.tempoPercent), fontSize = small, color = Idle)
                // tocar no número abre o painel de BPM e grade; sublinhado = grade corrigida por você
                Text(
                    deck.effectiveBpm?.let { formatBpm(it) } ?: "--",
                    color = if (bpmMatched) SyncBlue else BpmText,
                    fontSize = big,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (deck.gridEdited) TextDecoration.Underline else null,
                    modifier = Modifier.clickable(onClick = onBpmClick).padding(horizontal = 12.dp, vertical = 2.dp),
                )
                if (deck.hasTrack) {
                    Text(formatTenths(meters.elapsedTenths), fontSize = small)
                    Text("-" + formatTenths(deck.durationTenths - meters.elapsedTenths), fontSize = small, color = Idle)
                } else {
                    Text("--:--", fontSize = small, color = Idle)
                }
            }
        }
    }
}
