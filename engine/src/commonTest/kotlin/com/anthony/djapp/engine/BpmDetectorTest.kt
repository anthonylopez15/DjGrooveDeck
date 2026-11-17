package com.anthony.djapp.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BpmDetectorTest {
    private val rate = 44100

    /** Bumbo (seno de 70 Hz com decaimento) a cada batida + chimbal de ruído na contra-batida. */
    private fun clickTrack(bpm: Double, seconds: Double, offsetSec: Double): ShortArray {
        val n = (rate * seconds).toInt()
        val buf = FloatArray(n)
        val period = 60.0 / bpm
        var seed = 12345
        var t = offsetSec
        while (t < seconds) {
            val s0 = (t * rate).toInt()
            for (i in 0 until (0.04 * rate).toInt()) {
                if (s0 + i < n) buf[s0 + i] += (0.9 * sin(2 * PI * 70 * i / rate) * exp(-i / (0.012 * rate))).toFloat()
            }
            val h0 = ((t + period / 2) * rate).toInt()
            val hLen = (0.008 * rate).toInt()
            for (i in 0 until hLen) {
                seed = seed * 1103515245 + 12345
                val r = ((seed shr 16) and 0x7fff) / 16384f - 1f
                if (h0 + i < n) buf[h0 + i] += 0.25f * r * (1f - i / hLen.toFloat())
            }
            t += period
        }
        val pcm = ShortArray(n * 2)
        for (i in 0 until n) {
            val s = (buf[i].coerceIn(-1f, 1f) * 32767).toInt().toShort()
            pcm[2 * i] = s
            pcm[2 * i + 1] = s
        }
        return pcm
    }

    /** Distância circular entre a primeira batida detectada e a esperada, em segundos. */
    private fun phaseError(grid: BeatGrid, expectedOffset: Double): Double {
        val period = 60.0 / grid.bpm
        val d = ((grid.firstBeatSec - expectedOffset) % period + period) % period
        return min(d, period - d)
    }

    @Test
    fun detects128BpmAndPhase() {
        val grid = BpmDetector.detect(clickTrack(128.0, 40.0, 0.1), rate)
        assertNotNull(grid)
        assertTrue(abs(grid.bpm - 128.0) < 0.2, "bpm=${grid.bpm}")
        assertTrue(phaseError(grid, 0.1) < 0.03, "fase=${phaseError(grid, 0.1)}")
    }

    @Test
    fun detects100BpmAndPhase() {
        val grid = BpmDetector.detect(clickTrack(100.0, 40.0, 0.25), rate)
        assertNotNull(grid)
        assertTrue(abs(grid.bpm - 100.0) < 0.2, "bpm=${grid.bpm}")
        assertTrue(phaseError(grid, 0.25) < 0.03, "fase=${phaseError(grid, 0.25)}")
    }

    @Test
    fun detectsFractionalBpm() {
        val grid = BpmDetector.detect(clickTrack(125.5, 60.0, 0.0), rate)
        assertNotNull(grid)
        assertTrue(abs(grid.bpm - 125.5) < 0.1, "bpm=${grid.bpm}")
    }

    @Test
    fun foldsFastTempoIntoRange() {
        // 170 BPM não cabe em [80,160): deve sair como 85 (metade)
        val grid = BpmDetector.detect(clickTrack(170.0, 40.0, 0.0), rate)
        assertNotNull(grid)
        assertTrue(abs(grid.bpm - 85.0) < 0.3, "bpm=${grid.bpm}")
    }

    @Test
    fun firstBeatIsWithinFirstPeriod() {
        val grid = BpmDetector.detect(clickTrack(128.0, 40.0, 0.3), rate)
        assertNotNull(grid)
        assertTrue(grid.firstBeatSec >= 0.0 && grid.firstBeatSec < 60.0 / grid.bpm + 1e-6)
    }

    @Test
    fun returnsNullForSilenceToneAndShortTracks() {
        assertNull(BpmDetector.detect(ShortArray(rate * 2 * 30), rate), "silêncio")

        val tone = ShortArray(rate * 2 * 30)
        for (i in 0 until rate * 30) {
            val s = (sin(2 * PI * 440 * i / rate) * 0.5 * 32767).toInt().toShort()
            tone[2 * i] = s
            tone[2 * i + 1] = s
        }
        assertNull(BpmDetector.detect(tone, rate), "tom contínuo")

        assertNull(BpmDetector.detect(clickTrack(128.0, 5.0, 0.0), rate), "faixa curta")
    }

    @Test
    fun trackExposesBeatGrid() {
        val t = Track("clicks", rate, clickTrack(120.0, 30.0, 0.0))
        assertNotNull(t.beatGrid)
        assertEquals(120.0, t.beatGrid.bpm, 0.2)
    }
}
