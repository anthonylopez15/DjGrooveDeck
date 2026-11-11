package com.anthony.djapp.engine

import kotlin.math.ceil

/**
 * Um deck: toca uma [Track] com tempo variável, EQ de 3 bandas e volume.
 *
 * Com [keyLock] ligado (padrão) o tempo muda sem mudar o tom, via time-stretch ([WsolaStretcher]).
 * Desligado, vale o modo toca-discos: acelerar também sobe o tom.
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

    /** 1.0 = velocidade original. */
    @Volatile var tempo: Double = 1.0
    @Volatile var volume: Float = 1f
    @Volatile var lowDb: Double = 0.0
    @Volatile var midDb: Double = 0.0
    @Volatile var highDb: Double = 0.0

    /** Mantém o tom ao mudar o tempo (time-stretch). */
    @Volatile var keyLock: Boolean = true

    /**
     * Busca pedida pela UI: objeto imutável publicado numa única escrita volátil. A thread de áudio
     * só compara a identidade com a última aplicada ([appliedSeek]) e nunca "zera" o campo, então uma
     * busca nova postada no meio de um bloco jamais é apagada.
     */
    private class SeekRequest(val frame: Double)

    @Volatile private var seekRef: SeekRequest? = null
    @Volatile private var appliedSeek: SeekRequest? = null
    private val eq = Eq3(outRate)
    private var lastGain = 0f

    // estado do caminho com key lock (só a thread de áudio mexe)
    private val stretcher = WsolaStretcher()
    private var stretchSource: Track? = null
    private var stretchActive = false
    private var spos = 0.0 // posição de leitura (fracionária) dentro da fila do stretcher

    /** Pico recente (0..1) após EQ e ganho, com queda suave; alimenta o medidor de nível. */
    @Volatile var level: Float = 0f
        private set

    /**
     * Carrega [t] no deck. Regra: não troca a faixa de um deck que está tocando; devolve false
     * (sem mudar nada) nesse caso, e o chamador deve pedir para pausar antes.
     */
    fun load(t: Track): Boolean {
        if (playing) return false
        track = t
        positionFrames = 0.0
        seekRef = SeekRequest(0.0)
        return true
    }

    fun play() {
        val t = track
        if (t != null && !t.released) playing = true
    }

    /**
     * Solta o áudio da faixa (só pausado), guardando posição, tempo, EQ etc.; [restoreAudio] devolve.
     * Devolve false se o deck toca ou não há áudio a soltar.
     */
    fun releaseAudio(): Boolean {
        val t = track ?: return false
        if (playing || t.released) return false
        track = t.releasedCopy() // o stretcher solta a referência antiga na thread de áudio (render)
        return true
    }

    /**
     * Solta o PCM antigo que o stretcher ainda segura depois de [releaseAudio]. A thread de áudio faz
     * isso sozinha no render; chamar diretamente só com ela parada ([AudioOutput.running] = false).
     */
    fun dropStaleStretchSource() {
        val t = track
        if (stretchSource != null && stretchSource !== t && (t == null || t.released)) {
            stretcher.setSource(ShortArray(0))
            stretchSource = null
            stretchActive = false
        }
    }

    /** Devolve o áudio de uma faixa liberada (mesma música, mesmo tamanho). false se não cabe. */
    fun restoreAudio(pcm: ShortArray): Boolean {
        val t = track ?: return false
        if (!t.released || pcm.size / 2 != t.frames) return false
        track = t.withPcm(pcm)
        return true
    }

    fun pause() {
        playing = false
    }

    fun togglePlay() {
        if (playing) pause() else play()
    }

    /** Volta ao início da faixa, mantendo o estado de tocando/pausado. */
    fun restart() {
        seekToFrame(0.0)
    }

    fun seekToFraction(fraction: Float) {
        val t = track ?: return
        seekToFrame(fraction.coerceIn(0f, 1f) * (t.frames - 1).coerceAtLeast(0).toDouble())
    }

    /**
     * Posição para quem decide em cima dela (SYNC, fase da batida): uma busca ainda não aplicada pela
     * thread de áudio vale mais que [positionFrames], que pode estar velho.
     */
    val effectivePositionFrames: Double
        get() {
            val s = seekRef
            return if (s != null && s !== appliedSeek) s.frame else positionFrames
        }

    /** Vai para [frame] (da faixa); a thread de áudio aplica no próximo bloco. */
    fun seekToFrame(frame: Double) {
        seekRef = SeekRequest(frame)
        positionFrames = frame
    }

    /**
     * Soma [frames] frames deste deck em [out] (estéreo intercalado), já com EQ e
     * ganho = volume * [mixGain] (crossfader). O ganho é rampeado dentro do buffer
     * para evitar "zipper noise".
     */
    fun renderAdd(out: FloatArray, frames: Int, mixGain: Float) {
        var pos = positionFrames
        val seek = seekRef
        if (seek != null && seek !== appliedSeek) {
            pos = seek.frame
            appliedSeek = seek
            stretchActive = false // busca: o stretcher recomeça daqui
        }

        val t = track
        val target = volume * mixGain
        if (t == null || !playing) {
            if (t != null && t.released) dropStaleStretchSource()
            if (seekRef === appliedSeek) positionFrames = pos
            lastGain = target
            level *= 0.8f
            return
        }

        val lock = keyLock
        val tempoNow = tempo
        val pcm = t.pcm
        val last = t.frames - 1
        val scale = 1f / 32768f
        val g0 = lastGain
        eq.update(lowDb, midDb, highDb)
        var pk = 0f

        // razão entre taxas (faixa → saída); sem key lock o tempo também entra aqui (toca-discos)
        val rateRatio = t.sampleRate.toDouble() / outRate
        val readStep = if (lock) rateRatio else rateRatio * tempoNow
        val posStep = rateRatio * tempoNow // avanço da posição na faixa por frame de saída

        if (lock) {
            if (t !== stretchSource) {
                stretcher.setSource(pcm)
                stretchSource = t
                stretchActive = false
            }
            if (!stretchActive) {
                stretcher.reset(pos)
                spos = 0.0
                stretchActive = true
            }
            stretcher.ensure(ceil(spos + frames * readStep).toInt() + 2, tempoNow)
        } else {
            stretchActive = false
        }

        for (i in 0 until frames) {
            val l: Float
            val r: Float
            if (lock) {
                if (pos >= last) {
                    playing = false
                    pos = 0.0
                    stretchActive = false
                    break
                }
                val idx = spos.toInt()
                val frac = (spos - idx).toFloat()
                val a = stretcher.left(idx)
                val b = stretcher.right(idx)
                l = a + (stretcher.left(idx + 1) - a) * frac
                r = b + (stretcher.right(idx + 1) - b) * frac
                spos += readStep
            } else {
                val idx = pos.toInt()
                if (idx >= last) {
                    playing = false
                    pos = 0.0
                    break
                }
                val frac = (pos - idx).toFloat()
                val l0 = pcm[2 * idx]
                val r0 = pcm[2 * idx + 1]
                l = (l0 + (pcm[2 * idx + 2] - l0) * frac) * scale
                r = (r0 + (pcm[2 * idx + 3] - r0) * frac) * scale
            }

            val g = g0 + (target - g0) * ((i + 1).toFloat() / frames)
            val outL = eq.processL(l) * g
            val outR = eq.processR(r) * g
            out[2 * i] += outL
            out[2 * i + 1] += outR
            val aL = if (outL < 0f) -outL else outL
            val aR = if (outR < 0f) -outR else outR
            if (aL > pk) pk = aL
            if (aR > pk) pk = aR
            pos += posStep
        }

        if (lock && stretchActive) {
            val consumed = spos.toInt()
            stretcher.consume(consumed)
            spos -= consumed
        }

        level = maxOf(pk, level * 0.9f)
        lastGain = target
        // se a UI pediu uma busca durante este bloco, ela vence: não sobrescreve com a posição velha
        if (seekRef === appliedSeek) positionFrames = pos
    }
}
