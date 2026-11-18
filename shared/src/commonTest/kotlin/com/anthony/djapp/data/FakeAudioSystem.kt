package com.anthony.djapp.data

import com.anthony.djapp.domain.AudioSystem
import com.anthony.djapp.domain.PlatformEvent
import com.anthony.djapp.domain.SessionStore
import com.anthony.djapp.domain.TrackGridStore
import com.anthony.djapp.engine.DjMixer

/** [AudioSystem] falso: libera (ou nega) o play, conta os pedidos e deixa o teste emitir eventos. */
class FakeAudioSystem(var grant: Boolean = true) : AudioSystem {
    var requests = 0
        private set
    var outputOn = false

    private var listener: (PlatformEvent) -> Unit = {}

    override val outputRunning: Boolean get() = outputOn

    override fun requestPlayback(): Boolean {
        requests++
        if (grant) outputOn = true
        return grant
    }

    override fun setListener(listener: (PlatformEvent) -> Unit) {
        this.listener = listener
    }

    /** Simula a plataforma detectando algo. */
    fun emit(event: PlatformEvent) = listener(event)
}

/** Sessão para testes, com portas falsas. */
fun testSession(
    mixer: DjMixer = DjMixer(44100),
    audio: AudioSystem = FakeAudioSystem(),
    store: SessionStore = InMemorySessionStore(),
    grids: TrackGridStore = InMemoryTrackGridStore(),
) = MixSession(mixer, audio, store, grids)
