package com.anthony.djapp.data

import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.domain.DeckSnapshot
import com.anthony.djapp.domain.DeckView
import com.anthony.djapp.domain.LibraryEntry
import com.anthony.djapp.domain.SyncRules
import com.anthony.djapp.domain.SyncState
import com.anthony.djapp.engine.BeatGrid
import com.anthony.djapp.engine.Track

/**
 * Estado de um deck na sessão (imutável). Posição e nível do medidor não estão aqui: mudam a cada
 * quadro e são lidos direto do motor (ver ADR 0001, regra 5).
 */
data class DeckState(
    /** Faixa carregada; numa faixa liberada ([audioReleased]) só restam duração, onda e grade. */
    val track: Track? = null,
    /** De onde veio a faixa (para recarregar e para guardar o estado). */
    val source: LibraryEntry? = null,
    /** BPM na velocidade original; null sem faixa ou sem ritmo detectado. */
    val bpm: Double? = null,
    /** Posição (s) da primeira batida da grade. */
    val firstBeatSec: Double = 0.0,
    /** Ajuste de tempo em % (±[SyncRules.MAX_TEMPO_PERCENT]). */
    val tempoPercent: Float = 0f,
    /** Tempo de antes de ligar o SYNC, para voltar a ele ao desligar. */
    val tempoBeforeSync: Float = 0f,
    val volume: Float = 1f,
    val low: Float = 0.5f,
    val mid: Float = 0.5f,
    val high: Float = 0.5f,
    /** Seguindo o BPM do outro deck. */
    val syncOn: Boolean = false,
    /** Tocando (espelho do motor, atualizado quando muda). */
    val playing: Boolean = false,
    /** Nome da faixa em decodificação; null sem carregamento em curso. */
    val loadingName: String? = null,
    /** Progresso 0..1 da decodificação. */
    val loadProgress: Float = 0f,
    /** Áudio solto por falta de memória: precisa recarregar de [source] antes de tocar. */
    val audioReleased: Boolean = false,
    /** Faixa a recarregar depois que o processo foi reiniciado. */
    val pendingRestore: DeckSnapshot? = null,
) {
    val hasTrack: Boolean get() = track != null
    val loading: Boolean get() = loadingName != null
    val title: String get() = track?.name ?: ""
    val durationSec: Double get() = track?.durationSec ?: 0.0

    /** BPM com o ajuste de tempo aplicado. */
    val effectiveBpm: Double? get() = view().effectiveBpm

    /** Grade de batidas (BPM e primeira batida), ou null sem BPM. */
    fun beatGrid(): BeatGrid? = bpm?.let { BeatGrid(it, firstBeatSec) }

    /** "Foto" para as regras do domínio; [playingNow] permite usar o estado real do motor. */
    fun view(playingNow: Boolean = playing): DeckView = DeckView(bpm, tempoPercent, syncOn, playingNow)
}

/** Estado da sessão de DJ: os dois decks e o que é do mixer. */
data class MixState(
    val a: DeckState = DeckState(),
    val b: DeckState = DeckState(),
    val crossfader: Float = 0.5f,
    val master: Float = 0.8f,
    val keyLock: Boolean = true,
    /** Encaixe de batidas feito pelo SYNC; pausar, desligar o SYNC ou trocar a faixa desfaz. */
    val phaseLocked: Boolean = false,
    /** A tela está à vista (os decks só são recarregados do arquivo com ela à vista). */
    val foreground: Boolean = true,
    /** Aviso vindo de fora da tela (fone desconectado...); a apresentação mostra e consome. */
    val notice: String? = null,
) {
    fun deck(id: DeckId): DeckState = if (id == DeckId.A) a else b

    fun withDeck(id: DeckId, d: DeckState): MixState = if (id == DeckId.A) copy(a = d) else copy(b = d)

    /** Os dois decks tocando, com SYNC e batidas travadas: os dois botões ficam cheios. */
    val locked: Boolean get() = SyncRules.locked(phaseLocked, a.view(), b.view())

    /** BPM efetivo igual nos dois decks (o número fica azul). */
    val bpmMatched: Boolean get() = SyncRules.bpmMatched(a.view(), b.view())

    fun syncState(id: DeckId): SyncState = SyncRules.syncState(deck(id).view(), locked)

    fun canToggleSync(id: DeckId): Boolean = SyncRules.canToggleSync(deck(id).view(), deck(id.other).view(), locked)
}
