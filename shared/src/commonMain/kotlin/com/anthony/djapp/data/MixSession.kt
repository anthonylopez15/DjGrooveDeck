package com.anthony.djapp.data

import com.anthony.djapp.domain.AudioSystem
import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.domain.DeckSnapshot
import com.anthony.djapp.domain.Interruption
import com.anthony.djapp.domain.InterruptionPolicy
import com.anthony.djapp.domain.InterruptionReaction
import com.anthony.djapp.domain.LibraryEntry
import com.anthony.djapp.domain.PlatformEvent
import com.anthony.djapp.domain.PlayDecision
import com.anthony.djapp.domain.SessionSnapshot
import com.anthony.djapp.domain.SessionStore
import com.anthony.djapp.domain.SyncDecision
import com.anthony.djapp.domain.SyncRules
import com.anthony.djapp.engine.Deck
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.Eq3
import com.anthony.djapp.engine.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A sessão de DJ: mixer, decks e o estado deles ([state], imutável). Vive enquanto o processo vive
 * (não com a tela), para a música seguir em segundo plano e tudo estar como antes ao voltar.
 *
 * As decisões (SYNC, play, interrupções) são do domínio ([SyncRules], [InterruptionPolicy]); aqui se
 * executa a decisão no motor e no estado. Métodos que mudam estado devem ser chamados na thread
 * principal, exceto [DeckSession.updateLoadProgress] (o decodificador avisa de outra thread).
 *
 * Depende só de portas do domínio para falar com a plataforma: [AudioSystem] (pedir para tocar e
 * receber eventos) e [SessionStore] (estado guardado entre processos).
 */
