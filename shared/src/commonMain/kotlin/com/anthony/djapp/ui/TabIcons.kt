package com.anthony.djapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Ícones da barra inferior, desenhados em Canvas (sem dependência de pacote de ícones). */
@Composable
fun TabIcon(kind: TabIconKind, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val sw = w * 0.1f
        when (kind) {
            TabIconKind.Waves -> {
                // barras de onda de alturas variadas
                val heights = floatArrayOf(0.3f, 0.65f, 1f, 0.55f, 0.85f, 0.4f, 0.2f)
                val step = w / heights.size
                heights.forEachIndexed { i, f ->
                    val x = step * (i + 0.5f)
                    drawLine(
                        color,
                        Offset(x, h / 2f - h * f / 2f),
                        Offset(x, h / 2f + h * f / 2f),
                        strokeWidth = sw * 1.2f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            TabIconKind.Mixer -> {
                // três faders: trilho vertical + cursor em alturas diferentes
                val knobs = floatArrayOf(0.3f, 0.65f, 0.45f)
                knobs.forEachIndexed { i, f ->
                    val x = w * (0.2f + 0.3f * i)
                    drawLine(color.copy(alpha = 0.55f), Offset(x, h * 0.08f), Offset(x, h * 0.92f), strokeWidth = sw, cap = StrokeCap.Round)
                    val y = h * f
                    drawLine(color, Offset(x - w * 0.12f, y), Offset(x + w * 0.12f, y), strokeWidth = sw * 2.2f, cap = StrokeCap.Round)
                }
            }
            TabIconKind.KeyLock, TabIconKind.KeyUnlock -> {
                // cadeado: corpo + argola (fechada ou aberta)
                val bodyTop = h * 0.46f
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.18f, bodyTop),
                    size = Size(w * 0.64f, h * 0.46f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f, w * 0.1f),
                )
                val r = w * 0.2f
                val cx = w / 2f
                val shackle = Path().apply {
                    if (kind == TabIconKind.KeyLock) {
                        moveTo(cx - r, bodyTop)
                        lineTo(cx - r, h * 0.3f)
                        arcTo(androidx.compose.ui.geometry.Rect(cx - r, h * 0.3f - r, cx + r, h * 0.3f + r), 180f, 180f, false)
                        lineTo(cx + r, bodyTop)
                    } else {
                        moveTo(cx + r, bodyTop - h * 0.04f)
                        lineTo(cx + r, h * 0.3f)
                        arcTo(
                            androidx.compose.ui.geometry.Rect(cx - r + w * 0.3f, h * 0.3f - r, cx + r + w * 0.3f, h * 0.3f + r),
                            180f,
                            180f,
                            false,
                        )
                        lineTo(cx + r + w * 0.3f, h * 0.38f)
                    }
                }
                drawPath(shackle, color, style = Stroke(sw * 1.3f, cap = StrokeCap.Round))
            }
        }
    }
}

/** Qual ícone desenhar em [TabIcon]. */
enum class TabIconKind { Waves, Mixer, KeyLock, KeyUnlock }
