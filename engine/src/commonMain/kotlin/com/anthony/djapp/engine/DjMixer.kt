package com.anthony.djapp.engine

import kotlin.concurrent.Volatile

/** Mixer de 2 decks + crossfader + master. [render] é chamado pela thread de áudio. */
class DjMixer(val sampleRate: Int) {
    val deckA = Deck(sampleRate)
    val deckB = Deck(sampleRate)

    /** 0 = só A, 1 = só B. */
    @Volatile var crossfader: Float = 0.5f

    @Volatile var masterVolume: Float = 0.8f

    private companion object {
        /** Fase de compasso (em batidas) abaixo da qual o líder já está no tempo 1. */
        const val BAR_PHASE_EPSILON = 1e-6
    }

    /** Key lock nos dois decks: tempo sem mudar o tom. */
    var keyLock: Boolean = true
        set(value) {
            field = value
            deckA.keyLock = value
            deckB.keyLock = value
        }

    /** O que fazer com o seguidor ao alinhá-lo ao líder. A ordem importa: pedidos se fundem pelo maior. */
    enum class AlignMode {
        /** Só alinha a batida do seguidor (ele já toca, ou fica parado). */
        PhaseOnly,

        /** Alinha a batida e já larga, em qualquer batida. */
        StartOnBeat,

        /** Larga no tempo 1: parte de um início de compasso e entra no próximo início de compasso do líder. */
        StartOnBar,
    }

    private class AlignRequest(
        val follower: Deck,
        val leader: Deck,
        val followerGrid: BeatGrid,
        val leaderGrid: BeatGrid,
        val mode: AlignMode,
    )

    @Volatile private var pendingAlign: AlignRequest? = null

    /**
     * Pede para alinhar as batidas do [follower] às do [leader] e, conforme o [mode], largar o seguidor.
     * Quem resolve é a thread de áudio, no início do próximo bloco: as duas posições são lidas no mesmo
     * instante e o seguidor já renderiza esse bloco alinhado. Medir na UI e aplicar depois deixaria
     * um deslocamento de um bloco (20-40 ms) entre os decks.
     */
    fun requestAlign(
        follower: Deck,
        leader: Deck,
        followerGrid: BeatGrid,
        leaderGrid: BeatGrid,
        mode: AlignMode,
    ) {
        // um pedido anterior para largar o mesmo seguidor não pode se perder se outro chegar antes do bloco
        val prev = pendingAlign
        val merged = if (prev != null && prev.follower === follower) maxOf(mode, prev.mode) else mode
        pendingAlign = AlignRequest(follower, leader, followerGrid, leaderGrid, merged)
        if (merged == AlignMode.StartOnBar) follower.startPending = true
    }

    /** Descarta o alinhamento pedido e ainda não aplicado (ex.: tudo foi pausado antes do próximo bloco). */
    fun cancelAlign() {
        pendingAlign?.follower?.startPending = false
        pendingAlign = null
    }

    /**
     * Resolve um pedido de alinhamento no início de um bloco de [frames] frames. Devolve true se o pedido
     * acabou (aplicado ou inválido); false se segue esperando o tempo 1 do líder.
     */
    private fun applyAlign(r: AlignRequest, frames: Int): Boolean {
        val lt = r.leader.track ?: return true
        val ft = r.follower.track ?: return true
        if (r.mode == AlignMode.StartOnBar) return startOnBar(r, lt, ft, frames)
        val leaderPhase = r.leaderGrid.phaseAt(r.leader.effectivePositionFrames / lt.sampleRate)
        val followerPos = r.follower.effectivePositionFrames
        val followerPhase = r.followerGrid.phaseAt(followerPos / ft.sampleRate)
        val deltaFrames = beatPhaseDelta(leaderPhase, followerPhase) * 60.0 / r.followerGrid.bpm * ft.sampleRate
        val last = (ft.frames - 1).coerceAtLeast(0).toDouble()
        r.follower.seekToFrame((followerPos + deltaFrames).coerceIn(0.0, last))
        if (r.mode == AlignMode.StartOnBeat) r.follower.play()
        return true
    }

    /**
     * Largada no tempo 1: o seguidor parte do início de compasso mais próximo de onde estava e entra
     * **exatamente** no próximo início de compasso do líder (os dois tempos 1 coincidem, e portanto as
     * batidas também). Sem líder tocando, parte já. Enquanto espera devolve false e o seguidor fica parado.
     *
     * Sem atraso dentro do bloco: quando o tempo 1 do líder cai neste bloco, `k` frames adiante, o seguidor
     * começa no início do bloco já `k` frames antes do seu tempo 1, de modo que chegue nele no mesmo frame.
     * (Só fica um pouco adiantado se o tempo 1 do seguidor está a menos de `k` frames do início da faixa.)
     */
    private fun startOnBar(r: AlignRequest, lt: Track, ft: Track, frames: Int): Boolean {
        val follower = r.follower
        val barStart = r.followerGrid.nearestBarStart(follower.effectivePositionFrames / ft.sampleRate)
        var framesUntilDownbeat = 0.0
        if (r.leader.playing) {
            val barPhase = r.leaderGrid.barPhaseAt(r.leader.effectivePositionFrames / lt.sampleRate)
            if (barPhase > BAR_PHASE_EPSILON) {
                val beatsPerFrame = r.leaderGrid.bpm / 60.0 * r.leader.tempo / sampleRate
                framesUntilDownbeat = (BeatGrid.BEATS_PER_BAR - barPhase) / beatsPerFrame
                if (framesUntilDownbeat >= frames) return false // o tempo 1 do líder ainda não cai neste bloco
            }
        }
        val posStep = ft.sampleRate.toDouble() / sampleRate * follower.tempo // frames da faixa por frame de saída
        val last = (ft.frames - 1).coerceAtLeast(0).toDouble()
        follower.seekToFrame((barStart * ft.sampleRate - framesUntilDownbeat * posStep).coerceIn(0.0, last))
        follower.startPending = false
        follower.play()
        return true
    }

    /** Solta o PCM de faixas liberadas que o key lock ainda segurava; só com a thread de áudio parada. */
    fun dropStaleAudio() {
        deckA.dropStaleStretchSource()
        deckB.dropStaleStretchSource()
    }

    /** Preenche [out] com [frames] frames estéreo intercalados (tamanho >= frames*2). */
    fun render(out: FloatArray, frames: Int) {
        val align = pendingAlign
        if (align != null && applyAlign(align, frames) && pendingAlign === align) pendingAlign = null
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
