package com.anthony.djapp

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.anthony.djapp.engine.AndroidAudioOutput
import com.anthony.djapp.engine.AndroidTrackDecoder
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.ui.DeckId
import com.anthony.djapp.ui.DjApp
import com.anthony.djapp.ui.DjController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var output: AndroidAudioOutput
    private lateinit var mixer: DjMixer
    private lateinit var controller: DjController

    private var pendingDeck = DeckId.A

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) loadInto(pendingDeck, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = AndroidAudioOutput(this)
        mixer = DjMixer(output.sampleRate)
        controller = DjController(mixer)

        setContent {
            DjApp(controller) { deck ->
                pendingDeck = deck
                picker.launch(arrayOf("audio/*"))
            }
        }
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

    private fun loadInto(deck: DeckId, uri: Uri) {
        lifecycleScope.launch {
            try {
                val track = AndroidTrackDecoder.decode(this@MainActivity, uri)
                when (deck) {
                    DeckId.A -> controller.a.load(track)
                    DeckId.B -> controller.b.load(track)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Não consegui abrir: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
