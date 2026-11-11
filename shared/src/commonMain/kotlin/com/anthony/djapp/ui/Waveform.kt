package com.anthony.djapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

private const val VISIBLE_SEC = 6.0
private val BassColor = Color(0xFFEF4444)
private val TrebleColor = Color(0xFF3B82F6)
private val BarColor = Color(0xFFF59E0B)

/** Visão geral da faixa inteira; toque ou arraste para buscar. [progress] é lido só no desenho. */
@Composable
fun OverviewWaveform(
    peaks: FloatArray,
    progress: () -> Float,
    color: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seek by rememberUpdatedState(onSeek)
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { seek((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    seek((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val p = progress()
        if (peaks.isNotEmpty()) {
            val px = w.toInt().coerceAtLeast(1)
            for (x in 0 until px) {
                val peak = peaks[(x.toLong() * peaks.size / px).toInt().coerceIn(0, peaks.size - 1)]
                val half = (peak * mid * 0.95f).coerceAtLeast(1f)
                drawLine(
                    color = if (x.toFloat() / px <= p) color else color.copy(alpha = 0.35f),
                    start = Offset(x.toFloat(), mid - half),
                    end = Offset(x.toFloat(), mid + half),
                    strokeWidth = 1f,
                )
            }
        }
        drawLine(Color.White, Offset(p * w, 0f), Offset(p * w, h), strokeWidth = 2f)
    }
}

/**
 * Onda ampliada (~6 s) que rola sob um marcador fixo no centro, colorida por frequência
 * (grave = vermelho, agudo = azul). Arrastar na horizontal move a posição da faixa.
 */
@Composable
fun ScrollingWaveform(
    deck: DeckController,
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(Color(0xFF0D1218))) {
        val track = deck.track
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(deck) {
                    detectHorizontalDragGestures { _, dx ->
                        deck.scrubBySeconds(-dx / size.width * VISIBLE_SEC)
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val mid = h / 2f
            if (track != null) {
                val d = track.detail
                val bucketsPerSec = track.sampleRate.toDouble() / d.framesPerBucket
                val center = deck.positionSec * bucketsPerSec
                val visible = VISIBLE_SEC * bucketsPerSec
                var x = 0f
                while (x < w) {
                    val b = (center + (x - w / 2f) / w * visible).toInt()
                    if (b in 0 until d.size) {
                        val half = (d.peak[b] * mid * 0.92f).coerceAtLeast(1f)
                        val c = lerp(TrebleColor, BassColor, d.bass[b])
                        drawLine(
                            color = c.copy(alpha = if (x < w / 2f) 0.55f else 1f),
                            start = Offset(x, mid - half),
                            end = Offset(x, mid + half),
                            strokeWidth = 3f,
                        )
                    }
                    x += 3f
                }
            }
            // grade de batidas: linha fraca em cada batida, laranja a cada 4 (compasso)
            val bpm = deck.bpm
            if (track != null && bpm != null) {
                val period = 60.0 / bpm
                val tStart = deck.positionSec - VISIBLE_SEC / 2
                val tEnd = deck.positionSec + VISIBLE_SEC / 2
                val first = deck.firstBeatSec
                var n = ceil((tStart - first) / period).toInt().coerceAtLeast(0)
                while (true) {
                    val t = first + n * period
                    if (t > tEnd) break
                    val x = ((t - tStart) / VISIBLE_SEC * w).toFloat()
                    val bar = n % 4 == 0
                    drawLine(
                        if (bar) BarColor else Color.White.copy(alpha = 0.35f),
                        Offset(x, 0f),
                        Offset(x, h),
                        strokeWidth = if (bar) 3f else 1.5f,
                    )
                    n++
                }
            }
            drawLine(Color(0xFFFF3B30), Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 3f)
        }
        Text(
            label,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
    }
}
