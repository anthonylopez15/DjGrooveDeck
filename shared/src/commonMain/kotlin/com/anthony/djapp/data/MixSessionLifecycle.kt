package com.anthony.djapp.data

import com.anthony.djapp.domain.AudioSystem
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

/** Guarda o estado no [SessionStore]. */
fun MixSession.save() = sessionStore.save(snapshot())

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
    return SessionSnapshot(a.snapshot(), b.snapshot(), s.crossfader, s.master, s.keyLock)
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
