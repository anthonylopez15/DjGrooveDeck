package com.anthony.djapp.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Crossfader de potência constante: 0 = só deck A, 1 = só deck B. */
object Crossfader {
    fun gainA(position: Float): Float = cos(position.coerceIn(0f, 1f) * (PI / 2).toFloat())
    fun gainB(position: Float): Float = sin(position.coerceIn(0f, 1f) * (PI / 2).toFloat())
}
