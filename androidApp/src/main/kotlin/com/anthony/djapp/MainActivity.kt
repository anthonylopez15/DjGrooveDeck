package com.anthony.djapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.anthony.djapp.engine.AndroidAudioOutput
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.ui.DjApp
import com.anthony.djapp.ui.DjController

class MainActivity : ComponentActivity() {
    private lateinit var output: AndroidAudioOutput
    private lateinit var mixer: DjMixer
    private lateinit var controller: DjController
    private lateinit var library: AndroidLibrary

    /**
     * True enquanto o seletor de pastas do sistema está aberto: ele cobre o app (a tela sai de cena),
     * mas a música que está tocando não pode parar por causa disso.
     */
    private var pickingFolder = false

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        pickingFolder = false
        if (uri != null) library.onFolderPicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = AndroidAudioOutput(this)
        mixer = DjMixer(output.sampleRate)
        controller = DjController(mixer)
        library = AndroidLibrary(applicationContext) {
            pickingFolder = true
            folderPicker.launch(null)
        }

        setContent { DjApp(controller, library) }
    }

    override fun onStart() {
        super.onStart()
        output.start(mixer)
    }

    override fun onStop() {
        // Sem serviço em primeiro plano ainda: ao sair da tela o áudio para, exceto atrás do
        // seletor de pastas, que é rápido e deve deixar a música tocando.
        if (!pickingFolder) output.stop()
        super.onStop()
    }
}
