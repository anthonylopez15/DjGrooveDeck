package com.anthony.djapp

import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.content.res.Configuration
import com.anthony.djapp.engine.AndroidAudioOutput
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.ui.DjController
import com.anthony.djapp.ui.SessionSnapshot

/**
 * A "sessão" de DJ vive no processo, e não na tela: o mixer, a saída de áudio e o controller (faixas
 * carregadas, posições, tempo, SYNC...) continuam existindo quando a Activity sai de cena ou é
 * recriada. A tela só se liga a ela de novo ao voltar, e tudo aparece como estava.
 *
 * O [PlaybackService] mantém o processo vivo em primeiro plano enquanto há música tocando.
 *
 * Se mesmo assim o sistema encerrar o processo, o estado guardado com [Session.save] volta quando a
 * sessão nasce de novo: as faixas são recarregadas dos arquivos, pausadas, na mesma posição.
 */
object DjSession {
    class Session(context: Context) {
        val output = AndroidAudioOutput(context)
        val mixer = DjMixer(output.sampleRate)
        val controller = DjController(mixer)
        private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

        init {
            // processo novo: volta ao que estava (os decks são recarregados quando a tela aparece)
            SessionSnapshot.decode(prefs.getString(KEY_STATE, null))?.let(controller::restore)

            // Foco de áudio: pede antes de tocar; outro app ou uma ligação pausa o set
            val focus = AudioFocus(context) { transient ->
                if (anyPlaying) {
                    pauseAll()
                    controller.systemNotice =
                        if (transient) "Música pausada (ligação ou outro áudio)" else "Música pausada: outro app começou a tocar"
                }
            }
            // Antes de tocar: pede o foco e liga a saída de áudio (que fica desligada com tudo parado)
            controller.playbackGate = {
                focus.request().also { granted ->
                    if (granted) output.start(mixer)
                    else controller.systemNotice = "Não dá para tocar agora (ligação em andamento?)"
                }
            }

            // Fone saiu ou o Bluetooth caiu: o Android passaria o som para o alto-falante, no meio do set.
            // Como em qualquer player, pausa (e guarda o estado); o DJ decide quando voltar.
            val noisy = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    if (intent.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY || !anyPlaying) return
                    pauseAll()
                    controller.systemNotice = "Saída de áudio desconectada: música pausada"
                }
            }
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(noisy, filter)
            }

            // Em segundo plano o sistema pode pedir memória: solta o PCM dos decks parados (dezenas de MB
            // cada). Ficam a origem, a posição, o tempo, o SYNC e a onda; ao voltar, recarrega do arquivo.
            context.registerComponentCallbacks(object : ComponentCallbacks2 {
                override fun onTrimMemory(level: Int) {
                    if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND && !activityVisible) {
                        save() // pedido de memória costuma vir antes de o processo ser encerrado
                        releaseIdleAudio()
                    }
                }

                override fun onConfigurationChanged(newConfig: Configuration) = Unit
                override fun onLowMemory() {
                    if (!activityVisible) releaseIdleAudio()
                }
            })
        }

        private fun releaseIdleAudio() {
            controller.releaseIdleAudio()
            // com a thread de áudio parada ninguém mais solta o PCM que o key lock segurava: solta aqui
            if (!output.running) mixer.dropStaleAudio()
        }

        private val handler = Handler(Looper.getMainLooper())
        private var idleSince = 0L

        /**
         * Desliga a saída de áudio depois de [IDLE_STOP_MS] com os dois decks parados: com ela aberta o
         * sistema considera o app tocando (tocando silêncio) e gasta bateria. O próximo play religa.
         * Fora da tela quem cuida disso é a Activity/o serviço (desligam na hora, sem música).
         */
        private val idleWatcher = object : Runnable {
            override fun run() {
                val now = SystemClock.elapsedRealtime()
                when {
                    anyPlaying || !output.running -> idleSince = 0L
                    idleSince == 0L -> idleSince = now
                    now - idleSince >= IDLE_STOP_MS -> {
                        output.stop()
                        idleSince = 0L
                    }
                }
                handler.postDelayed(this, IDLE_CHECK_MS)
            }
        }

        init {
            handler.postDelayed(idleWatcher, IDLE_CHECK_MS)
        }

        /** Algum deck tocando agora (estado real do motor). */
        val anyPlaying: Boolean get() = mixer.deckA.playing || mixer.deckB.playing

        /** Pausa os dois decks. */
        fun pauseAll() {
            mixer.deckA.pause()
            mixer.deckB.pause()
            save()
        }

        /** Guarda o estado em disco (assíncrono, barato). Chamar na thread principal. */
        fun save() {
            prefs.edit().putString(KEY_STATE, controller.snapshot().encode()).apply()
        }
    }

    private const val KEY_STATE = "state"
    private const val IDLE_STOP_MS = 5_000L
    private const val IDLE_CHECK_MS = 1_000L

    @Volatile private var session: Session? = null

    /** A tela está visível (entre onStart e onStop da Activity). */
    @Volatile var activityVisible: Boolean = false

    fun get(context: Context): Session =
        session ?: synchronized(this) {
            session ?: Session(context.applicationContext).also { session = it }
        }
}
