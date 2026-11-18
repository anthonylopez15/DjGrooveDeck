package com.anthony.djapp.data

import com.anthony.djapp.domain.DeckSnapshot
import com.anthony.djapp.engine.Track

// Ciclo de vida de um deck (extensões de [DeckSession]): restaurar a faixa depois que o processo foi
// encerrado e soltar/devolver o áudio quando o sistema pede memória.

/** Marca o deck para ser recarregado como estava em [snap]. */
fun DeckSession.scheduleRestore(snap: DeckSnapshot) {
    if (state.track == null) update { it.copy(pendingRestore = snap) }
}

/** Estado para guardar em disco; null se não há faixa com origem conhecida. */
fun DeckSession.snapshot(): DeckSnapshot? {
    val s = state
    return s.pendingRestore ?: snapshotOfLoaded(s) // ainda não recarregado: guarda o que veio do disco
}

private fun DeckSession.snapshotOfLoaded(s: DeckState): DeckSnapshot? {
    val src = s.source ?: return null
    val t = s.track ?: return null
    return DeckSnapshot(
        source = src,
        positionSec = engineDeck.effectivePositionFrames / t.sampleRate,
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
fun DeckSession.applyRestore(reloaded: Track, snap: DeckSnapshot): Boolean {
    if (state.pendingRestore !== snap || !load(reloaded, snap.source)) return false
    changeTempo(snap.tempoPercent)
    changeVolume(snap.volume)
    changeLow(snap.low)
    changeMid(snap.mid)
    changeHigh(snap.high)
    update { it.copy(syncOn = snap.syncOn, tempoBeforeSync = snap.tempoBeforeSync) }
    val frames = (snap.positionSec * reloaded.sampleRate).coerceIn(0.0, (reloaded.frames - 1).coerceAtLeast(0).toDouble())
    engineDeck.seekToFrame(frames)
    return true
}

/** Desiste de recarregar (arquivo sumiu, por exemplo): o deck fica vazio. */
fun DeckSession.dropRestore() = update { it.copy(pendingRestore = null) }

// ---------- memória ----------

/**
 * Solta o PCM decodificado (dezenas de MB) mantendo tudo o mais; só com o deck parado e a origem
 * conhecida. Devolve se soltou.
 */
fun DeckSession.releaseAudio(): Boolean {
    if (state.source == null || state.audioReleased || !engineDeck.releaseAudio()) return false
    update { it.copy(track = engineDeck.track, audioReleased = true) } // cópia leve: duração, onda e grade
    return true
}

/** Devolve o áudio recarregado de [DeckState.source]; o deck volta como estava. */
fun DeckSession.restoreAudio(reloaded: Track): Boolean {
    if (!state.audioReleased || !engineDeck.restoreAudio(reloaded.pcm)) return false
    update { it.copy(track = engineDeck.track, audioReleased = false) }
    return true
}