class MixSession(
    private val mixer: DjMixer,
    private val audio: AudioSystem,
    private val sessionStore: SessionStore,
) {
    private val store = MutableStateFlow(MixState())

    /** Estado atual da sessão. */
    val state: StateFlow<MixState> = store.asStateFlow()

    val a = DeckSession(DeckId.A, mixer.deckA, store)
    val b = DeckSession(DeckId.B, mixer.deckB, store)

    fun deck(id: DeckId): DeckSession = if (id == DeckId.A) a else b

    /** Algum deck tocando agora (estado real do motor). */
    val anyPlaying: Boolean get() = a.playingNow || b.playingNow

    init {
        // processo novo: volta ao que estava (os decks são recarregados quando a tela aparecer)
        sessionStore.load()?.let(::restore)
        audio.setListener(::onPlatformEvent)
    }

    private fun update(f: (MixState) -> MixState) = store.update(f)

    // ---------- mixer ----------

    fun changeCrossfader(x: Float) {
        val v = x.coerceIn(0f, 1f)
        mixer.crossfader = v
        update { it.copy(crossfader = v) }
    }

    fun changeMaster(x: Float) {
        val v = x.coerceIn(0f, 1f)
        mixer.masterVolume = v
        update { it.copy(master = v) }
    }

    /** Key lock: o andamento muda sem mudar o tom (desligado = toca-discos). */
    fun toggleKeyLock() {
        val v = !store.value.keyLock
        mixer.keyLock = v
        update { it.copy(keyLock = v) }
    }

    // ---------- tela, avisos, memória, disco ----------

    /**
     * Houve pedido de memória com a tela fora de cena: decks que pararem depois (pausa pela notificação,
     * interrupção, fim da faixa) também soltam o áudio. Volta a false quando a tela reaparece.
     */
    private var memoryPressureInBackground = false

    /** A tela entrou/saiu de cena. */
    fun setForeground(visible: Boolean) {
        if (visible) memoryPressureInBackground = false
        update { it.copy(foreground = visible) }
    }

    /** A apresentação já mostrou o aviso. */
    fun consumeNotice() = update { it.copy(notice = null) }

    /**
     * Solta o áudio dos decks parados (o sistema pediu memória). Devolve quantos soltou. Com a saída de
     * áudio desligada ninguém mais solta o PCM que o key lock segurava: solta aqui também.
     */
    fun releaseIdleAudio(): Int {
        val released = listOf(a, b).count { it.releaseAudio() }
        if (!audio.outputRunning) mixer.dropStaleAudio()
        return released
    }

    /** Guarda o estado no [SessionStore]. */
    fun save() = sessionStore.save(snapshot())

    /**
     * Evento detectado pela plataforma ([AudioSystem]). A decisão é daqui (e do domínio), igual em
     * Android e iOS: interrupções seguem a [InterruptionPolicy]; falta de memória com a tela fora de
     * cena guarda o estado (o processo pode ser encerrado logo) e solta o áudio dos decks parados.
     */
    fun onPlatformEvent(event: PlatformEvent) {
        when (event) {
            is PlatformEvent.Interrupted -> interrupt(event.kind)
            PlatformEvent.MemoryPressure -> if (!store.value.foreground) {
                memoryPressureInBackground = true
                save()
                releaseIdleAudio() // decks tocando não podem soltar agora; soltam quando pararem
            }
        }
    }

    /**
     * Trata uma interrupção e guarda o estado se a reação pedir. Em segundo plano, depois de um pedido de
     * memória, os decks que pararam agora soltam o áudio (todo deck que para fora da tela passa por aqui).
     */
    fun interrupt(kind: Interruption) {
        if (onInterruption(kind).persist) save()
        if (memoryPressureInBackground && !store.value.foreground) releaseIdleAudio()
    }

    /** Estado atual para guardar em disco. */
    fun snapshot(): SessionSnapshot {
        val s = store.value
        return SessionSnapshot(a.snapshot(), b.snapshot(), s.crossfader, s.master, s.keyLock)
    }

    /**
     * Volta ao estado guardado depois que o processo foi reiniciado: mixer na hora; os decks ficam
     * marcados para recarregar os arquivos (pausados, na mesma posição) quando a tela aparecer.
     */
    fun restore(s: SessionSnapshot) {
        changeCrossfader(s.crossfader)
        changeMaster(s.master)
        if (store.value.keyLock != s.keyLock) toggleKeyLock()
        s.a?.let(a::scheduleRestore)
        s.b?.let(b::scheduleRestore)
    }

    /** Reflete no estado o que o motor mudou sozinho (ex.: a largada alinhada dá play na thread de áudio). */
    fun syncFromEngine() {
        a.syncFromEngine()
        b.syncFromEngine()
    }

    // ---------- regras ----------

    /** Regra do app: a faixa de um deck tocando não é trocada. */
    fun canReplaceTrack(id: DeckId): Boolean = SyncRules.canReplaceTrack(deck(id).view())

    /** Carrega a faixa no deck (só pausado); qualquer encaixe de batidas anterior se desfaz. */
    fun loadTrack(id: DeckId, track: Track, source: LibraryEntry? = null): Boolean {
        if (!deck(id).load(track, source)) return false
        update { it.copy(phaseLocked = false) }
        return true
    }

    /** SYNC, como no djay (regras em [SyncRules.toggleSync]). */
    fun toggleSync(id: DeckId) {
        val me = deck(id)
        val o = deck(id.other)
        when (val d = SyncRules.toggleSync(me.view(), o.view(), store.value.locked)) {
            is SyncDecision.TurnOff -> {
                me.setSync(false)
                me.restoreTempoBeforeSync()
                unlock()
                if (d.rematchOther) matchTempo(follower = o, leader = me)
            }
            SyncDecision.BreakLink -> {
                o.setSync(false)
                unlock()
            }
            is SyncDecision.TurnOn -> {
                me.setSync(true)
                if (d.matchNow) matchTempo(follower = me, leader = o)
                if (d.lockNow) lockBeats(follower = me, leader = o)
            }
            SyncDecision.Ignore -> Unit
        }
    }

    /**
     * Play/pause (regras em [SyncRules.togglePlay]). Antes de começar, a plataforma pode negar
     * ([AudioSystem.requestPlayback]). Pausar desfaz o encaixe (o SYNC continua ligado, só sem travar).
     */
    fun togglePlay(id: DeckId) {
        val me = deck(id)
        val o = deck(id.other)
        val decision = SyncRules.togglePlay(me.view(), o.view())
        val ms = me.state
        val startsWithAudio = decision != PlayDecision.Pause && ms.hasTrack && !ms.audioReleased
        if (startsWithAudio && !audio.requestPlayback()) {
            interrupt(Interruption.PlaybackDenied)
            return
        }
        when (decision) {
            PlayDecision.Pause -> {
                me.pause()
                unlock()
            }
            PlayDecision.Start -> me.play()
            is PlayDecision.AlignedStart -> {
                if (decision.thisFollows) matchTempo(follower = me, leader = o) else matchTempo(follower = o, leader = me)
                // largada quantizada: o motor alinha e começa no mesmo bloco; sem encaixe possível, só toca
                if (!lockBeats(follower = me, leader = o, startFollower = true)) me.play()
            }
        }
    }

    /** Volta ao início da faixa; com SYNC e o outro tocando, cai de novo na batida certa. */
    fun restart(id: DeckId) {
        val me = deck(id)
        val o = deck(id.other)
        me.restart()
        if (SyncRules.relockAfterRestart(me.view(), o.view())) lockBeats(follower = me, leader = o)
    }

    /**
     * Evento de fora da tela (fone desconectado, outro app, ligação, "Pausar" da notificação...). A
     * reação é do domínio ([InterruptionPolicy]); devolve-a para a plataforma guardar o estado se pedido.
     */
    fun onInterruption(kind: Interruption): InterruptionReaction {
        val reaction = InterruptionPolicy.reactTo(kind, anyPlaying)
        if (reaction.pauseAll) pauseAll()
        if (reaction.notify) noticeFor(kind)?.let { msg -> update { it.copy(notice = msg) } }
        return reaction
    }

    private fun noticeFor(kind: Interruption): String? = when (kind) {
        Interruption.OutputDisconnected -> "Saída de áudio desconectada: música pausada"
        Interruption.OtherAppPlaying -> "Música pausada: outro app começou a tocar"
        Interruption.Transient -> "Música pausada (ligação ou outro áudio)"
        Interruption.PlaybackDenied -> "Não dá para tocar agora (ligação em andamento?)"
        Interruption.PauseRequested -> null
    }

    /**
     * Pausa os dois decks: desfaz o encaixe de batidas e descarta uma largada alinhada ainda pendente no
     * motor, que senão faria o seguidor tocar depois da pausa.
     */
    fun pauseAll() {
        mixer.cancelAlign()
        a.pause()
        b.pause()
        unlock()
    }

    private fun unlock() = update { it.copy(phaseLocked = false) }

    /**
     * Pede ao motor para alinhar as batidas do [follower] às do [leader] (e, se [startFollower], começar
     * a tocar no mesmo bloco), se as regras deixarem ([SyncRules.canLockBeats]). Devolve se pediu.
     */
    private fun lockBeats(follower: DeckSession, leader: DeckSession, startFollower: Boolean = false): Boolean {
        if (!SyncRules.canLockBeats(follower.view(), leader.view())) return false
        val followerGrid = follower.state.beatGrid() ?: return false
        val leaderGrid = leader.state.beatGrid() ?: return false
        mixer.requestAlign(follower.engineDeck, leader.engineDeck, followerGrid, leaderGrid, startFollower)
        update { it.copy(phaseLocked = true) }
        return true
    }

    /** Ajusta o tempo do [follower] para o BPM efetivo do [leader] ([SyncRules.followerTempo]). */
    private fun matchTempo(follower: DeckSession, leader: DeckSession) {
        SyncRules.followerTempo(follower.view(), leader.view())?.let(follower::changeTempo)
    }
}

