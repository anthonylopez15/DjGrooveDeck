package com.anthony.djapp.engine

/** Mixer de 2 decks + crossfader + master. [render] é chamado pela thread de áudio. */
class DjMixer(val sampleRate: Int) {
    val deckA = Deck(sampleRate)
    val deckB = Deck(sampleRate)

    /** 0 = só A, 1 = só B. */
    @Volatile var crossfader: Float = 0.5f
    @Volatile var masterVolume: Float = 0.8f

    /** Preenche [out] com [frames] frames estéreo intercalados (tamanho >= frames*2). */
    fun render(out: FloatArray, frames: Int) {
        out.fill(0f, 0, frames * 2)
        val x = crossfader
        deckA.renderAdd(out, frames, Crossfader.gainA(x))
        deckB.renderAdd(out, frames, Crossfader.gainB(x))

        val master = masterVolume
        for (i in 0 until frames * 2) {
            out[i] = softClip(out[i] * master)
        }
    }

    /** Limita em ±1 sem estourar: linear até 0,9 e curva suave acima. */
    private fun softClip(x: Float): Float {
        val a = if (x < 0f) -x else x
        if (a <= 0.9f) return x
        val over = (a - 0.9f) / 0.1f
        val y = 0.9f + 0.1f * (over / (1f + over))
        return if (x < 0f) -y else y
    }
}
