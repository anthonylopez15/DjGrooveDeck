package com.anthony.djapp.di

import com.anthony.djapp.data.FakeAudioSystem
import com.anthony.djapp.data.InMemorySessionStore
import com.anthony.djapp.data.InMemoryTrackGridStore
import com.anthony.djapp.data.MixSession
import com.anthony.djapp.domain.AudioSystem
import com.anthony.djapp.domain.EmptyTrackRepository
import com.anthony.djapp.domain.SessionStore
import com.anthony.djapp.domain.TrackGridStore
import com.anthony.djapp.domain.TrackRepository
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.presentation.DjViewModel
import com.anthony.djapp.presentation.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * O Koin monta as dependências ao rodar (não ao compilar): este teste monta o grafo dos módulos comuns
 * com uma plataforma falsa e falha se faltar alguma definição — antes de o app abrir.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KoinModulesTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher()) // o ViewModel usa Dispatchers.Main

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** O que cada plataforma precisa fornecer (ver `platformModule`). */
    private val fakePlatform = module {
        single { DjMixer(44100) }
        single<AudioSystem> { FakeAudioSystem() }
        single<SessionStore> { InMemorySessionStore() }
        single<TrackGridStore> { InMemoryTrackGridStore() }
        single<TrackRepository> { EmptyTrackRepository }
    }

    @Test
    fun commonModulesResolveWithAPlatformModule() {
        val koin = koinApplication { modules(fakePlatform, sessionModule, presentationModule) }.koin
        val session = koin.get<MixSession>()
        assertSame(session, koin.get<MixSession>(), "a sessão é uma só por processo")
        val vm1 = koin.get<DjViewModel>()
        val vm2 = koin.get<DjViewModel>()
        assertNotSame(vm1, vm2, "ViewModel é por tela")
        koin.get<LibraryViewModel>()
    }
}