/** Um deck da sessão: aplica as mudanças no [Deck] do motor e no estado. */
class DeckSession internal constructor(
    val id: DeckId,
    private val deck: Deck,
    private val store: MutableStateFlow<MixState>,
) {
    /** Estado atual deste deck. */
    val state: DeckState get() = store.value.deck(id)

    private fun update(f: (DeckState) -> DeckState) = store.update { it.withDeck(id, f(it.deck(id))) }

    /** Deck do motor, para o mixer alinhar as batidas na thread de áudio. */
    internal val engineDeck: Deck get() = deck

    /** Estado real do motor (sem esperar o espelho no [state]). */
    val playingNow: Boolean get() = deck.playing

    /** Posição atual em segundos, lida do motor. */
    val positionSec: Double
        get() {
            val t = deck.track ?: return 0.0
            return deck.positionFrames / t.sampleRate
        }

    /** Nível de pico (0..1) para o medidor, lido do motor. */
    val level: Float get() = deck.level

    /** "Foto" para as regras, com o estado real do motor (tocando agora). */
    internal fun view() = state.view(playingNow)

    internal fun syncFromEngine() {
        val p = deck.playing
        if (state.playing != p) update { it.copy(playing = p) }
    }

    // ---------- carregamento ----------

    fun startLoading(name: String) = update { it.copy(loadingName = name, loadProgress = 0f) }

    /** Pode ser chamado de outra thread (o decodificador avisa o progresso). */
    fun updateLoadProgress(p: Float) = update { it.copy(loadProgress = p.coerceIn(0f, 1f)) }

    fun cancelLoading() = update { it.copy(loadingName = null) }

    /** false se o deck está tocando (regra: não troca a faixa enquanto toca). */
    fun load(track: Track, source: LibraryEntry? = null): Boolean {
        if (!deck.load(track)) return false
        deck.tempo = 1.0
        update {
            it.copy(
                track = track,
                source = source,
                pendingRestore = null,
                audioReleased = false,
                loadingName = null,
                bpm = track.beatGrid?.bpm,
                firstBeatSec = track.beatGrid?.firstBeatSec ?: 0.0,
                syncOn = false,
                tempoPercent = 0f,
                playing = deck.playing,
            )
        }
        return true
    }

    // ---------- restauração após o processo ser encerrado ----------

    /** Marca o deck para ser recarregado como estava em [snap]. */
    fun scheduleRestore(snap: DeckSnapshot) {
        if (state.track == null) update { it.copy(pendingRestore = snap) }
    }

    /** Estado para guardar em disco; null se não há faixa com origem conhecida. */
    fun snapshot(): DeckSnapshot? {
        val s = state
        return s.pendingRestore ?: snapshotOfLoaded(s) // ainda não recarregado: guarda o que veio do disco
    }

    private fun snapshotOfLoaded(s: DeckState): DeckSnapshot? {
        val src = s.source ?: return null
        val t = s.track ?: return null
        return DeckSnapshot(
            source = src,
            positionSec = deck.effectivePositionFrames / t.sampleRate,
            tempoPercent = s.tempoPercent,
            tempoBeforeSync = s.tempoBeforeSync,
            volume = s.volume,
            low = s.low,
            mid = s.mid,
            high = s.high,
            syncOn = s.syncOn,
        )
    }

    /** Carrega [reloaded] e devolve o deck ao estado de [snap] (pausado). false se não coube. */
    fun applyRestore(reloaded: Track, snap: DeckSnapshot): Boolean {
        if (state.pendingRestore !== snap || !load(reloaded, snap.source)) return false
        changeTempo(snap.tempoPercent)
        changeVolume(snap.volume)
        changeLow(snap.low)
        changeMid(snap.mid)
        changeHigh(snap.high)
        update { it.copy(syncOn = snap.syncOn, tempoBeforeSync = snap.tempoBeforeSync) }
        val frames = (snap.positionSec * reloaded.sampleRate).coerceIn(0.0, (reloaded.frames - 1).coerceAtLeast(0).toDouble())
        deck.seekToFrame(frames)
        return true
    }

    /** Desiste de recarregar (arquivo sumiu, por exemplo): o deck fica vazio. */
    fun dropRestore() = update { it.copy(pendingRestore = null) }

    // ---------- memória ----------

    /**
     * Solta o PCM decodificado (dezenas de MB) mantendo tudo o mais; só com o deck parado e a origem
     * conhecida. Devolve se soltou.
     */
    fun releaseAudio(): Boolean {
        if (state.source == null || state.audioReleased || !deck.releaseAudio()) return false
        update { it.copy(track = deck.track, audioReleased = true) } // cópia leve: duração, onda e grade
        return true
    }

    /** Devolve o áudio recarregado de [DeckState.source]; o deck volta como estava. */
    fun restoreAudio(reloaded: Track): Boolean {
        if (!state.audioReleased || !deck.restoreAudio(reloaded.pcm)) return false
        update { it.copy(track = deck.track, audioReleased = false) }
        return true
    }

    // ---------- transporte ----------

    internal fun play() {
        deck.play()
        syncFromEngine()
    }

    /** Pausa já, refletindo no estado sem esperar o próximo quadro. */
    fun pause() {
        deck.pause()
        syncFromEngine()
    }

    fun restart() = deck.restart()

    fun seek(fraction: Float) = deck.seekToFraction(fraction)

    /** Arrastar a onda ampliada: move a posição em [deltaSec] segundos. */
    fun scrubBySeconds(deltaSec: Double) {
        val d = state.durationSec
        if (d <= 0.0) return
        val target = (positionSec + deltaSec).coerceIn(0.0, d)
        deck.seekToFraction((target / d).toFloat())
    }

    // ---------- ajustes ----------

    /** Liga/desliga o SYNC; ao ligar, guarda o tempo atual para voltar a ele ao desligar. */
    internal fun setSync(on: Boolean) = update {
        if (on && !it.syncOn) it.copy(syncOn = true, tempoBeforeSync = it.tempoPercent) else it.copy(syncOn = on)
    }

    /** Desligar o SYNC pelo botão devolve o deck à velocidade que tinha antes de sincronizar. */
    internal fun restoreTempoBeforeSync() = changeTempo(state.tempoBeforeSync)

    fun changeTempo(p: Float) {
        val v = p.coerceIn(-SyncRules.MAX_TEMPO_PERCENT, SyncRules.MAX_TEMPO_PERCENT)
        deck.tempo = 1.0 + v / 100.0
        update { it.copy(tempoPercent = v) }
    }

    fun changeVolume(v: Float) {
        val x = v.coerceIn(0f, 1f)
        deck.volume = x
        update { it.copy(volume = x) }
    }

    fun changeLow(k: Float) {
        val x = k.coerceIn(0f, 1f)
        deck.lowDb = Eq3.knobToDb(x)
        update { it.copy(low = x) }
    }

    fun changeMid(k: Float) {
        val x = k.coerceIn(0f, 1f)
        deck.midDb = Eq3.knobToDb(x)
        update { it.copy(mid = x) }
    }

    fun changeHigh(k: Float) {
        val x = k.coerceIn(0f, 1f)
        deck.highDb = Eq3.knobToDb(x)
        update { it.copy(high = x) }
    }
}
