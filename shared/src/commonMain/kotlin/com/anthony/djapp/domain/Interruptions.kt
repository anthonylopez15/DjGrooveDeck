package com.anthony.djapp.domain

/**
 * Eventos de fora da tela que afetam o áudio. A plataforma só detecta (Android: fone desconectado, foco
 * de áudio; iOS: rota e interrupções do AVAudioSession) e repassa; a reação é decidida aqui, igual nas
 * duas plataformas.
 */
enum class Interruption {
    /** Fone/Bluetooth/USB saiu: o som iria para o alto-falante. */
    OutputDisconnected,

    /** Outro app começou a tocar música (perda definitiva do foco). */
    OtherAppPlaying,

    /** Ligação, assistente de voz e afins (perda temporária). */
    Transient,

    /** O sistema não deixou começar a tocar (ex.: ligação em andamento). */
    PlaybackDenied,

    /** Pedido de pausa vindo de fora (ex.: botão "Pausar" da notificação). */
    PauseRequested,
}

/** O que fazer diante de uma [Interruption]. */
data class InterruptionReaction(
    /** Pausar os dois decks, desfazendo o encaixe de batidas e qualquer largada pendente. */
    val pauseAll: Boolean,
    /** Guardar o estado em disco (o processo pode ser encerrado logo depois). */
    val persist: Boolean,
    /** Avisar o usuário na tela. */
    val notify: Boolean,
)

object InterruptionPolicy {
    /**
     * Reação a [kind] com [anyPlaying] decks tocando. Nunca volta a tocar sozinho depois: num set, o DJ
     * decide quando retomar (e os dois decks precisam voltar alinhados).
     */
    fun reactTo(kind: Interruption, anyPlaying: Boolean): InterruptionReaction = when (kind) {
        Interruption.PlaybackDenied -> InterruptionReaction(pauseAll = false, persist = false, notify = true)
        // pedido explícito: pausa e guarda mesmo que já estivesse parado; não precisa avisar
        Interruption.PauseRequested -> InterruptionReaction(pauseAll = true, persist = true, notify = false)
        else ->
            if (anyPlaying) {
                InterruptionReaction(pauseAll = true, persist = true, notify = true)
            } else {
                InterruptionReaction(pauseAll = false, persist = false, notify = false)
            }
    }
}
