package com.anthony.djapp.engine

/**
 * Um deck: toca uma [Track] com tempo variável (varispeed, como um toca-discos:
 * mudar o tempo também muda o tom), EQ de 3 bandas e volume.
 *
 * Threading: a UI escreve os campos @Volatile; a thread de áudio só lê e,
 * em [renderAdd], escreve a posição. Nada aqui aloca memória durante o render.
 */
class Deck(private val outRate: Int) {
    @Volatile var track: Track? = null
        private set
    @Volatile var playing: Boolean = false
        private set
    @Volatile var positionFrames: Double = 0.0
        private set
    @Volatile var cueFrame: Double = 0.0
        private set

    /** 1.0 = velocidade original. */
    @Volatile var tempo: Double = 1.0
    @Volatile var volume: Float = 1f
    @Volatile var lowDb: Double = 0.0
    @Volatile var midDb: Double = 0.0
    @Volatile var highDb: Double = 0.0

    @Volatile private var pendingSeek: Double = -1.0
    private val eq = Eq3(outRate)
    private var lastGain = 0f

    fun load(t: Track) {
        playing = false
        track = t
        positionFrames = 0.0
        cueFrame = 0.0
        pendingSeek = 0.0
    }

    fun play() {
        if (track != null) playing = true
    }

    fun pause() {
        playing = false
    }

    fun togglePlay() {
        if (playing) pause() else play()
    }

    /** Comportamento de CDJ: parado define o cue; tocando volta ao cue e para. */
    fun cue() {
        if (playing) {
            playing = false
            seekToFrame(cueFrame)
        } else {
            cueFrame = positionFrames
        }
    }

    fun seekToFraction(fraction: Float) {
        val t = track ?: return
        seekToFrame(fraction.coerceIn(0f, 1f) * (t.frames - 1).coerceAtLeast(0).toDouble())
    }

    private fun seekToFrame(frame: Double) {
        pendingSeek = frame
        positionFrames = frame
    }

    /**
     * Soma [frames] frames deste deck em [out] (estéreo intercalado), já com EQ e
     * ganho = volume * [mixGain] (crossfader). O ganho é rampeado dentro do buffer
     * para evitar "zipper noise".
     */
    fun renderAdd(out: FloatArray, frames: Int, mixGain: Float) {
        var pos = positionFrames
        val seek = pendingSeek
        if (seek >= 0.0) {
            pos = seek
            pendingSeek = -1.0
        }

        val t = track
        val target = volume * mixGain
        if (t == null || !playing) {
            positionFrames = pos
            lastGain = target
            return
        }

        val ratio = t.sampleRate.toDouble() / outRate * tempo
        val pcm = t.pcm
        val last = t.frames - 1
        val scale = 1f / 32768f
        val g0 = lastGain
        eq.update(lowDb, midDb, highDb)

        for (i in 0 until frames) {
            val idx = pos.toInt()
            if (idx >= last) {
                playing = false
                pos = 0.0
                break
            }
            val frac = (pos - idx).toFloat()
            val l0 = pcm[2 * idx]
            val r0 = pcm[2 * idx + 1]
            val l = (l0 + (pcm[2 * idx + 2] - l0) * frac) * scale
            val r = (r0 + (pcm[2 * idx + 3] - r0) * frac) * scale
            val g = g0 + (target - g0) * ((i + 1).toFloat() / frames)
            out[2 * i] += eq.processL(l) * g
            out[2 * i + 1] += eq.processR(r) * g
            pos += ratio
        }

        lastGain = target
        positionFrames = pos
    }
}
