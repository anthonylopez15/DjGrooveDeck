package com.anthony.djapp.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log

/**
 * Saída via AudioTrack em modo de baixa latência, PCM float.
 * O mixer renderiza em buffers pré-alocados numa thread de prioridade de áudio.
 * Se a latência não bastar para o seu uso, o próximo passo é trocar por Oboe (C++/NDK)
 * mantendo esta mesma interface.
 */
class AndroidAudioOutput(context: Context) : AudioOutput {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override val sampleRate: Int =
        am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000

    private val burstFrames: Int =
        am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256

    /** Cada thread de áudio tem a sua flag: uma thread antiga nunca "revive" por causa de um novo start(). */
    private class Worker {
        @Volatile var alive = true
    }

    @Volatile private var worker: Worker? = null

    override val running: Boolean get() = worker != null
    private var thread: Thread? = null

    private fun createTrack(chunkFrames: Int): AudioTrack {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        // buffer do track com pelo menos 2 chunks
        val bufferBytes = maxOf(minBytes, chunkFrames * 2 * Float.SIZE_BYTES * 2)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
            .also { track ->
                // a capacidade pode ser grande; o que vale para a latência é quanto fica preenchido
                track.bufferSizeInFrames = (chunkFrames * LATENCY_CHUNKS).coerceAtMost(track.bufferCapacityInFrames)
                // diagnóstico: para onde o áudio está indo (alto-falante, fone, Bluetooth...)
                track.addOnRoutingChangedListener({ r ->
                    Log.i(TAG, "rota: ${describe(r.routedDevice)}")
                }, null)
            }
    }

    private fun sleepQuietly(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    override fun start(mixer: DjMixer) {
        if (worker != null) return
        val chunkFrames = burstFrames * 2 // chunks de 2 bursts
        val w = Worker()
        worker = w

        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buf = FloatArray(chunkFrames * 2)
            var at: AudioTrack? = null
            while (w.alive) {
                if (at == null) {
                    at = try {
                        createTrack(chunkFrames).also {
                            it.play()
                            Log.i(TAG, "saída aberta: $sampleRate Hz, $chunkFrames frames/bloco, rota ${describe(it.routedDevice)}")
                        }
                    } catch (ignored: Exception) { // sem saída agora: tenta de novo abaixo
                        null
                    }
                    if (at == null) { // sem saída agora (ex.: o fone saiu): tenta de novo logo
                        Log.w(TAG, "sem saída de áudio; tentando de novo")
                        sleepQuietly(300)
                        continue
                    }
                }
                mixer.render(buf, chunkFrames)
                val written = at.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    // a rota de áudio mudou ou o track morreu (DEAD_OBJECT): recria em vez de ficar mudo
                    Log.w(TAG, "escrita falhou ($written); recriando o AudioTrack")
                    runCatching { at.release() }
                    at = null
                    sleepQuietly(150)
                }
            }
            at?.let {
                Log.i(TAG, "saída fechada; falhas de buffer: ${it.underrunCount}")
                runCatching { it.stop() }
                runCatching { it.release() }
            }
        }, "dj-audio").also { it.start() }
    }

    companion object {
        private const val TAG = "DjAudio"

        /** Blocos de renderização mantidos no buffer do AudioTrack: menos = menos atraso, mais = menos risco de falha. */
        private const val LATENCY_CHUNKS = 3

        private fun describe(d: AudioDeviceInfo?): String {
            if (d == null) return "nenhuma"
            val kind = when (d.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "alto-falante"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "fone com fio"
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
                else -> "tipo ${d.type}"
            }
            return "$kind (${d.productName})"
        }
    }

    override fun stop() {
        worker?.alive = false
        worker = null
        thread?.join(500)
        thread = null
        Log.i(TAG, "saída fechada")
    }
}
