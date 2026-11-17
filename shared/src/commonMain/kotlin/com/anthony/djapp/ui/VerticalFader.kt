package com.anthony.djapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Fader vertical de arraste relativo (não "pula" para o dedo). Toque duplo volta ao [resetTo]. */
@Composable
fun VerticalFader(
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    resetTo: Float? = null,
    centerTick: Boolean = false,
    ticks: Int = 0,
    onActive: (Boolean) -> Unit = {},
) {
    val active by rememberUpdatedState(onActive)
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val span = range.endInclusive - range.start
    Canvas(
        modifier
            .width(40.dp)
            .fillMaxHeight()
            .pointerInput(resetTo) {
                detectTapGestures(onDoubleTap = { if (resetTo != null) change(resetTo) })
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { active(true) },
                    onDragEnd = { active(false) },
                    onDragCancel = { active(false) },
                ) { _, dy ->
                    val usable = (size.height - 22.dp.toPx()).coerceAtLeast(1f)
                    change((current - dy / usable * span).coerceIn(range.start, range.endInclusive))
                }
            },
    ) {
        val thumbH = 22.dp.toPx()
        val cx = size.width / 2f
        val usable = size.height - thumbH
        drawLine(Groove, Offset(cx, thumbH / 2f), Offset(cx, size.height - thumbH / 2f), strokeWidth = 4f, cap = StrokeCap.Round)
        // marcações de nível (sem números), dos dois lados da guia; as pontas e o meio são mais longos
        if (ticks >= 2) {
            for (i in 0 until ticks) {
                val y = thumbH / 2f + usable * i / (ticks - 1)
                val major = i == 0 || i == ticks - 1 || i == (ticks - 1) / 2 && ticks % 2 == 1
                val gap = 6.dp.toPx()
                val len = (if (major) 13.dp else 8.dp).toPx()
                val c = if (major) Color(0xFFC3CCD6) else Idle
                val w = 1.5.dp.toPx()
                drawLine(c, Offset(cx - gap - len, y), Offset(cx - gap, y), strokeWidth = w)
                drawLine(c, Offset(cx + gap, y), Offset(cx + gap + len, y), strokeWidth = w)
            }
        }
        if (centerTick) {
            val y = thumbH / 2f + usable / 2f
            drawLine(Idle, Offset(cx - 12f, y), Offset(cx + 12f, y), strokeWidth = 2f)
        }
        val frac = ((value - range.start) / span).coerceIn(0f, 1f)
        val top = (1f - frac) * usable
        drawRoundRect(
            color = Color(0xFFDDE3EA),
            topLeft = Offset(2f, top),
            size = Size(size.width - 4f, thumbH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
        )
        drawLine(color, Offset(8f, top + thumbH / 2f), Offset(size.width - 8f, top + thumbH / 2f), strokeWidth = 3f)
    }
}
