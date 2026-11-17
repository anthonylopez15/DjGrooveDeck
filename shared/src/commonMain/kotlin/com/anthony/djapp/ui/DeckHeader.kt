package com.anthony.djapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anthony.djapp.domain.DeckId

// Cabeçalho: aviso do topo, nome/tempo/BPM de cada deck, botão de carregar e visão geral da onda.

@Composable
internal fun Header(
    controller: DjController,
    browsing: Boolean,
    target: DeckId,
    notice: String?,
    onLoad: (DeckId) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
            if (browsing) {
                Button(onClick = onDone, contentPadding = ButtonDefaults.ContentPadding) { Text("OK", fontWeight = FontWeight.Bold) }
            } else if (notice != null) {
                Text(notice, color = Color(0xFFFACC15), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DeckHeader(controller.a, DeckAColor, browsing && target == DeckId.A, mirrored = false, { onLoad(DeckId.A) }, Modifier.weight(1f), controller.bpmMatched)
            DeckHeader(controller.b, DeckBColor, browsing && target == DeckId.B, mirrored = true, { onLoad(DeckId.B) }, Modifier.weight(1f), controller.bpmMatched)
        }
    }
}

@Composable
private fun DeckHeader(
    deck: DeckController,
    color: Color,
    selected: Boolean,
    mirrored: Boolean,
    onLoad: () -> Unit,
    modifier: Modifier = Modifier,
    bpmMatched: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!mirrored) LoadButton(color, selected, deck, onLoad)
            Column(Modifier.weight(1f), horizontalAlignment = if (mirrored) Alignment.End else Alignment.Start) {
                val loadingName = deck.loadingName
                Text(
                    when {
                        loadingName != null -> "Carregando $loadingName"
                        deck.hasTrack -> deck.title
                        else -> "Toque para carregar"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    color = if (loadingName != null) color else if (deck.hasTrack) MaterialTheme.colorScheme.onSurface else Idle,
                )
                if (loadingName != null) {
                    Text("${(deck.loadProgress * 100).toInt()}%", fontSize = 13.sp, color = color)
                } else if (deck.hasTrack) {
                    // tempo restante e, ao lado, o BPM efetivo (visível também na lista de músicas)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "-" + formatTenths(deck.durationTenths - deck.elapsedTenths).substringBeforeLast('.'),
                            fontSize = 13.sp,
                            color = Idle,
                        )
                        Text(
                            deck.effectiveBpm?.let { "${formatBpm(it)} BPM" } ?: "-- BPM",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (deck.effectiveBpm == null) Idle else if (bpmMatched) SyncBlue else BpmText,
                        )
                    }
                }
            }
            if (mirrored) LoadButton(color, selected, deck, onLoad)
        }
        if (deck.loading) {
            // barra de progresso real no lugar da mini-waveform enquanto decodifica
            Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                LinearProgressIndicator(
                    progress = { deck.loadProgress },
                    color = color,
                    trackColor = Groove,
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
            }
        } else {
            OverviewWaveform(
                peaks = deck.track?.peaks ?: FloatArray(0),
                progress = deck::progress,
                color = color,
                onSeek = deck::seek,
                modifier = Modifier.fillMaxWidth().height(24.dp),
            )
        }
    }
}

@Composable
private fun LoadButton(color: Color, selected: Boolean, deck: DeckController, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(52.dp)
            .background(if (selected || deck.loading) color.copy(alpha = 0.25f) else Color(0xFF10151B), shape)
            .border(2.dp, if (selected || deck.loading) color else color.copy(alpha = if (deck.playing) 0.2f else 0.45f), shape)
            .clickable(enabled = !deck.loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (deck.loading) {
            CircularProgressIndicator(
                progress = { deck.loadProgress },
                color = color,
                trackColor = Groove,
                strokeWidth = 3.dp,
                modifier = Modifier.size(30.dp),
            )
        } else {
            Text("♫", color = color.copy(alpha = if (deck.playing) 0.35f else 1f), fontSize = 22.sp)
        }
    }
}
