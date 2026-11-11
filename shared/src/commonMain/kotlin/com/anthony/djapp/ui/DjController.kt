package com.anthony.djapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.anthony.djapp.engine.BeatGrid
import com.anthony.djapp.engine.Deck
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.Eq3
import com.anthony.djapp.engine.Track
import com.anthony.djapp.engine.beatPhaseDelta
import kotlin.math.abs

/** Estado observável (Compose) de um deck, ligado ao [Deck] do motor. */
class DeckController(private val deck: Deck) {
    var track by mutableStateOf<Track?>(null)
        private set
    var playing by mutableStateOf(false)
        private set

    /** Posição em segundos, atualizada a cada frame. Leia só em fase de desenho (Canvas). */
    var positionSec by mutableDoubleStateOf(0.0)
        private set

    /** Nível de pico (0..1) para o medidor, a cada frame. Leia só em fase de desenho. */
    var level by mutableFloatStateOf(0f)
        private set

    /** Posição em décimos de segundo: muda 10x/s, segura para textos. */
    var elapsedTenths by mutableIntStateOf(0)
        private set

    /** Ajuste de tempo em % (-16..+16). */
    var tempoPercent by mutableFloatStateOf(0f)
        private set
    var volume by mutableFloatStateOf(1f)
        private set
    var low by mutableFloatStateOf(0.5f)
        private set
    var mid by mutableFloatStateOf(0.5f)
        private set
    var high by mutableFloatStateOf(0.5f)
        private set

    /** Nome da faixa em decodificação, ou null se não há carregamento em curso. */
    var loadingName by mutableStateOf<String?>(null)
        private set

    /** Progresso 0..1 da decodificação em curso. */
    var loadProgress by mutableFloatStateOf(0f)
        private set

    val loading: Boolean get() = loadingName != null

    fun startLoading(name: String) {
        loadProgress = 0f
        loadingName = name
    }

    fun updateLoadProgress(p: Float) {
        loadProgress = p.coerceIn(0f, 1f)
    }

    fun cancelLoading() {
        loadingName = null
    }

    /** BPM da faixa em velocidade original; null se não detectado. */
    var bpm by mutableStateOf<Double?>(null)
        private set

    /** Posição (s) da primeira batida da grade. */
    var firstBeatSec by mutableDoubleStateOf(0.0)
        private set

    /** BPM efetivo, já com o ajuste de tempo. */
    val effectiveBpm: Double? get() = bpm?.let { it * (1.0 + tempoPercent / 100.0) }

    /** Deck seguindo o BPM do outro (SYNC ligado). */
    var syncOn by mutableStateOf(false)
        private set

    private var tempoBeforeSync = 0f

    /** Liga/desliga o sync; ao ligar, guarda o tempo atual para poder voltar a ele ao desligar. */
    fun setSync(on: Boolean) {
        if (on && !syncOn) tempoBeforeSync = tempoPercent
        syncOn = on
    }

    /** Desligar o SYNC pelo botão devolve o deck à velocidade que tinha antes de sincronizar. */
    fun restoreTempoBeforeSync() = changeTempo(tempoBeforeSync)

    /** Estado real do motor (sem esperar o próximo quadro de tela). */
    val playingNow: Boolean get() = deck.playing

    /** Fase da batida (0..1) na posição atual do motor, ou null sem BPM. */
    fun beatPhaseNow(): Double? {
        val t = deck.track ?: return null
        val b = bpm ?: return null
        return BeatGrid(b, firstBeatSec).phaseAt(deck.positionFrames / t.sampleRate)
    }

    /** Anda [deltaBeats] batidas (para alinhar com o outro deck). */
    fun nudgeBeats(deltaBeats: Double) {
        val t = deck.track ?: return
        val b = bpm ?: return
        val frames = deltaBeats * 60.0 / b * t.sampleRate
        deck.seekToFrame((deck.positionFrames + frames).coerceIn(0.0, (t.frames - 1).coerceAtLeast(0).toDouble()))
    }

