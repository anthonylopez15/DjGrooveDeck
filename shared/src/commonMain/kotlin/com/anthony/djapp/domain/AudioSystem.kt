package com.anthony.djapp.domain

/**
 * Porta (ADR 0001): a integração com o sistema de áudio da plataforma. A plataforma só **detecta**
 * (foco de áudio, fone desconectado, ligação, falta de memória) e avisa por [PlatformEvent]; quem
 * decide o que fazer é a sessão, com as regras do domínio — igual em Android e iOS.
 *
 * Android: foco de áudio + ACTION_AUDIO_BECOMING_NOISY + onTrimMemory. iOS: AVAudioSession
 * (interrupções, troca de rota) + aviso de memória.
 */
interface AudioSystem {
    /**
     * Chamado antes de um deck começar a tocar: pede a vez ao sistema (foco de áudio / sessão de áudio)
     * e liga a saída. false se o sistema negar (ex.: ligação em andamento).
     */
    fun requestPlayback(): Boolean

    /** A saída de áudio está ligada (a thread de áudio está rodando). */
    val outputRunning: Boolean

    /** Registra quem recebe os eventos detectados (a sessão). Chamado na thread principal. */
    fun setListener(listener: (PlatformEvent) -> Unit)

    /**
     * Agenda um [PlatformEvent.IdleTimeout] daqui a [afterMs] (substitui o anterior); null cancela. É só
     * um lembrete de "melhor esforço" (o sistema pode adiar com o app congelado): quem decide é a sessão,
     * conferindo o relógio.
     */
    fun setIdleTimeout(afterMs: Long?)
}

/** Algo que a plataforma detectou e a sessão precisa tratar. */
sealed interface PlatformEvent {
    /** Interrupção do áudio (ver [InterruptionPolicy]). */
    data class Interrupted(val kind: Interruption) : PlatformEvent

    /** O sistema pediu memória (app em segundo plano, normalmente antes de encerrar o processo). */
    data object MemoryPressure : PlatformEvent

    /** O temporizador pedido por [AudioSystem.setIdleTimeout] venceu. */
    data object IdleTimeout : PlatformEvent
}
