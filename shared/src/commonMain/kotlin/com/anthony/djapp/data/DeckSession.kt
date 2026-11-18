package com.anthony.djapp.data

import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.domain.LibraryEntry
import com.anthony.djapp.domain.SyncRules
import com.anthony.djapp.engine.Deck
import com.anthony.djapp.engine.Eq3
import com.anthony.djapp.engine.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Um deck da sessão: aplica as mudanças no [Deck] do motor e no estado. Carregar, tocar e ajustar ficam
 * aqui; restaurar depois que o processo morre e liberar memória estão em `DeckSessionLifecycle.kt`.
 */
class DeckSession internal constructor(
    val id: DeckId,
    private val deck: Deck,
    private val store: MutableStateFlow<MixState>,
) {
    /** Estado atual deste deck. */
    val state: DeckState get() = store.value.deck(id)

    internal fun update(f: (DeckState) -> DeckState) = store.update { it.withDeck(id, f(it.deck(id))) }

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
    internal fun view() = state.view(playingNow, deck.startPending)

    internal fun syncFromEngine() {
        val p = deck.playing
        val w = deck.startPending
        if (state.playing != p || state.startPending != w) update { it.copy(playing = p, startPending = w) }
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

    // ---------- transporte ----------

    /**
     * Põe a posição no início de compasso (tempo 1) mais próximo, pela grade do deck (as linhas laranja da
     * onda). Sem grade, não mexe.
     */
    internal fun snapToBar() {
        val grid = state.beatGrid() ?: return
        val rate = state.track?.sampleRate ?: return
        deck.seekToFrame(grid.nearestBarStart(deck.effectivePositionFrames / rate) * rate)
    }

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

    /**
     * Esvazia o deck (faixa, origem, restauração pendente, andamento, SYNC): deck como recém-aberto. Volume e
     * EQ ficam como estão. false se o deck toca.
     */
    internal fun unload(): Boolean {
        if (!deck.unload()) return false
        update {
            it.copy(
                track = null,
                source = null,
                pendingRestore = null,
                audioReleased = false,
                loadingName = null,
                loadProgress = 0f,
                bpm = null,
                firstBeatSec = 0.0,
                syncOn = false,
                tempoPercent = 0f,
                tempoBeforeSync = 0f,
                playing = false,
            )
        }
        return true
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
