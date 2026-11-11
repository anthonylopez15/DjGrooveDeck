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

    @Volatile private var running = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    override fun start(mixer: DjMixer) {
        if (running) return
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        // chunks de 2 bursts; buffer do track com pelo menos 2 chunks
        val chunkFrames = burstFrames * 2
        val bufferBytes = maxOf(minBytes, chunkFrames * 2 * Float.SIZE_BYTES * 2)

        val at = AudioTrack.Builder()
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
        track = at
        running = true

        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buf = FloatArray(chunkFrames * 2)
            at.play()
            while (running) {
                mixer.render(buf, chunkFrames)
                val written = at.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) break
            }
            at.stop()
            at.release()
        }, "dj-audio").also { it.start() }
    }

    override fun stop() {
        running = false
        thread?.join(500)
        thread = null
        track = null
    }
}
