package com.anthony.djapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
