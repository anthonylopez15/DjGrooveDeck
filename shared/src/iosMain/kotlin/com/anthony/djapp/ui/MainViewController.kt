package com.anthony.djapp.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.IosAudioOutput

/**
 * PLACEHOLDER, não verificado: iOS não compila no Linux.
 * Sem Mac ainda não há projeto Xcode, biblioteca de arquivos nem áudio.
 * Quando houver, o app Swift chama MainViewController() e implementa AudioOutput e Library reais.
 */
fun MainViewController() = ComposeUIViewController {
    // remember: sem isso cada recomposição criaria outro mixer/controller e perderia o estado
    val controller = remember {
        val output = IosAudioOutput()
        val mixer = DjMixer(output.sampleRate)
        output.start(mixer)
        DjController(mixer)
    }
    DjApp(controller, EmptyLibrary)
}
