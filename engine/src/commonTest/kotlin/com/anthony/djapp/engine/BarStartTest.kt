package com.anthony.djapp.engine

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Largada no tempo 1: o seguidor entra no próximo início de compasso do líder, no mesmo frame. */
class BarStartTest {
    private val rate = 44100
    private val chunk = 512

    private fun track(seconds: Int = 120) = Track("t", rate, ShortArray(rate * 2 * seconds), beatGrid = null)

    private class Rig(rate: Int, val leaderGrid: BeatGrid, val followerGrid: BeatGrid) {
        val mixer = DjMixer(rate)
        val leader = mixer.deckA
        val follower = mixer.deckB
    }

    private fun rig(
        leaderBpm: Double = 120.0,
        followerBpm: Double = 120.0,
        leaderPos: Double = 10.0,
        followerPos: Double = 33.3,
    ): Rig {
        // compassos a partir de 0,2 s (líder) e de 1,0 s (seguidor)
        val r = Rig(rate, BeatGrid(leaderBpm, LEADER_FIRST), BeatGrid(followerBpm, FOLLOWER_FIRST))
        r.leader.load(track())
        r.follower.load(track())
        r.leader.seekToFrame(leaderPos * rate)
        r.follower.seekToFrame(followerPos * rate)
        r.leader.play()
        r.mixer.render(FloatArray(chunk * 2), chunk) // o líder anda um bloco, aplicando a busca
        return r
    }

    private fun Rig.request() = mixer.requestAlign(follower, leader, followerGrid, leaderGrid, DjMixer.AlignMode.StartOnBar)

    private fun Rig.barGap(): Double {
        val pl = leaderGrid.barPhaseAt(leader.effectivePositionFrames / rate)
        val pf = followerGrid.barPhaseAt(follower.effectivePositionFrames / rate)
        val d = abs(pl - pf)
        return minOf(d, BeatGrid.BEATS_PER_BAR - d)
    }

    /** Renderiza blocos até o seguidor tocar; devolve quantos blocos esperou. */
    private fun Rig.runUntilStarted(maxBlocks: Int = 5000): Int {
        var n = 0
        while (!follower.playing && n < maxBlocks) {
            mixer.render(FloatArray(chunk * 2), chunk)
            n++
        }
        return n
    }

    @Test
    fun barPhaseAndNearestBarStart() {
        val g = BeatGrid(120.0, 0.25) // 0,5 s por batida, 2 s por compasso
        assertEquals(0.0, g.barPhaseAt(0.25), 1e-9)
        assertEquals(1.0, g.barPhaseAt(0.75), 1e-9)
        assertEquals(3.5, g.barPhaseAt(2.0), 1e-9)
        assertEquals(0.0, g.barPhaseAt(2.25), 1e-9, "volta ao tempo 1 a cada 4 batidas")
        assertEquals(2.25, g.nearestBarStart(3.1), 1e-9)
        assertEquals(4.25, g.nearestBarStart(3.4), 1e-9)
        assertEquals(0.25, g.nearestBarStart(0.0), 1e-9, "nunca antes da primeira batida")
    }

    @Test
    fun followerWaitsForTheLeadersNextDownbeatAndEntersExactlyOnIt() {
        val r = rig()
        r.request()
        assertTrue(r.follower.startPending, "enquanto espera, está pendente")
        r.mixer.render(FloatArray(chunk * 2), chunk)
        // a menos que o tempo 1 do líder caia justo neste bloco, o seguidor ainda está parado
        if (!r.follower.playing) assertTrue(r.follower.startPending)

        val waited = r.runUntilStarted()

        assertTrue(r.follower.playing)
        assertFalse(r.follower.startPending)
        assertTrue(waited * chunk.toDouble() / rate <= 2.0 + 0.1, "espera no máximo um compasso (2 s a 120 BPM)")
        // um bloco depois da largada, os dois estão no mesmo ponto do compasso (e da batida)
        r.mixer.render(FloatArray(chunk * 2), chunk)
        assertTrue(r.barGap() < 0.002, "tempos 1 coincidem: ${r.barGap()} batidas")
    }

    @Test
    fun followerStartsFromABarStartOfItsOwnGrid() {
        val r = rig(followerPos = 33.3)
        r.request()
        r.runUntilStarted()
        // no frame da largada o seguidor está num início de compasso: a fase de compasso dele, no instante em
        // que o líder está no tempo 1, é ~0
        val nearest = r.followerGrid.nearestBarStart(33.3)
        assertEquals(33.0, nearest, 1e-9)
        val pos = r.follower.positionFrames / rate
        // largou perto do tempo 1 mais próximo (até um bloco de diferença, mais o que andou depois)
        assertTrue(abs(pos - nearest) < 0.2, "posição ${pos}s perto do compasso ${nearest}s")
    }

    @Test
    fun differentBpmsStillCoincideOnTheDownbeat() {
        // o seguidor já está no andamento do líder (a sessão iguala antes): 126 BPM de 122 com tempo +3,28%
        val r = rig(leaderBpm = 126.0, followerBpm = 122.0)
        r.follower.tempo = 126.0 / 122.0
        r.request()
        r.runUntilStarted()
        r.mixer.render(FloatArray(chunk * 2), chunk)
        assertTrue(r.barGap() < 0.01, "tempos 1 coincidem: ${r.barGap()}")
    }

    @Test
    fun withoutAPlayingLeaderItStartsAtOnce() {
        val r = rig()
        r.leader.pause()
        r.request()
        r.mixer.render(FloatArray(chunk * 2), chunk)
        assertTrue(r.follower.playing, "sem líder tocando, não há o que esperar")
        assertFalse(r.follower.startPending)
    }

    @Test
    fun cancelDropsTheWaitAndKeepsTheFollowerStopped() {
        val r = rig()
        r.request()
        assertTrue(r.follower.startPending)
        r.mixer.cancelAlign()
        assertFalse(r.follower.startPending)
        repeat(300) { r.mixer.render(FloatArray(chunk * 2), chunk) }
        assertFalse(r.follower.playing, "cancelado: nunca larga")
    }

    @Test
    fun aLeaderDownbeatRightAtTheBlockStartStartsAtOnce() {
        // líder exatamente num tempo 1 (posição = primeira batida + compassos inteiros)
        val r = rig(leaderPos = LEADER_FIRST + 2.0 * 5)
        r.leader.pause()
        r.leader.seekToFrame((0.2 + 2.0 * 5) * rate)
        r.leader.play()
        r.request()
        val waited = r.runUntilStarted()
        assertTrue(waited <= 1, "tempo 1 do líder neste bloco: larga sem esperar um compasso (esperou $waited)")
    }

    private companion object {
        const val LEADER_FIRST = 0.2
        const val FOLLOWER_FIRST = 1.0
    }
}
