package com.anthony.djapp.engine

/**
 * Saída de áudio da plataforma. Android: AudioTrack (hoje) / Oboe (futuro).
 * iOS: AVAudioEngine com um source node (a implementar, precisa de Mac).
 */
interface AudioOutput {
    /** Taxa nativa do dispositivo; o [DjMixer] deve ser criado com ela. */
    val sampleRate: Int

    /** A thread de áudio está ligada (entre [start] e [stop]). */
    val running: Boolean

    /** Começa a puxar áudio de [mixer] numa thread própria. Sem efeito se já está rodando. */
    fun start(mixer: DjMixer)

    fun stop()
}
