package com.anthony.djapp.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anthony.djapp.data.MixSession
import com.anthony.djapp.domain.EmptyLibrary
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.IosAudioOutput
import com.anthony.djapp.presentation.DjViewModel

/**
 * PLACEHOLDER, não verificado: iOS não compila no Linux.
 * Sem Mac ainda não há projeto Xcode, biblioteca de arquivos nem áudio.
 * Quando houver, o app Swift chama MainViewController() e implementa AudioOutput e Library reais
 * (e a sessão passa a viver fora da tela, como no Android).
 */
fun MainViewController() = ComposeUIViewController {
    // remember: sem isso cada recomposição criaria outro mixer/sessão e perderia o estado
    val session = remember {
        val output = IosAudioOutput()
        val mixer = DjMixer(output.sampleRate)
        MixSession(mixer).apply {
            playbackGate = {
                output.start(mixer)
                true
            }
        }
    }
    val viewModel = viewModel { DjViewModel(session, EmptyLibrary) }
    DjApp(viewModel, EmptyLibrary)
}
