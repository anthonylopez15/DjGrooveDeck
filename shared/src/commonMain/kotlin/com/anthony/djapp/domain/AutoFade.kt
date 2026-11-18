package com.anthony.djapp.domain

import kotlin.math.abs

/**
 * Regra do "auto fade": com o botão ligado, se o crossfader está todo do lado de um deck que toca, ele
 * vai sozinho, de forma suave, até a ponta do outro deck (passa a música de um deck para o outro).
 */
object AutoFade {
    /** Tempo para percorrer o crossfader inteiro, de uma ponta à outra. */
    const val GLIDE_MS = 5_000L

    /** O fade vale no último quarto de cada lado: até 25% (lado A) ou a partir de 75% (lado B). */
    const val ZONE = 0.25f

    /** Maior intervalo contado entre dois passos: depois de uma pausa (tela fora de cena) não dá salto. */
    const val MAX_STEP_MS = 100L

    /** O deck do lado do crossfader toca e ele está na zona desse lado: pode começar a passar. */
    fun shouldStart(x: Float, aPlaying: Boolean, bPlaying: Boolean): Boolean =
        (x <= ZONE && aPlaying) || (x >= 1f - ZONE && bPlaying)

    /** A ponta oposta: de quem está do lado de A (0) vai a B (1) e vice-versa. */
    fun targetFor(x: Float): Float = if (x < 0.5f) 1f else 0f

    /** Próxima posição depois de [dtMs], andando para [target] sem passar dele. */
    fun next(x: Float, target: Float, dtMs: Long): Float {
        val move = dtMs.coerceIn(0L, MAX_STEP_MS).toFloat() / GLIDE_MS
        return when {
            abs(target - x) <= move -> target
            x < target -> x + move
            else -> x - move
        }
    }
}
