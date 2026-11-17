package com.anthony.djapp.di

import com.anthony.djapp.data.MixSession
import com.anthony.djapp.presentation.DjViewModel
import com.anthony.djapp.presentation.LibraryViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

// Injeção de dependências (ADR 0001, regra 7). Comum a Android e iOS; cada plataforma fornece o
// platformModule com as implementações das portas do domínio e o motor de áudio:
// DjMixer, AudioSystem, SessionStore e TrackRepository.

/** A sessão de áudio: uma por processo (continua tocando sem a tela). */
val sessionModule = module {
    singleOf(::MixSession)
}

/** ViewModels: vivem com a tela. */
val presentationModule = module {
    viewModelOf(::DjViewModel)
    viewModelOf(::LibraryViewModel)
}

/** Implementações da plataforma (motor de áudio e portas do domínio). */
expect val platformModule: Module

/** Todos os módulos do app, na ordem em que devem ser carregados. */
fun appModules(): List<Module> = listOf(platformModule, sessionModule, presentationModule)
