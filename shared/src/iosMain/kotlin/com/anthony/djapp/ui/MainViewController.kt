package com.anthony.djapp.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.anthony.djapp.di.initKoin
import com.anthony.djapp.domain.TrackRepository
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * PLACEHOLDER, não verificado: iOS não compila no Linux.
 * Sem Mac ainda não há projeto Xcode, biblioteca de arquivos nem áudio reais (ver `PlatformModule.ios.kt`).
 * O app Swift chama `initKoin()` ao abrir e mostra `MainViewController()`.
 */
@Suppress("ktlint:standard:function-naming", "FunctionNaming") // nome que o app Swift usa (convenção do Compose Multiplatform)
fun MainViewController() = run {
    initKoin()
    ComposeUIViewController {
        DjApp(koinViewModel(), koinInject<TrackRepository>())
    }
}
