package com.anthony.djapp.data

import com.anthony.djapp.domain.AudioSystem
import com.anthony.djapp.domain.PlatformEvent
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.IosAudioOutput

/**
 * PLACEHOLDER do [AudioSystem] no iOS (não verificado: precisa de Mac). Hoje só liga a saída no play.
 * A fazer: AVAudioSession (categoria playback), interrupções (ligação, outro app) e troca de rota
 * (fone saiu) como [PlatformEvent.Interrupted], e o aviso de memória como [PlatformEvent.MemoryPressure].
 */
class IosAudioSystem(private val output: IosAudioOutput, private val mixer: DjMixer) : AudioSystem {
    override val outputRunning: Boolean get() = output.running

    override fun requestPlayback(): Boolean {
        output.start(mixer)
        return true
    }

    override fun setListener(listener: (PlatformEvent) -> Unit) = Unit

    override fun setIdleTimeout(afterMs: Long?) = Unit
}
