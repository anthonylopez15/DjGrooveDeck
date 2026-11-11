package com.anthony.djapp.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.IosAudioOutput

/**
 * PLACEHOLDER, não verificado: iOS não compila no Linux.
 * Sem Mac ainda não há projeto Xcode nem seletor de arquivos nem áudio.
 * Quando houver, o app Swift chama MainViewController() e implementa o AudioOutput real.
 */
fun MainViewController() = ComposeUIViewController {
    val output = IosAudioOutput()
    val mixer = DjMixer(output.sampleRate)
    output.start(mixer)
    DjApp(DjController(mixer), onLoad = { /* TODO: UIDocumentPicker + AVAudioFile */ })
}
