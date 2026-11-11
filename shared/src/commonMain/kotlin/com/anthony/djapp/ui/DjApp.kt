package com.anthony.djapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

private val DeckAColor = Color(0xFF22D3EE)
private val DeckBColor = Color(0xFFF472B6)

private val DjColors = darkColorScheme(
    background = Color(0xFF0B0F14),
    surface = Color(0xFF151B23),
    primary = DeckAColor,
    onSurface = Color(0xFFE6EDF3),
    onBackground = Color(0xFFE6EDF3),
)

/** Tela única do DJ. [onLoad] abre o seletor de arquivos da plataforma para o deck pedido. */
@Composable
fun DjApp(controller: DjController, onLoad: (DeckId) -> Unit) {
    LaunchedEffect(controller) {
        while (true) {
            controller.poll()
            delay(33)
        }
    }
    MaterialTheme(colorScheme = DjColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.safeDrawingPadding().padding(8.dp)) {
                if (maxWidth > maxHeight) {
                    // paisagem: deck A | mixer | deck B
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeckPanel("A", controller.a, DeckAColor, { onLoad(DeckId.A) }, Modifier.weight(1f).fillMaxSize())
                        MixerPanel(controller, Modifier.weight(0.8f).fillMaxSize())
                        DeckPanel("B", controller.b, DeckBColor, { onLoad(DeckId.B) }, Modifier.weight(1f).fillMaxSize())
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DeckPanel("A", controller.a, DeckAColor, { onLoad(DeckId.A) }, Modifier.fillMaxWidth())
                        MixerPanel(controller, Modifier.fillMaxWidth())
                        DeckPanel("B", controller.b, DeckBColor, { onLoad(DeckId.B) }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckPanel(
    label: String,
    deck: DeckController,
    color: Color,
    onLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(deck.title, maxLines = 1, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onLoad) { Text("Carregar") }
        }
        Waveform(
            peaks = deck.peaks,
            progress = deck.progress,
            color = color,
            onSeek = deck::seek,
            modifier = Modifier.fillMaxWidth().height(80.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(deck.elapsedSec), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text("-" + formatTime((deck.durationSec - deck.elapsedSec).coerceAtLeast(0.0)), fontSize = 20.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = deck::cue, enabled = deck.hasTrack, modifier = Modifier.weight(1f)) {
                Text("CUE")
            }
            Button(
                onClick = deck::togglePlay,
                enabled = deck.hasTrack,
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.Black),
                modifier = Modifier.weight(2f),
            ) { Text(if (deck.playing) "PAUSE" else "PLAY", fontWeight = FontWeight.Bold) }
        }
        val pct = deck.tempoPercent
        LabeledSlider(
            label = "Tempo ${if (pct >= 0) "+" else ""}${((pct * 10).toInt() / 10f)}%",
            value = pct,
            range = -16f..16f,
            color = color,
            onChange = { deck.changeTempo(if (abs(it) < 0.4f) 0f else it) }, // "ímã" no zero
        )
    }
}

@Composable
private fun MixerPanel(controller: DjController, modifier: Modifier = Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EqColumn("A", controller.a, DeckAColor, Modifier.weight(1f))
            EqColumn("B", controller.b, DeckBColor, Modifier.weight(1f))
        }
        LabeledSlider("Master", controller.master, 0f..1f, Color.White, controller::changeMaster)
        Text("Crossfader  A ◄──► B", fontSize = 12.sp)
        Slider(value = controller.crossfader, onValueChange = controller::changeCrossfader)
    }
}

@Composable
private fun EqColumn(label: String, deck: DeckController, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("EQ $label", color = color, fontWeight = FontWeight.Bold)
        LabeledSlider("HI", deck.high, 0f..1f, color, deck::changeHigh)
        LabeledSlider("MID", deck.mid, 0f..1f, color, deck::changeMid)
        LabeledSlider("LOW", deck.low, 0f..1f, color, deck::changeLow)
        LabeledSlider("VOL", deck.volume, 0f..1f, color, deck::changeVolume)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(label, fontSize = 12.sp)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
            ),
        )
    }
}

internal fun formatTime(sec: Double): String {
    val total = sec.toInt().coerceAtLeast(0)
    val s = total % 60
    return "${total / 60}:${if (s < 10) "0" else ""}$s"
}
