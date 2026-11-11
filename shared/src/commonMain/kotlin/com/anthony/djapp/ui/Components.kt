package com.anthony.djapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val Idle = Color(0xFF8A94A0)
internal val Groove = Color(0xFF39424D)

/** Botão redondo de play/pause com ícone desenhado (sem depender de fonte/emoji). */
@Composable
fun PlayButton(
    playing: Boolean,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = (if (playing) color else Idle).copy(alpha = if (enabled) 1f else 0.35f)
    Box(
        modifier
            .size(56.dp)
            .clip(CircleShape)
            .border(2.dp, c, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            if (playing) {
                val bw = size.width * 0.28f
                drawRect(c, Offset(size.width * 0.12f, 0f), Size(bw, size.height))
                drawRect(c, Offset(size.width * 0.60f, 0f), Size(bw, size.height))
            } else {
                val p = Path().apply {
                    moveTo(size.width * 0.18f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width * 0.18f, size.height)
                    close()
                }
                drawPath(p, c)
            }
        }
    }
}

@Composable
fun CueButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Idle.copy(alpha = if (enabled) 1f else 0.35f)
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.5.dp, c, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("CUE", color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

/**
 * Prato do deck: anel com marcador que gira com a faixa (33⅓ rpm × tempo) e, dentro,
 * tempo/decorrido/restante. Por enquanto só visual.
 */
@Composable
fun Platter(deck: DeckController, color: Color, modifier: Modifier = Modifier) {
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
                val ang = (deck.positionSec * 200.0 % 360.0) * PI / 180.0 - PI / 2
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
                Text(formatTempo(deck.tempoPercent), color = color, fontSize = big, fontWeight = FontWeight.Bold)
                if (deck.hasTrack) {
                    Text(formatTenths(deck.elapsedTenths), fontSize = small)
                    Text("-" + formatTenths(deck.durationTenths - deck.elapsedTenths), fontSize = small, color = Idle)
                } else {
                    Text("--:--", fontSize = small, color = Idle)
                }
            }
        }
    }
}

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
) {
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
                detectVerticalDragGestures { _, dy ->
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

/** Knob rotativo (0..1, 0,5 = neutro). Arraste vertical; toque duplo volta ao centro. */
@Composable
fun Knob(
    value: Float,
    onChange: (Float) -> Unit,
    color: Color,
    label: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(size)
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { change(0.5f) }) }
                .pointerInput(Unit) {
                    detectDragGestures { _, drag ->
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
        Text(label, fontSize = 11.sp, color = Idle)
    }
}

/** Medidor de nível em segmentos (verde → amarelo → vermelho). [level] é lido só no desenho. */
@Composable
fun LevelMeter(level: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier.width(10.dp).fillMaxHeight()) {
        val segments = 16
        val gap = 2f
        val segH = (size.height - gap * (segments - 1)) / segments
        // curva suave para que sinais fracos ainda apareçam
        val lit = (kotlin.math.sqrt(level().coerceIn(0f, 1f)) * segments).toInt()
        for (s in 0 until segments) {
            val on = s < lit
            val base = when {
                s >= segments - 2 -> Color(0xFFEF4444)
                s >= segments - 5 -> Color(0xFFFACC15)
                else -> Color(0xFF84CC16)
            }
            val y = size.height - (s + 1) * segH - s * gap
            drawRect(if (on) base else Color(0xFF232B35), Offset(0f, y), Size(size.width, segH))
        }
    }
}

internal fun formatTempo(p: Float): String {
    val t = kotlin.math.round(p * 10).toInt()
    val sign = if (t >= 0) "+" else "-"
    val a = kotlin.math.abs(t)
    return "$sign${a / 10}.${a % 10}%"
}

/** Décimos de segundo → m:ss.t */
internal fun formatTenths(tenths: Int): String {
    val t = tenths.coerceAtLeast(0)
    val s = (t / 10) % 60
    return "${t / 600}:${if (s < 10) "0" else ""}$s.${t % 10}"
}