    val hasTrack: Boolean get() = track != null
    val title: String get() = track?.name ?: ""
    val durationSec: Double get() = track?.durationSec ?: 0.0
    val durationTenths: Int get() = (durationSec * 10).toInt()

    fun progress(): Float {
        val d = durationSec
        return if (d > 0.0) (positionSec / d).toFloat().coerceIn(0f, 1f) else 0f
    }

    /** false se o deck está tocando (regra: não troca a faixa enquanto toca). */
    fun load(track: Track): Boolean {
        if (!deck.load(track)) return false
        this.track = track
        loadingName = null
        bpm = track.beatGrid?.bpm
        firstBeatSec = track.beatGrid?.firstBeatSec ?: 0.0
        syncOn = false
        changeTempo(0f)
        poll()
        return true
    }

    fun togglePlay() = deck.togglePlay()
    fun restart() = deck.restart()
    fun seek(fraction: Float) = deck.seekToFraction(fraction)

    /** Arrastar a onda ampliada: move a posição em [deltaSec] segundos. */
    fun scrubBySeconds(deltaSec: Double) {
        val d = durationSec
        if (d <= 0.0) return
        val target = (positionSec + deltaSec).coerceIn(0.0, d)
        positionSec = target
        deck.seekToFraction((target / d).toFloat())
    }

    fun changeTempo(p: Float) {
        tempoPercent = p.coerceIn(-16f, 16f)
        deck.tempo = 1.0 + tempoPercent / 100.0
    }

    fun changeVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        deck.volume = volume
    }

    fun changeLow(k: Float) {
        low = k.coerceIn(0f, 1f)
        deck.lowDb = Eq3.knobToDb(low)
    }

    fun changeMid(k: Float) {
        mid = k.coerceIn(0f, 1f)
        deck.midDb = Eq3.knobToDb(mid)
    }

    fun changeHigh(k: Float) {
        high = k.coerceIn(0f, 1f)
        deck.highDb = Eq3.knobToDb(high)
    }

    /** Chamado a cada frame pela UI para refletir a posição vinda da thread de áudio. */
    fun poll() {
        val t = deck.track
        playing = deck.playing
        level = deck.level
        if (t != null) {
            val s = deck.positionFrames / t.sampleRate
            positionSec = s
            elapsedTenths = (s * 10).toInt()
        } else {
            positionSec = 0.0
            elapsedTenths = 0
        }
    }
}

class DjController(private val mixer: DjMixer) {
    val a = DeckController(mixer.deckA)
    val b = DeckController(mixer.deckB)

    var crossfader by mutableFloatStateOf(0.5f)
        private set
    var master by mutableFloatStateOf(0.8f)
        private set

    fun deck(id: DeckId): DeckController = if (id == DeckId.A) a else b
    private fun other(id: DeckId): DeckController = if (id == DeckId.A) b else a

    /**
     * Encaixe de batidas: true quando as batidas dos dois decks foram alinhadas pelo SYNC.
     * Pausar um deck, desligar o SYNC ou carregar outra faixa desfaz.
     */
    private var phaseLocked by mutableStateOf(false)

    /** Os dois decks tocando, com SYNC ligado e as batidas travadas: os dois botões ficam cheios. */
    val locked: Boolean
        get() = phaseLocked && a.playing && b.playing && (a.syncOn || b.syncOn)

    /** BPM efetivo igual nos dois decks (o número fica azul no prato). */
    val bpmMatched: Boolean
        get() {
            val x = a.effectiveBpm ?: return false
            val y = b.effectiveBpm ?: return false
            return abs(x - y) < 0.05
        }

    /** Aparência do botão SYNC do deck [id]: apagado, contorno (ligado, sem travar) ou cheio (travado). */
    fun syncState(id: DeckId): SyncState = when {
        locked -> SyncState.Locked
        deck(id).syncOn -> SyncState.Armed
        else -> SyncState.Off
    }

    fun canToggleSync(id: DeckId): Boolean =
        deck(id).syncOn || locked || (deck(id).bpm != null && other(id).bpm != null)

