package com.anthony.djapp.data

import com.anthony.djapp.domain.AudioSystem
import com.anthony.djapp.domain.IdlePolicy
import com.anthony.djapp.domain.Interruption
import com.anthony.djapp.domain.InterruptionPolicy
import com.anthony.djapp.domain.PlatformEvent
import com.anthony.djapp.domain.SessionSnapshot
import com.anthony.djapp.domain.SessionStore

// Ciclo de vida da sessão (extensões de [MixSession]): salvar e restaurar o estado, pedido de memória e
// interrupções. "A plataforma detecta, o domínio decide": a plataforma emite [PlatformEvent] pela porta
// [AudioSystem]; a reação sai das regras do domínio.

/**
 * Solta o áudio dos decks parados (o sistema pediu memória). Devolve quantos soltou. Com a saída de
 * áudio desligada ninguém mais solta o PCM que o key lock segurava: solta aqui também.
 */
fun MixSession.releaseIdleAudio(): Int {
    val released = listOf(a, b).count { it.releaseAudio() }
    if (!audio.outputRunning) mixer.dropStaleAudio()
    return released
}

/** Guarda o estado no [SessionStore] e conta como "uso" ([IdlePolicy]). */
fun MixSession.save() {
    lastActivityMs = nowMs()
    sessionStore.save(snapshot())
}

/**
 * Evento detectado pela plataforma ([AudioSystem]). A decisão é daqui (e do domínio), igual em
 * Android e iOS: interrupções seguem a [InterruptionPolicy]; falta de memória com a tela fora de
 * cena guarda o estado (o processo pode ser encerrado logo) e solta o áudio dos decks parados.
 */
fun MixSession.onPlatformEvent(event: PlatformEvent) {
    when (event) {
        is PlatformEvent.Interrupted -> interrupt(event.kind)
        PlatformEvent.MemoryPressure -> if (!store.value.foreground) {
            memoryPressureInBackground = true
            save()
            releaseIdleAudio() // decks tocando não podem soltar agora; soltam quando pararem
        }
        PlatformEvent.IdleTimeout -> onIdleTimeout()
    }
}

/**
 * O temporizador de inatividade venceu (app fora da tela). Se já passou de 30 min sem uso e nada toca,
 * solta as faixas; senão (estava tocando, ou o uso foi mais recente) agenda de novo para o que falta.
 */
private fun MixSession.onIdleTimeout() {
    if (store.value.foreground) return
    val remaining = IdlePolicy.remainingMs(lastActivityMs, nowMs())
    if (remaining > 0 || anyPlaying) {
        audio.setIdleTimeout(if (remaining > 0) remaining else IdlePolicy.LIMIT_MS)
    } else {
        clearDecks(notify = false) // em segundo plano ninguém lê o aviso
        save()
    }
}

/**
 * Voltou à tela: se o app ficou mais de 30 min sem uso e nada toca, esvazia os decks (abre limpo) e avisa.
 * Com música tocando em segundo plano o "uso" é renovado a cada gravação do estado, então não esvazia.
 */
internal fun MixSession.clearDecksIfIdle() {
    if (anyPlaying || !IdlePolicy.isStale(lastActivityMs, nowMs())) return
    if (clearDecks(notify = true)) save()
}

/**
 * Esvazia os dois decks (parados): faixas, restauração pendente, andamento e SYNC; solta o PCM. Volume,
 * EQ, crossfader e master ficam. Devolve se havia algo a esvaziar; [notify] avisa na tela.
 */
internal fun MixSession.clearDecks(notify: Boolean): Boolean {
    val had = listOf(a, b).any { it.state.hasTrack || it.state.pendingRestore != null }
    mixer.cancelAlign()
    val cleared = a.unload() and b.unload()
    if (!cleared) return false
    if (!audio.outputRunning) mixer.dropStaleAudio()
    update { it.copy(phaseLocked = false, notice = if (notify && had) IDLE_NOTICE else it.notice) }
    return had
}

private const val IDLE_NOTICE = "Faixas descarregadas: o app ficou mais de 30 min sem uso"

/**
 * Processo novo com estado guardado: se o estado é recente, volta a ele ([restore]); se o app ficou sem
 * uso por mais de 30 min, só o mixer (crossfader, master, key lock) volta — os decks abrem limpos.
 */
internal fun MixSession.restoreOrStartClean(s: SessionSnapshot) {
    if (IdlePolicy.isStale(s.savedAtMs, nowMs())) {
        restore(s.copy(a = null, b = null))
        if (s.a != null || s.b != null) update { it.copy(notice = IDLE_NOTICE) }
    } else {
        restore(s)
    }
}

/**
 * Trata uma interrupção e guarda o estado se a reação pedir. Em segundo plano, depois de um pedido de
 * memória, os decks que pararam agora soltam o áudio (todo deck que para fora da tela passa por aqui).
 */
fun MixSession.interrupt(kind: Interruption) {
    if (onInterruption(kind).persist) save()
    if (memoryPressureInBackground && !store.value.foreground) releaseIdleAudio()
}

/** Estado atual para guardar em disco. */
fun MixSession.snapshot(): SessionSnapshot {
    val s = store.value
    return SessionSnapshot(a.snapshot(), b.snapshot(), s.crossfader, s.master, s.keyLock, savedAtMs = nowMs())
}

/**
 * Volta ao estado guardado depois que o processo foi reiniciado: mixer na hora; os decks ficam
 * marcados para recarregar os arquivos (pausados, na mesma posição) quando a tela aparecer.
 */
fun MixSession.restore(s: SessionSnapshot) {
    changeCrossfader(s.crossfader)
    changeMaster(s.master)
    if (store.value.keyLock != s.keyLock) toggleKeyLock()
    s.a?.let(a::scheduleRestore)
    s.b?.let(b::scheduleRestore)
}
