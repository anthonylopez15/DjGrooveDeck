package com.anthony.djapp.data

import com.anthony.djapp.domain.AudioSystem
import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.domain.DeckSnapshot
import com.anthony.djapp.domain.IdlePolicy
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
import com.anthony.djapp.domain.SyncStart
import com.anthony.djapp.engine.Deck
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.Eq3
import com.anthony.djapp.engine.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

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
 *
 * Organização (um recurso novo entra no arquivo da sua responsabilidade):
 * - `MixSession.kt` (este): mixer, regras de SYNC e transporte dos dois decks;
 * - `MixSessionLifecycle.kt`: salvar/restaurar a sessão, pedido de memória e interrupções;
 * - `DeckSession.kt` e `DeckSessionLifecycle.kt`: um deck (carregar, tocar, ajustes; restaurar e memória).
 */
class MixSession(
    internal val mixer: DjMixer,
    internal val audio: AudioSystem,
    internal val sessionStore: SessionStore,
) {
    internal val store = MutableStateFlow(MixState())

    /** Estado atual da sessão. */
    val state: StateFlow<MixState> = store.asStateFlow()

    val a = DeckSession(DeckId.A, mixer.deckA, store)
    val b = DeckSession(DeckId.B, mixer.deckB, store)

    fun deck(id: DeckId): DeckSession = if (id == DeckId.A) a else b

    /** Algum deck tocando agora (estado real do motor). */
    val anyPlaying: Boolean get() = a.playingNow || b.playingNow

    /** Relógio (ms de época); os testes trocam por um falso. */
    internal var nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() }

    /** Último momento de "uso" (estado guardado); com ele se decide se o app ficou sem uso ([IdlePolicy]). */
    internal var lastActivityMs: Long = nowMs()

    init {
        // processo novo: volta ao que estava, a menos que tenha ficado sem uso (aí abre limpo)
        sessionStore.load()?.let(::restoreOrStartClean)
        audio.setListener(::onPlatformEvent)
    }

    internal fun update(f: (MixState) -> MixState) = store.update(f)

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

    fun setSyncStart(mode: SyncStart) = update { it.copy(syncStart = mode) }

    // ---------- tela e avisos (salvar, restaurar, memória e eventos da plataforma: MixSessionLifecycle.kt) ----------

    /**
     * Houve pedido de memória com a tela fora de cena: decks que pararem depois (pausa pela notificação,
     * interrupção, fim da faixa) também soltam o áudio. Volta a false quando a tela reaparece.
     */
    internal var memoryPressureInBackground = false

    /** A tela entrou/saiu de cena. */
    fun setForeground(visible: Boolean) {
        if (visible) {
            memoryPressureInBackground = false
            audio.setIdleTimeout(null)
            clearDecksIfIdle() // voltou depois de mais de 30 min sem uso: abre limpo
        } else {
            audio.setIdleTimeout(IdlePolicy.LIMIT_MS)
        }
        update { it.copy(foreground = visible) }
    }

    /** A apresentação já mostrou o aviso. */
    fun consumeNotice() = update { it.copy(notice = null) }

    /** Reflete no estado o que o motor mudou sozinho (ex.: a largada alinhada dá play na thread de áudio). */
    fun syncFromEngine() {
        a.syncFromEngine()
        b.syncFromEngine()
    }

    // ---------- regras ----------

    /** Regra do app: a faixa de um deck tocando não é trocada. */
    fun canReplaceTrack(id: DeckId): Boolean = SyncRules.canReplaceTrack(deck(id).view())

    /**
     * Andamento manual do deck [id] (%, limitado a ±[SyncRules.MAX_TEMPO_PERCENT]). Como no djay: mexer no
     * andamento de um deck que **segue** o outro desliga o SYNC dele; mexer no de quem **lidera** faz o
     * seguidor acompanhar (e as batidas se alinham de novo se os dois tocam). O encaixe de batidas antigo
     * deixa de valer.
     */
    fun setTempo(id: DeckId, percent: Float) {
        val me = deck(id)
        val o = deck(id.other)
        if (!me.state.hasTrack) return
        if (me.state.syncOn) me.setSync(false)
        me.changeTempo(percent)
        unlock()
        if (o.state.syncOn && o.playingNow) {
            matchTempo(follower = o, leader = me)
            if (me.playingNow) lockBeats(follower = o, leader = me)
        }
    }

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
        val startsWithAudio = decision != PlayDecision.Pause && decision != PlayDecision.CancelStart &&
            ms.hasTrack && !ms.audioReleased
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
            PlayDecision.CancelStart -> {
                mixer.cancelAlign()
                me.syncFromEngine()
            }
            PlayDecision.StartOnBar -> {
                me.snapToBar()
                me.play()
            }
            is PlayDecision.AlignedStart -> {
                if (decision.thisFollows) matchTempo(follower = me, leader = o) else matchTempo(follower = o, leader = me)
                // largada quantizada: o motor alinha e começa no mesmo bloco; sem encaixe possível, só toca
                if (!lockBeats(follower = me, leader = o, startFollower = true)) {
                    me.snapToBar() // sem encaixe possível (andamentos longe demais), ainda larga num tempo 1
                    me.play()
                }
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
        // largada (play com SYNC): no tempo 1; sem ser largada (ligar o SYNC tocando): só alinha a batida
        val mode = when {
            !startFollower -> DjMixer.AlignMode.PhaseOnly
            else -> when (store.value.syncStart) {
                SyncStart.Bar -> DjMixer.AlignMode.StartOnBar
                SyncStart.Beat -> DjMixer.AlignMode.StartOnNextBeat
                SyncStart.Now -> DjMixer.AlignMode.StartOnBeat
            }
        }
        mixer.requestAlign(follower.engineDeck, leader.engineDeck, followerGrid, leaderGrid, mode)
        update { it.copy(phaseLocked = true) }
        return true
    }

    /** Ajusta o tempo do [follower] para o BPM efetivo do [leader] ([SyncRules.followerTempo]). */
    private fun matchTempo(follower: DeckSession, leader: DeckSession) {
        SyncRules.followerTempo(follower.view(), leader.view())?.let(follower::changeTempo)
    }
}
