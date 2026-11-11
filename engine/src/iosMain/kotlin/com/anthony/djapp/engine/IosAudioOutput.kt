package com.anthony.djapp.engine

/**
 * PLACEHOLDER: ainda não toca nada.
 * Não dá para compilar nem testar iOS no Linux; a implementação real fica para quando
 * houver um Mac. Plano: AVAudioEngine + AVAudioSourceNode, chamando mixer.render(...)
 * num buffer pré-alocado dentro do render block.
 */
class IosAudioOutput(override val sampleRate: Int = 48000) : AudioOutput {
    override val running: Boolean = false
    override fun start(mixer: DjMixer) = Unit
    override fun stop() = Unit
}
