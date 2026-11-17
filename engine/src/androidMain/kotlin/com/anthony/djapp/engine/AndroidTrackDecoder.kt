package com.anthony.djapp.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteOrder

/** Decodifica qualquer áudio suportado pelo Android (MP3, AAC, FLAC, WAV, OGG...) para [Track]. */
object AndroidTrackDecoder {
    /** Trecho decodificado na análise rápida: o suficiente para o detector de BPM. */
    private const val ANALYSIS_SECONDS = 100.0

    /**
     * Uma decodificação completa por vez: cada uma confere a memória livre antes de começar
     * ([MemoryBudget]); duas ao mesmo tempo (ex.: restaurar os dois decks) passariam juntas na conferência
     * e somariam os picos.
     */
    private val fullDecodeLock = Mutex()

    /** [onProgress] recebe 0..1 conforme a decodificação avança (chamado fora da thread principal). */
    suspend fun decode(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit = {},
    ): Track = fullDecodeLock.withLock {
        withContext(Dispatchers.Default) {
            val name = displayName(context, uri)
            try {
                val d = decodeRange(
                    context,
                    uri,
                    startUs = 0L,
                    maxSeconds = null,
                    onProgress = onProgress,
                    memoryLimit = currentMemoryLimit(),
                ) { ensureActive() }
                onProgress(1f)
                Track(name, d.sampleRate, d.pcm)
            } catch (e: OutOfMemoryError) {
                // OutOfMemoryError é Error, não Exception: sem isto o app fecha em vez de avisar
                throw IllegalStateException("Memória insuficiente para carregar esta faixa", e)
            }
        }
    }

    /**
     * Limite para a próxima faixa, com a memória em uso agora (inclusive a do outro deck). Se a memória
     * livre é o que limita, pede uma coleta antes de medir de novo: lixo ainda não coletado não conta.
     */
    @Suppress("ExplicitGarbageCollectionCall") // proposital: lixo ainda não coletado não é memória ocupada
    private fun currentMemoryLimit(): MemoryBudget.Limit {
        val rt = Runtime.getRuntime()
        fun measure() = MemoryBudget.limit(rt.maxMemory(), rt.totalMemory() - rt.freeMemory())
        val first = measure()
        if (!first.byFreeMemory) return first
        System.gc()
        return measure()
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
     * Decodifica a partir de [startUs]; se [maxSeconds] não for null, para depois desse tanto de áudio.
     * [check] é chamado a cada volta do laço (use para propagar cancelamento).
     */
    private fun decodeRange(
        context: Context,
        uri: Uri,
        startUs: Long,
        maxSeconds: Double?,
        onProgress: (Float) -> Unit,
        memoryLimit: MemoryBudget.Limit? = null,
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
            // faixa completa: primeiro a regra do app (10 min), depois a memória do aparelho
            if (maxSeconds == null && TrackLimits.tooLong(durationUs / 1_000_000.0)) error(TrackLimits.TOO_LONG_MESSAGE)
            if (memoryLimit != null && durationFrames > memoryLimit.frames) {
                error(MemoryBudget.tooLongMessage(memoryLimit, sampleRate))
            }

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inFormat, null, null, 0)
            codec.start()

            // com a duração conhecida, aloca de uma vez (evita o buffer dobrando de tamanho)
            val out = ShortBuilder(
                if (maxSeconds == null && durationFrames > 0) ((durationFrames + 8192) * 2).toInt() else sampleRate * 2 * 30,
            )
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
                                if (maxSeconds == null && TrackLimits.tooLong(out.frames.toDouble() / sampleRate)) {
                                    error(TrackLimits.TOO_LONG_MESSAGE)
                                }
                                if (memoryLimit != null && out.frames > memoryLimit.frames) {
                                    error(MemoryBudget.tooLongMessage(memoryLimit, sampleRate))
                                }
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
