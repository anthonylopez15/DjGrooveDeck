package com.anthony.djapp

import android.content.Context
import com.anthony.djapp.data.AndroidAudioSystem
import com.anthony.djapp.data.AndroidTrackRepository
import com.anthony.djapp.data.MixSession
import com.anthony.djapp.data.PrefsSessionStore
import com.anthony.djapp.domain.Interruption
import com.anthony.djapp.engine.AndroidAudioOutput
import com.anthony.djapp.engine.DjMixer

/**
 * Monta a sessão de DJ do processo (não da tela): saída de áudio, mixer, as implementações Android das
 * portas do domínio e a [MixSession]. Tudo continua existindo quando a Activity sai de cena ou é
 * recriada; a tela só se liga de novo ao voltar, e tudo aparece como estava.
 *
 * Aqui não há regra: o que fazer em interrupções, falta de memória e restauração é da [MixSession].
 * O [PlaybackService] mantém o processo vivo em primeiro plano enquanto há música tocando.
 */
object DjSession {
    class Session(context: Context) {
        private val output = AndroidAudioOutput(context)
        private val mixer = DjMixer(output.sampleRate)
        private val audioSystem = AndroidAudioSystem(context, output, mixer)

        /** A sessão de áudio (restaura o estado guardado ao nascer). */
        val mix = MixSession(mixer, audioSystem, PrefsSessionStore(context))

        /** Biblioteca de músicas: vive no processo (a tela e o ViewModel podem ser recriados). */
        val library = AndroidTrackRepository(context)

        /** Algum deck tocando agora (estado real do motor). */
        val anyPlaying: Boolean get() = mix.a.playingNow || mix.b.playingNow

        /** Pausa os dois decks (botão "Pausar" da notificação, fim do serviço) e guarda o estado. */
        fun pauseAll() = mix.interrupt(Interruption.PauseRequested)

        /** Guarda o estado (chamado ao sair da tela e periodicamente pelo serviço). */
        fun save() = mix.save()

        /** Desliga a saída de áudio já (a tela saiu de cena sem música tocando). */
        fun stopOutput() = audioSystem.stopOutput()
    }

    @Volatile private var session: Session? = null

    /** A tela está visível (entre onStart e onStop da Activity); o serviço usa para saber quando parar. */
    @Volatile var activityVisible: Boolean = false

    fun get(context: Context): Session =
        session ?: synchronized(this) {
            session ?: Session(context.applicationContext).also { session = it }
        }
}
