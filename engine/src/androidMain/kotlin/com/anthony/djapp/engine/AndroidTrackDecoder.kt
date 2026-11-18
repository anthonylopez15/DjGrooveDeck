package com.anthony.djapp.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder

/** Decodifica qualquer áudio suportado pelo Android (MP3, AAC, FLAC, WAV, OGG...) para [Track]. */
object AndroidTrackDecoder {
    private const val TAG = "DjDecode"

    /** Trecho decodificado na análise rápida: o suficiente para o detector de BPM. */
    private const val ANALYSIS_SECONDS = 100.0

    /**
     * Decodificações completas em paralelo só quando a soma dos picos de memória cabe ([DecodeAdmission]);
     * senão esperam a vez.
     */
    private val admission = DecodeAdmission(
        maxHeap = { Runtime.getRuntime().maxMemory() },
        usedHeap = { Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() } },
        collect = { collectGarbage() },
    )

    @Suppress("ExplicitGarbageCollectionCall") // proposital: lixo ainda não coletado não é memória ocupada
    private fun collectGarbage() = System.gc()

    /** [onProgress] recebe 0..1 conforme a decodificação avança (chamado fora da thread principal). */
    suspend fun decode(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit = {},
    ): Track = withContext(Dispatchers.Default) {
        val name = displayName(context, uri)
        val (durationFrames, sampleRate) = probeFrames(context, uri)
        val queuedAt = SystemClock.elapsedRealtime()
        val ticket = admission.admit(durationFrames, sampleRate)
        val startedAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "decodificando '$name': esperou ${startedAt - queuedAt} ms pela memória")
        try {
            val d = decodeRange(context, uri, startUs = 0L, maxSeconds = null, onProgress = onProgress, ticket = ticket) {
                ensureActive()
            }
            onProgress(1f)
            Log.i(TAG, "decodificado '$name' em ${SystemClock.elapsedRealtime() - startedAt} ms")
            Track(name, d.sampleRate, d.pcm)
        } catch (e: OutOfMemoryError) {
            // OutOfMemoryError é Error, não Exception: sem isto o app fecha em vez de avisar
            throw TrackRejectedException("Memória insuficiente para carregar esta faixa", e)
        } finally {
            ticket.release()
        }
    }

    /** Duração anunciada (em frames) e taxa do arquivo; 0 frames se ele não anuncia a duração. */
    private fun probeFrames(context: Context, uri: Uri): Pair<Long, Int> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val f = extractor.getTrackFormat(firstAudioTrack(extractor))
            val rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val us = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L
            (us / 1_000_000.0 * rate).toLong() to rate
        } finally {
            extractor.release()
        }
    }

    /**
     * Duração e BPM sem carregar a faixa inteira: decodifica só ~100 s (a partir de 25% da faixa,
     * se ela for longa), o que é bem mais rápido. Cancelável entre blocos de decodificação.
     */
    suspend fun analyze(context: Context, uri: Uri): TrackInfo = withContext(Dispatchers.Default) {
        val durationUs = durationUs(context, uri)
        val startUs = if (durationUs > 150_000_000L) (durationUs * 0.25).toLong() else 0L
        val d = decodeRange(context, uri, startUs, ANALYSIS_SECONDS, {}) { ensureActive() }
        TrackInfo(
            durationSec = durationUs / 1_000_000.0,
            bpm = BpmDetector.detect(d.pcm, d.sampleRate)?.bpm,
        )
    }

    private class Decoded(val pcm: ShortArray, val sampleRate: Int)

    private fun durationUs(context: Context, uri: Uri): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val i = firstAudioTrack(extractor)
            val f = extractor.getTrackFormat(i)
            if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L
        } finally {
            extractor.release()
        }
    }

    private fun firstAudioTrack(extractor: MediaExtractor): Int =
        (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("Arquivo sem faixa de áudio")

    /**
     * Recusa a faixa de [frames] frames se passa do limite do app ([TrackLimits]: duração) ou da memória
     * ([MemoryBudget]); a mensagem diz qual, com a duração. [memoryLimit] null = sem limite de memória.
     */
    private fun checkFits(frames: Long, sampleRate: Int, memoryLimit: MemoryBudget.Limit?) {
        val durationSec = frames.toDouble() / sampleRate
        if (TrackLimits.tooLong(durationSec)) throw TrackRejectedException(TrackLimits.tooLongMessage(durationSec))
        if (memoryLimit != null && frames > memoryLimit.frames) {
            throw TrackRejectedException(MemoryBudget.tooLongMessage(memoryLimit, sampleRate))
        }
    }

    /**
     * Decodifica a partir de [startUs]; se [maxSeconds] não for null, para depois desse tanto de áudio.
     * [check] é chamado a cada volta do laço (use para propagar cancelamento).
     */
    private fun decodeRange(
        context: Context,
        uri: Uri,
        startUs: Long,
        maxSeconds: Double?,
        onProgress: (Float) -> Unit,
        ticket: DecodeAdmission.Ticket? = null,
        check: () -> Unit,
    ): Decoded {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        try {
            val trackIndex = firstAudioTrack(extractor)
            extractor.selectTrack(trackIndex)
            val inFormat = extractor.getTrackFormat(trackIndex)
            val mime = inFormat.getString(MediaFormat.KEY_MIME)!!
            if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val durationUs =
                if (inFormat.containsKey(MediaFormat.KEY_DURATION)) inFormat.getLong(MediaFormat.KEY_DURATION) else 0L
            var lastPct = -1

            var sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val maxFrames = maxSeconds?.let { (it * sampleRate).toLong() }
            val durationFrames = if (durationUs > 0) (durationUs / 1_000_000.0 * sampleRate).toLong() else 0L
            // faixa completa: confere a duração anunciada antes de começar a decodificar
            val memoryLimit = ticket?.limit
            if (maxSeconds == null) checkFits(durationFrames, sampleRate, memoryLimit)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inFormat, null, null, 0)
            codec.start()

            // com a duração conhecida, aloca de uma vez (evita o buffer dobrando de tamanho)
            val out = ShortBuilder(
                if (maxSeconds == null && durationFrames > 0) ((durationFrames + 8192) * 2).toInt() else sampleRate * 2 * 30,
            )
            ticket?.bufferAllocated()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            try {
                while (!outputDone) {
                    check()
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val st = extractor.sampleTime
                                codec.queueInputBuffer(inIdx, 0, size, st, 0)
                                if (maxSeconds == null && durationUs > 0) {
                                    val pct = ((st * 100) / durationUs).toInt().coerceIn(0, 99)
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        onProgress(pct / 100f)
                                    }
                                }
                                extractor.advance()
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIdx >= 0 -> {
                            if (info.size > 0) {
                                val buf = codec.getOutputBuffer(outIdx)!!
                                buf.position(info.offset)
                                buf.limit(info.offset + info.size)
                                val shorts = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                                out.appendInterleaved(shorts, channels)
                                // arquivo sem duração confiável: confere também enquanto decodifica
                                if (maxSeconds == null) checkFits(out.frames.toLong(), sampleRate, memoryLimit)
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                            if (maxFrames != null && out.frames >= maxFrames) outputDone = true
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
            } finally {
                codec.stop()
                codec.release()
            }
            return Decoded(out.toArray(), sampleRate)
        } finally {
            extractor.release()
        }
    }

    private fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?: uri.lastPathSegment
            ?: "Faixa"

    /** Acumula PCM 16 bits como estéreo intercalado, convertendo mono/multicanal. */
    private class ShortBuilder(initial: Int) {
        private var data = ShortArray(initial.coerceAtLeast(1024) and 1.inv())
        private var size = 0

        val frames: Int get() = size / 2

        fun appendInterleaved(src: java.nio.ShortBuffer, channels: Int) {
            val frames = src.remaining() / channels
            ensure(size + frames * 2)
            for (f in 0 until frames) {
                val l = src.get()
                val r = if (channels >= 2) src.get() else l
                repeat(channels - 2) { src.get() } // descarta canais extras (nada se mono/estéreo)
                data[size++] = l
                data[size++] = r
            }
        }

        private fun ensure(needed: Int) {
            if (needed > data.size) data = data.copyOf(maxOf(needed, data.size * 2))
        }

        fun toArray(): ShortArray = data.copyOf(size)
    }
}
