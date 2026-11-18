package com.anthony.djapp.di

import com.anthony.djapp.data.AndroidAudioSystem
import com.anthony.djapp.data.AndroidTrackRepository
import com.anthony.djapp.data.PrefsSessionStore
import com.anthony.djapp.data.PrefsTrackGridStore
import com.anthony.djapp.domain.AudioSystem
import com.anthony.djapp.domain.SessionStore
import com.anthony.djapp.domain.TrackGridStore
import com.anthony.djapp.domain.TrackRepository
import com.anthony.djapp.engine.AndroidAudioOutput
import com.anthony.djapp.engine.DjMixer
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/** Android: motor com AudioTrack e as portas implementadas sobre o sistema (tudo por processo). */
actual val platformModule: Module = module {
    single { AndroidAudioOutput(androidContext()) }
    single { DjMixer(get<AndroidAudioOutput>().sampleRate) }
    single { AndroidAudioSystem(androidContext(), get(), get()) } bind AudioSystem::class
    single<SessionStore> { PrefsSessionStore(androidContext()) }
    single<TrackGridStore> { PrefsTrackGridStore(androidContext()) }
    single { AndroidTrackRepository(androidContext()) } bind TrackRepository::class
}
