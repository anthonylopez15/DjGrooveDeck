package com.anthony.djapp.domain

import kotlin.math.abs

/** Os dois decks do app. */
enum class DeckId {
    A,
    B,
    ;

    val other: DeckId get() = if (this == A) B else A
}

/** Aparência do botão SYNC: apagado, ligado sem travar (contorno) ou travado nas batidas (cheio). */
enum class SyncState { Off, Armed, Locked }

/**
 * O que as regras precisam saber de um deck, num instante. A camada de apresentação monta esta "foto"
 * a partir do estado real e executa a decisão que as regras devolvem.
 */
data class DeckView(
    /** BPM da faixa na velocidade original; null sem faixa ou sem ritmo detectado. */
    val bpm: Double?,
    /** Ajuste de tempo em %. */
    val tempoPercent: Float,
    val syncOn: Boolean,
    val playing: Boolean,
) {
    /** BPM com o ajuste de tempo aplicado. */
    val effectiveBpm: Double? get() = bpm?.let { it * (1.0 + tempoPercent / 100.0) }
}

/** O que fazer ao tocar no botão SYNC de um deck. */
sealed interface SyncDecision {
    /**
     * Desliga o SYNC deste deck e devolve o tempo que ele tinha antes de sincronizar. Se o outro deck
     * também segue e está tocando ([rematchOther]), o outro passa a seguir o novo andamento deste.
     */
    data class TurnOff(val rematchOther: Boolean) : SyncDecision

    /** Tudo travado e o toque foi no líder: desfaz o vínculo (o outro desliga o SYNC e fica onde está). */
    data object BreakLink : SyncDecision

    /**
     * Liga o SYNC: com o deck tocando, iguala o andamento já ([matchNow]); com os dois tocando, também
     * alinha as batidas ([lockNow]).
     */
    data class TurnOn(val matchNow: Boolean, val lockNow: Boolean) : SyncDecision

    /** Não dá para sincronizar (falta BPM em algum deck). */
    data object Ignore : SyncDecision
}

/** O que fazer ao tocar no play/pause de um deck. */
sealed interface PlayDecision {
    data object Pause : PlayDecision

    data object Start : PlayDecision

    /**
     * Largada alinhada: o deck começa no andamento e na batida do outro, que já está tocando. Se
     * [thisFollows], este deck iguala o andamento do outro; senão o outro (que segue este) iguala o deste.
     */
    data class AlignedStart(val thisFollows: Boolean) : PlayDecision
}

/**
 * Regras do SYNC e do transporte, no modelo do djay. Puras: sem estado, sem Compose, sem plataforma.
 */
object SyncRules {
    /** Limite do ajuste de tempo (±%). */
    const val MAX_TEMPO_PERCENT = 20f

    /** Passo dos botões − e + do andamento (%). */
    const val TEMPO_STEP_PERCENT = 1f

    /** Diferença máxima de BPM efetivo para considerar os decks no mesmo andamento. */
    const val BPM_MATCH_TOLERANCE = 0.05

    /** Mesmo BPM efetivo nos dois decks (o número fica azul no prato). */
    fun bpmMatched(a: DeckView, b: DeckView): Boolean {
        val x = a.effectiveBpm ?: return false
        val y = b.effectiveBpm ?: return false
        return abs(x - y) < BPM_MATCH_TOLERANCE
    }

    /** Batidas travadas de fato: houve encaixe, os dois tocam e algum segue o outro. */
    fun locked(phaseLocked: Boolean, a: DeckView, b: DeckView): Boolean =
        phaseLocked && a.playing && b.playing && (a.syncOn || b.syncOn)

    fun syncState(me: DeckView, locked: Boolean): SyncState = when {
        locked -> SyncState.Locked
        me.syncOn -> SyncState.Armed
        else -> SyncState.Off
    }

    /** O botão SYNC responde: já ligado, travado (para desfazer) ou com BPM nos dois decks. */
    fun canToggleSync(me: DeckView, other: DeckView, locked: Boolean): Boolean =
        me.syncOn || locked || (me.bpm != null && other.bpm != null)

    /**
     * Tempo (%) para o [follower] ter o BPM efetivo do [leader], limitado a ±[MAX_TEMPO_PERCENT];
     * null se falta BPM em algum deles.
     */
    fun followerTempo(follower: DeckView, leader: DeckView): Float? {
        val fb = follower.bpm ?: return null
        val lb = leader.effectiveBpm ?: return null
        return ((lb / fb - 1.0) * 100.0).toFloat().coerceIn(-MAX_TEMPO_PERCENT, MAX_TEMPO_PERCENT)
    }

    /**
     * Pode travar as batidas: os dois têm BPM e o andamento efetivo já é o mesmo. Decks distantes demais
     * (fora dos ±20%) não casam e não devem aparecer "travados".
     */
    fun canLockBeats(follower: DeckView, leader: DeckView): Boolean =
        follower.bpm != null && leader.bpm != null && bpmMatched(follower, leader)

    fun toggleSync(me: DeckView, other: DeckView, locked: Boolean): SyncDecision = when {
        me.syncOn -> SyncDecision.TurnOff(rematchOther = other.syncOn && other.playing)
        locked && other.syncOn -> SyncDecision.BreakLink
        me.bpm == null || other.bpm == null -> SyncDecision.Ignore
        else -> SyncDecision.TurnOn(matchNow = me.playing, lockNow = me.playing && other.playing)
    }

    fun togglePlay(me: DeckView, other: DeckView): PlayDecision = when {
        me.playing -> PlayDecision.Pause
        (me.syncOn || other.syncOn) && other.playing && me.bpm != null && other.bpm != null ->
            PlayDecision.AlignedStart(thisFollows = me.syncOn)
        else -> PlayDecision.Start
    }

    /** Depois de voltar ao início: com os dois tocando e SYNC, realinha as batidas. */
    fun relockAfterRestart(me: DeckView, other: DeckView): Boolean =
        me.playing && other.playing && (me.syncOn || other.syncOn)

    /** Regra do app: não troca a faixa de um deck que está tocando. */
    fun canReplaceTrack(me: DeckView): Boolean = !me.playing
}
