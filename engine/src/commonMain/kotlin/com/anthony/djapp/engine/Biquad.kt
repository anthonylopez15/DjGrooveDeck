package com.anthony.djapp.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Filtro biquad estéreo (formas do "Audio EQ Cookbook" de R. Bristow-Johnson),
 * forma transposta direta II. Sem alocação no caminho de áudio.
 */
class Biquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private var z1L = 0.0
    private var z2L = 0.0
    private var z1R = 0.0
    private var z2R = 0.0

    fun processL(x: Float): Float {
        val y = b0 * x + z1L
        z1L = b1 * x - a1 * y + z2L
        z2L = b2 * x - a2 * y
        return y.toFloat()
    }

    fun processR(x: Float): Float {
        val y = b0 * x + z1R
        z1R = b1 * x - a1 * y + z2R
        z2R = b2 * x - a2 * y
        return y.toFloat()
    }

    fun setLowShelf(fs: Int, f0: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f0 / fs
        val cw = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0) // slope S = 1
        val t = 2.0 * sqrt(a) * alpha
        set(
            b0 = a * ((a + 1) - (a - 1) * cw + t),
            b1 = 2 * a * ((a - 1) - (a + 1) * cw),
            b2 = a * ((a + 1) - (a - 1) * cw - t),
            a0 = (a + 1) + (a - 1) * cw + t,
            a1 = -2 * ((a - 1) + (a + 1) * cw),
            a2 = (a + 1) + (a - 1) * cw - t,
        )
    }

    fun setHighShelf(fs: Int, f0: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f0 / fs
        val cw = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)
        val t = 2.0 * sqrt(a) * alpha
        set(
            b0 = a * ((a + 1) + (a - 1) * cw + t),
            b1 = -2 * a * ((a - 1) + (a + 1) * cw),
            b2 = a * ((a + 1) + (a - 1) * cw - t),
            a0 = (a + 1) - (a - 1) * cw + t,
            a1 = 2 * ((a - 1) - (a + 1) * cw),
            a2 = (a + 1) - (a - 1) * cw - t,
        )
    }

    fun setPeaking(fs: Int, f0: Double, q: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * f0 / fs
        val cw = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        set(
            b0 = 1 + alpha * a,
            b1 = -2 * cw,
            b2 = 1 - alpha * a,
            a0 = 1 + alpha / a,
            a1 = -2 * cw,
            a2 = 1 - alpha / a,
        )
    }

    private fun set(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
        this.b0 = b0 / a0
        this.b1 = b1 / a0
        this.b2 = b2 / a0
        this.a1 = a1 / a0
        this.a2 = a2 / a0
    }
}
