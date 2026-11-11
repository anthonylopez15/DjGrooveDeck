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

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) library.onFolderPicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = AndroidAudioOutput(this)
        mixer = DjMixer(output.sampleRate)
        controller = DjController(mixer)
        library = AndroidLibrary(applicationContext) { folderPicker.launch(null) }

        setContent { DjApp(controller, library) }
    }

    override fun onStart() {
        super.onStart()
        output.start(mixer)
    }

    override fun onStop() {
        // Sem serviço em primeiro plano ainda: ao sair da tela o áudio para.
        output.stop()
        super.onStop()
    }
}
