package com.anthony.djapp.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder

/** Decodifica qualquer áudio suportado pelo Android (MP3, AAC, FLAC, WAV, OGG...) para [Track]. */
object AndroidTrackDecoder {

    suspend fun decode(context: Context, uri: Uri): Track = withContext(Dispatchers.Default) {
        val name = displayName(context, uri)
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        try {
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("Arquivo sem faixa de áudio")
            extractor.selectTrack(trackIndex)
            val inFormat = extractor.getTrackFormat(trackIndex)
            val mime = inFormat.getString(MediaFormat.KEY_MIME)!!

            var sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inFormat, null, null, 0)
            codec.start()

            val out = ShortBuilder(sampleRate * 2 * 30) // 30 s iniciais; cresce sob demanda
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            try {
                while (!outputDone) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
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
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
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
            Track(name, sampleRate, out.toArray())
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

        fun appendInterleaved(src: java.nio.ShortBuffer, channels: Int) {
            val frames = src.remaining() / channels
            ensure(size + frames * 2)
            for (f in 0 until frames) {
                val l = src.get()
                val r = if (channels >= 2) src.get() else l
                for (c in 2 until channels) src.get() // descarta canais extras
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
