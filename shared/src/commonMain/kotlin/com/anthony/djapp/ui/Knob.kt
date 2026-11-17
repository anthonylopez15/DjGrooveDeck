package com.anthony.djapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Knob rotativo (0..1, 0,5 = neutro). Arraste vertical; toque duplo volta ao centro. */
@Composable
fun Knob(
    value: Float,
    onChange: (Float) -> Unit,
    color: Color,
    label: String,
    size: Dp,
    modifier: Modifier = Modifier,
    /** Texto mostrado no lugar do rótulo enquanto o dedo está no knob (ex.: "-37%"). */
    readout: (Float) -> String = { "" },
) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    var dragging by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(size)
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { change(0.5f) }) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { _, drag ->
                        change((current - drag.y / 220.dp.toPx()).coerceIn(0f, 1f))
                    }
                },
        ) {
            val stroke = size.toPx() * 0.12f
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Groove, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * value, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val r = this.size.minDimension / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val a = (135f + 270f * value) * PI / 180.0
            drawLine(
                Color.White,
                Offset(c.x + cos(a).toFloat() * r * 0.25f, c.y + sin(a).toFloat() * r * 0.25f),
                Offset(c.x + cos(a).toFloat() * r * 0.62f, c.y + sin(a).toFloat() * r * 0.62f),
                strokeWidth = stroke * 0.5f,
                cap = StrokeCap.Round,
            )
        }
        // porcentagem enquanto arrasta e enquanto o valor não estiver em 0%; senão, o rótulo
        val text = readout(value)
        val showReadout = text.isNotEmpty() && (dragging || text != "0%")
        Text(if (showReadout) text else label, fontSize = 11.sp, color = if (showReadout) color else Idle)
    }
}
