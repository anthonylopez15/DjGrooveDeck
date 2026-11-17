package com.anthony.djapp

import android.app.Application
import com.anthony.djapp.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Inicia a injeção de dependências (Koin) uma vez por processo. As peças da sessão de áudio são
 * `single`: vivem enquanto o processo vive, então a música segue em segundo plano e a tela, ao voltar,
 * encontra tudo como estava.
 */
class GrooveDeckApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@GrooveDeckApp)
            modules(appModules() + androidAppModule)
        }
    }
}

/** O que é só do app Android (Activity/serviço). */
val androidAppModule = module {
    singleOf(::ScreenVisibility)
}

/** A tela está à vista (entre onStart e onStop da Activity); o serviço usa para saber quando parar. */
class ScreenVisibility {
    @Volatile var visible: Boolean = false
}
