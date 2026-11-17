package com.anthony.djapp.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Foco de áudio: o app pede para ser "o" player de música antes de tocar, e é avisado quando outro
 * app ou uma ligação toma a vez.
 *
 * - Perda definitiva (outro app começou a tocar música) ou temporária (ligação, assistente de voz):
 *   [onLost] — o app pausa. Não volta a tocar sozinho ao recuperar o foco: num set, o DJ decide
 *   quando e como voltar (e os dois decks precisam voltar alinhados).
 * - Perda breve que só pede volume baixo (som de notificação): o próprio Android abaixa o volume e
 *   devolve depois; nada a fazer.
 */
internal class AudioFocus(context: Context, private val onLost: (transient: Boolean) -> Unit) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val request: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener({ change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        abandon()
                        onLost(false)
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onLost(true)
                }
            }, Handler(Looper.getMainLooper()))
            .build()

    /** Pede o foco antes de tocar. false se o sistema negar (ex.: ligação em andamento). */
    fun request(): Boolean = am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    fun abandon() {
        am.abandonAudioFocusRequest(request)
    }
}
