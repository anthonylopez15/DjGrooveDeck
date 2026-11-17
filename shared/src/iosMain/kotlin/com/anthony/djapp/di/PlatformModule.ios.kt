package com.anthony.djapp.di

import com.anthony.djapp.data.InMemorySessionStore
import com.anthony.djapp.data.IosAudioSystem
import com.anthony.djapp.domain.AudioSystem
import com.anthony.djapp.domain.EmptyTrackRepository
import com.anthony.djapp.domain.SessionStore
import com.anthony.djapp.domain.TrackRepository
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.IosAudioOutput
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

/** iOS (PLACEHOLDER, não verificado: precisa de Mac): saída e portas provisórias. */
actual val platformModule: Module = module {
    single { IosAudioOutput() }
    single { DjMixer(get<IosAudioOutput>().sampleRate) }
    single<AudioSystem> { IosAudioSystem(get(), get()) }
    single<SessionStore> { InMemorySessionStore() }
    single<TrackRepository> { EmptyTrackRepository }
}

private var koinStarted = false

/** Inicia o Koin uma vez (o app Swift chama ao abrir; o MainViewController garante também). */
fun initKoin() {
    if (koinStarted) return
    startKoin { modules(appModules()) }
    koinStarted = true
}
