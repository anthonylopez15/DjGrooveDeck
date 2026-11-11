package com.anthony.djapp.engine

/** Mixer de 2 decks + crossfader + master. [render] é chamado pela thread de áudio. */
class DjMixer(val sampleRate: Int) {
    val deckA = Deck(sampleRate)
    val deckB = Deck(sampleRate)

    /** 0 = só A, 1 = só B. */
    @Volatile var crossfader: Float = 0.5f
    @Volatile var masterVolume: Float = 0.8f

    /** Key lock nos dois decks: tempo sem mudar o tom. */
    var keyLock: Boolean = true
        set(value) {
            field = value
            deckA.keyLock = value
            deckB.keyLock = value
        }

    private class AlignRequest(
        val follower: Deck,
        val leader: Deck,
        val followerGrid: BeatGrid,
        val leaderGrid: BeatGrid,
        val startFollower: Boolean,
    )

    @Volatile private var pendingAlign: AlignRequest? = null

    /**
     * Pede para alinhar as batidas do [follower] às do [leader] (e, se [startFollower], começar a tocar).
     * Quem resolve é a thread de áudio, no início do próximo bloco: as duas posições são lidas no mesmo
     * instante e o seguidor já renderiza esse bloco alinhado. Medir na UI e aplicar depois deixaria
     * um deslocamento de um bloco (20-40 ms) entre os decks.
     */
    fun requestAlign(
        follower: Deck,
        leader: Deck,
        followerGrid: BeatGrid,
        leaderGrid: BeatGrid,
        startFollower: Boolean,
    ) {
        // um pedido anterior para largar o mesmo seguidor não pode se perder se outro chegar antes do bloco
        val prev = pendingAlign
        val start = startFollower || (prev != null && prev.follower === follower && prev.startFollower)
        pendingAlign = AlignRequest(follower, leader, followerGrid, leaderGrid, start)
    }

    private fun applyAlign(r: AlignRequest) {
        val lt = r.leader.track ?: return
        val ft = r.follower.track ?: return
        val leaderPhase = r.leaderGrid.phaseAt(r.leader.effectivePositionFrames / lt.sampleRate)
        val followerPos = r.follower.effectivePositionFrames
        val followerPhase = r.followerGrid.phaseAt(followerPos / ft.sampleRate)
        val deltaFrames = beatPhaseDelta(leaderPhase, followerPhase) * 60.0 / r.followerGrid.bpm * ft.sampleRate
        val last = (ft.frames - 1).coerceAtLeast(0).toDouble()
        r.follower.seekToFrame((followerPos + deltaFrames).coerceIn(0.0, last))
        if (r.startFollower) r.follower.play()
    }

    /** Solta o PCM de faixas liberadas que o key lock ainda segurava; só com a thread de áudio parada. */
    fun dropStaleAudio() {
        deckA.dropStaleStretchSource()
        deckB.dropStaleStretchSource()
    }

    /** Preenche [out] com [frames] frames estéreo intercalados (tamanho >= frames*2). */
    fun render(out: FloatArray, frames: Int) {
        val align = pendingAlign
        if (align != null) {
            pendingAlign = null
            applyAlign(align)
        }
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
