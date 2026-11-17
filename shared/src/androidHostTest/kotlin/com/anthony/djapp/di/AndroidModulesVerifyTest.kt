package com.anthony.djapp.di

import android.content.Context
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Verifica, sem aparelho, que o grafo Android completo (plataforma + sessão + ViewModel) tem tudo o que
 * os construtores pedem. `Context` vem do `androidContext()` no app; `Int` é a taxa de amostragem.
 */
@OptIn(KoinExperimentalAPI::class)
class AndroidModulesVerifyTest {
    @Test
    fun androidModuleGraphIsComplete() {
        org.koin.dsl.module { includes(appModules()) }.verify(extraTypes = listOf(Context::class, Int::class))
    }
}
