package com.anthony.djapp.data

import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.anthony.djapp.domain.AudioSystem
import com.anthony.djapp.domain.Interruption
import com.anthony.djapp.domain.PlatformEvent
import com.anthony.djapp.engine.AndroidAudioOutput
import com.anthony.djapp.engine.DjMixer

/**
 * [AudioSystem] no Android. Só **detecta** e repassa (a decisão é da sessão):
 * - foco de áudio: pedido antes de tocar; perdê-lo = outro app tocando ou ligação;
 * - ACTION_AUDIO_BECOMING_NOISY: fone/Bluetooth/USB desconectado;
 * - onTrimMemory / onLowMemory: o sistema pediu memória.
 *
 * Também cuida da saída de áudio, que é da plataforma: liga no play e desliga depois de
 * [IDLE_STOP_MS] com os dois decks parados (aberta, o sistema considera o app tocando silêncio).
 */
class AndroidAudioSystem(
    context: Context,
    private val output: AndroidAudioOutput,
    private val mixer: DjMixer,
) : AudioSystem {
    private var listener: (PlatformEvent) -> Unit = {}
    private val handler = Handler(Looper.getMainLooper())

    private fun emit(event: PlatformEvent) = listener(event)

    private val focus = AudioFocus(context) { transient ->
        emit(PlatformEvent.Interrupted(if (transient) Interruption.Transient else Interruption.OtherAppPlaying))
    }

    override val outputRunning: Boolean get() = output.running

    override fun requestPlayback(): Boolean {
        if (!focus.request()) return false
        output.start(mixer)
        return true
    }

    override fun setListener(listener: (PlatformEvent) -> Unit) {
        this.listener = listener
    }

    /** Desliga a saída de áudio já (a tela saiu de cena sem música tocando). O próximo play religa. */
    fun stopOutput() = output.stop()

    init {
        val noisy = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    emit(PlatformEvent.Interrupted(Interruption.OutputDisconnected))
                }
            }
        }
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(noisy, filter)
        }

        context.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (isMemoryPressure(level)) emit(PlatformEvent.MemoryPressure)
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            // obsoleto desde o Android 14, mas ainda chamado em versões anteriores (minSdk 26)
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onLowMemory() = emit(PlatformEvent.MemoryPressure)
        })

        handler.postDelayed(idleWatcher(), IDLE_CHECK_MS)
    }

    /**
     * Pedido de memória que vale soltar áudio. BACKGROUND e acima: o app saiu de cena (Android 14+ só
     * manda esses, e não para um processo com serviço tocando). RUNNING_LOW/CRITICAL: memória baixa com o
     * app ativo — até o Android 13 chega mesmo tocando em segundo plano, e aí o deck **parado** pode soltar.
     * UI_HIDDEN (só "a tela sumiu") não é falta de memória.
     */
    @Suppress("DEPRECATION")
    private fun isMemoryPressure(level: Int): Boolean =
        level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL

    private fun idleWatcher() = object : Runnable {
        private var idleSince = 0L

        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val anyPlaying = mixer.deckA.playing || mixer.deckB.playing
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

    private companion object {
        const val IDLE_STOP_MS = 5_000L
        const val IDLE_CHECK_MS = 1_000L
    }
}
