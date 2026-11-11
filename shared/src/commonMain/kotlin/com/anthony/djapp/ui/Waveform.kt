package com.anthony.djapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

/** Visão geral da faixa; toque ou arraste para buscar. */
@Composable
fun Waveform(
    peaks: FloatArray,
    progress: Float,
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
        if (peaks.isNotEmpty()) {
            val px = w.toInt().coerceAtLeast(1)
            val played = Stroke(width = 1f)
            for (x in 0 until px) {
                val p = peaks[(x.toLong() * peaks.size / px).toInt().coerceIn(0, peaks.size - 1)]
                val half = (p * mid * 0.95f).coerceAtLeast(1f)
                val isPlayed = x.toFloat() / px <= progress
                drawLine(
                    color = if (isPlayed) color else color.copy(alpha = 0.35f),
                    start = Offset(x.toFloat(), mid - half),
                    end = Offset(x.toFloat(), mid + half),
                    strokeWidth = played.width,
                )
            }
        }
        val px = progress * w
        drawLine(Color.White, Offset(px, 0f), Offset(px, h), strokeWidth = 2f)
    }
}
