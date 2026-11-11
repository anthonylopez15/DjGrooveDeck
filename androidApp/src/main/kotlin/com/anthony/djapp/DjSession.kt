package com.anthony.djapp

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.anthony.djapp.engine.AndroidAudioOutput
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.ui.DjController

/**
 * A "sessão" de DJ vive no processo, e não na tela: o mixer, a saída de áudio e o controller (faixas
 * carregadas, posições, tempo, SYNC...) continuam existindo quando a Activity sai de cena ou é
 * recriada. A tela só se liga a ela de novo ao voltar, e tudo aparece como estava.
 *
 * O [PlaybackService] mantém o processo vivo em primeiro plano enquanto há música tocando.
 */
object DjSession {
    class Session(context: Context) {
        val output = AndroidAudioOutput(context)
        val mixer = DjMixer(output.sampleRate)
        val controller = DjController(mixer)

        init {
            // Em segundo plano o sistema pode pedir memória: solta o PCM dos decks parados (dezenas de MB
            // cada). Ficam a origem, a posição, o tempo, o SYNC e a onda; ao voltar, recarrega do arquivo.
            context.registerComponentCallbacks(object : ComponentCallbacks2 {
                override fun onTrimMemory(level: Int) {
                    if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND && !activityVisible) {
                        controller.releaseIdleAudio()
                    }
                }

                override fun onConfigurationChanged(newConfig: Configuration) = Unit
                override fun onLowMemory() {
                    if (!activityVisible) controller.releaseIdleAudio()
                }
            })
        }

        /** Algum deck tocando agora (estado real do motor). */
        val anyPlaying: Boolean get() = mixer.deckA.playing || mixer.deckB.playing

        /** Pausa os dois decks. */
        fun pauseAll() {
            mixer.deckA.pause()
            mixer.deckB.pause()
        }
    }

    @Volatile private var session: Session? = null

    /** A tela está visível (entre onStart e onStop da Activity). */
    @Volatile var activityVisible: Boolean = false

    fun get(context: Context): Session =
        session ?: synchronized(this) {
            session ?: Session(context.applicationContext).also { session = it }
        }
}
