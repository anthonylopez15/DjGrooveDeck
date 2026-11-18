package com.anthony.djapp.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BeatGridEditingTest {
    @Test
    fun bpmIsClampedAndRounded() {
        assertEquals(40.0, BeatGridEditing.clampBpm(10.0))
        assertEquals(220.0, BeatGridEditing.clampBpm(400.0))
        assertEquals(126.35, BeatGridEditing.clampBpm(126.3456))
        assertEquals(127.0, BeatGridEditing.adjustBpm(126.0, 1.0))
        assertEquals(125.9, BeatGridEditing.adjustBpm(126.0, -0.1))
        assertEquals(40.0, BeatGridEditing.adjustBpm(40.0, -5.0), "não passa do mínimo")
    }

    @Test
    fun firstBeatIsKeptInsideTheFirstBeatOfTheTrack() {
        // 120 BPM = 0,5 s por batida
        assertEquals(0.25, BeatGridEditing.normalizeFirstBeat(0.25, 120.0), 1e-9)
        assertEquals(0.25, BeatGridEditing.normalizeFirstBeat(10.25, 120.0), 1e-9)
        assertEquals(0.25, BeatGridEditing.anchorAt(37.75, 120.0), 1e-9)
        // negativo (grade empurrada para trás) volta para dentro do primeiro período
        assertEquals(0.45, BeatGridEditing.nudge(0.05, -0.1, 120.0), 1e-9)
    }

    @Test
    fun nudgeMovesTheGridByTenMilliseconds() {
        assertEquals(0.26, BeatGridEditing.nudge(0.25, BeatGridEditing.NUDGE_SEC, 120.0), 1e-9)
        assertEquals(0.24, BeatGridEditing.nudge(0.25, -BeatGridEditing.NUDGE_SEC, 120.0), 1e-9)
    }

    @Test
    fun tapTempoNeedsTwoTapsAndAveragesTheIntervals() {
        val t = TapTempo()
        assertNull(t.tap(10.0))
        assertEquals(120.0, assertNotNull(t.tap(10.5)), 1e-9)
        assertEquals(120.0, assertNotNull(t.tap(11.0)), 1e-9)
        // toque um pouco atrasado: a média suaviza (3 intervalos em 1,6 s = 112,5 BPM)
        assertEquals(112.5, assertNotNull(t.tap(11.6)), 1e-9)
        assertEquals(4, t.count)
    }

    @Test
    fun aLongPauseRestartsTheCount() {
        val t = TapTempo(maxGapSec = 2.0)
        t.tap(0.0)
        t.tap(0.5)
        assertNull(t.tap(10.0), "pausa de 9,5 s: recomeça, só há um toque")
        assertEquals(1, t.count)
        assertEquals(60.0, assertNotNull(t.tap(11.0)), 1e-9)
    }

    @Test
    fun onlyTheLastTapsCount() {
        val t = TapTempo(keep = 4)
        // primeiro um andamento lento (60 BPM), depois rápido (120 BPM): só os 4 últimos valem
        listOf(0.0, 1.0, 2.0, 3.0).forEach { t.tap(it) }
        listOf(3.5, 4.0, 4.5, 5.0).forEach { t.tap(it) }
        assertEquals(4, t.count)
        assertEquals(120.0, assertNotNull(t.tap(5.5)), 1e-9)
    }

    @Test
    fun resetClears() {
        val t = TapTempo()
        t.tap(0.0)
        t.reset()
        assertEquals(0, t.count)
        assertTrue(t.tap(1.0) == null)
    }
}
