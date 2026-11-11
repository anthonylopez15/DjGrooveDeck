package com.anthony.djapp.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process

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

    private var worker: Worker? = null
    private var thread: Thread? = null

    private fun createTrack(chunkFrames: Int): AudioTrack {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT,
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
                        createTrack(chunkFrames).also { it.play() }
                    } catch (e: Exception) {
                        null
                    }
                    if (at == null) { // sem saída agora (ex.: o fone saiu): tenta de novo logo
                        sleepQuietly(300)
                        continue
                    }
                }
                mixer.render(buf, chunkFrames)
                val written = at.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    // a rota de áudio mudou ou o track morreu (DEAD_OBJECT): recria em vez de ficar mudo
                    runCatching { at.release() }
                    at = null
                    sleepQuietly(150)
                }
            }
            at?.let {
                runCatching { it.stop() }
                runCatching { it.release() }
            }
        }, "dj-audio").also { it.start() }
    }

    override fun stop() {
        worker?.alive = false
        worker = null
        thread?.join(500)
        thread = null
    }
}