    /** Carrega a faixa no deck (só pausado); qualquer encaixe de batidas anterior se desfaz. */
    fun loadTrack(id: DeckId, track: Track): Boolean {
        if (!deck(id).load(track)) return false
        phaseLocked = false
        return true
    }

    /**
     * SYNC, como no djay:
     * - **Liga**: o deck passa a seguir o BPM do outro (se estiver tocando, já iguala o andamento e,
     *   com o outro tocando, alinha as batidas e trava). Parado, espera o play.
     * - **Desliga** o próprio SYNC: volta ao andamento que tinha antes.
     * - Tocar no botão do **líder** (aceso só pelo vínculo) com tudo travado desfaz o vínculo e
     *   cada deck fica no andamento em que está.
     */
    fun toggleSync(id: DeckId) {
        val me = deck(id)
        val o = other(id)
        when {
            me.syncOn -> {
                me.setSync(false)
                me.restoreTempoBeforeSync()
                phaseLocked = false
                if (o.syncOn && o.playingNow) matchTempo(follower = o, leader = me)
            }
            locked && o.syncOn -> {
                o.setSync(false)
                phaseLocked = false
            }
            me.bpm == null || o.bpm == null -> Unit
            else -> {
                me.setSync(true)
                if (me.playingNow) {
                    matchTempo(follower = me, leader = o)
                    if (o.playingNow) lockBeats(follower = me, leader = o)
                }
            }
        }
    }

    /**
     * Play/pause. Dar play num deck com SYNC (ou com o outro seguindo) enquanto o outro toca faz a
     * largada alinhada: o seguidor entra no andamento do outro e na batida certa, e as batidas travam.
     * Pausar desfaz o encaixe (o SYNC continua ligado, só sem travar).
     */
    fun togglePlay(id: DeckId) {
        val me = deck(id)
        val o = other(id)
        val starting = !me.playingNow
        if (starting && (me.syncOn || o.syncOn) && o.playingNow && me.bpm != null && o.bpm != null) {
            if (me.syncOn) matchTempo(follower = me, leader = o) else matchTempo(follower = o, leader = me)
            lockBeats(follower = me, leader = o)
            me.togglePlay()
        } else {
            me.togglePlay()
            if (!starting) phaseLocked = false
        }
    }

    /** Volta ao início da faixa; com SYNC e o outro tocando, cai de novo na batida certa. */
    fun restart(id: DeckId) {
        val me = deck(id)
        val o = other(id)
        me.restart()
        if (me.playingNow && o.playingNow && (me.syncOn || o.syncOn)) lockBeats(follower = me, leader = o)
    }

    /** Alinha as batidas do [follower] às do [leader] (caminho mais curto) e marca o encaixe. */
    private fun lockBeats(follower: DeckController, leader: DeckController) {
        val pl = leader.beatPhaseNow() ?: return
        val pf = follower.beatPhaseNow() ?: return
        follower.nudgeBeats(beatPhaseDelta(pl, pf))
        phaseLocked = true
    }

    /** Ajusta o tempo do [follower] para que o BPM efetivo dele seja o do [leader] (limitado a ±16%). */
    private fun matchTempo(follower: DeckController, leader: DeckController) {
        val fb = follower.bpm ?: return
        val lb = leader.effectiveBpm ?: return
        follower.changeTempo(((lb / fb - 1.0) * 100.0).toFloat())
    }

    fun changeCrossfader(x: Float) {
        crossfader = x.coerceIn(0f, 1f)
        mixer.crossfader = crossfader
    }

    /** Key lock: o andamento muda sem mudar o tom (desligado = toca-discos). */
    var keyLock by mutableStateOf(true)
        private set

    fun toggleKeyLock() {
        keyLock = !keyLock
        mixer.keyLock = keyLock
    }

    fun changeMaster(v: Float) {
        master = v.coerceIn(0f, 1f)
        mixer.masterVolume = master
    }

    fun poll() {
        a.poll()
        b.poll()
    }
}

enum class DeckId { A, B }

/** Aparência do botão SYNC: apagado, ligado sem travar (contorno) ou travado nas batidas (cheio). */
enum class SyncState { Off, Armed, Locked }
