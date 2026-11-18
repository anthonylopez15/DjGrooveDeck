package com.anthony.djapp.data

import com.anthony.djapp.domain.AutoFade
import com.anthony.djapp.domain.DeckId
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Auto fade: com o botão ligado, o crossfader todo do lado de um deck que toca vai suave até a ponta do outro. */
class AutoFadeTest {
    private val rate = 44100

    private class Rig {
        val mixer = DjMixer(44100)
        var now = 1_000_000L
        val c = testSession(mixer = mixer).also { it.nowMs = { now } }
    }

    private fun rig(): Rig = Rig().also {
        it.c.loadTrack(DeckId.A, Track("a", rate, ShortArray(rate * 2 * 60)))
        it.c.loadTrack(DeckId.B, Track("b", rate, ShortArray(rate * 2 * 60)))
    }

    /** Avança o relógio [ms] em quadros de 16 ms, como a tela faz. */
    private fun Rig.run(ms: Long) {
        var left = ms
        while (left > 0) {
            val dt = minOf(16L, left)
            now += dt
            left -= dt
            c.syncFromEngine()
        }
    }

    // ---------- regra pura ----------

    @Test
    fun startsOnlyInTheZoneOfAPlayingDeck() {
        assertTrue(AutoFade.shouldStart(0f, aPlaying = true, bPlaying = false))
        assertTrue(AutoFade.shouldStart(1f, aPlaying = false, bPlaying = true))
        assertFalse(AutoFade.shouldStart(0f, aPlaying = false, bPlaying = true), "o lado de A está parado")
        assertTrue(AutoFade.shouldStart(0.25f, aPlaying = true, bPlaying = false), "até 25% ainda vale")
        assertTrue(AutoFade.shouldStart(0.75f, aPlaying = false, bPlaying = true), "a partir de 75% vale")
        assertFalse(AutoFade.shouldStart(0.3f, aPlaying = true, bPlaying = true), "fora da zona")
        assertFalse(AutoFade.shouldStart(0.7f, aPlaying = true, bPlaying = true), "fora da zona")
        assertFalse(AutoFade.shouldStart(0.2f, aPlaying = false, bPlaying = true), "o deck daquele lado está parado")
    }

    @Test
    fun targetIsTheOppositeEdge() {
        assertEquals(1f, AutoFade.targetFor(0f))
        assertEquals(0f, AutoFade.targetFor(1f))
    }

    @Test
    fun stepNeverPassesTheTargetAndCrossesTheWholeFaderInTheGlideTime() {
        var x = 0f
        var ms = 0L
        while (x != 1f) {
            x = AutoFade.next(x, 1f, 16)
            ms += 16
        }
        assertTrue(ms in AutoFade.GLIDE_MS - 100..AutoFade.GLIDE_MS + 100, "levou $ms ms")
        assertEquals(1f, AutoFade.next(0.999f, 1f, 100))
        assertTrue(AutoFade.next(0f, 1f, 100_000L) < 0.03f, "um intervalo longo conta só ${AutoFade.MAX_STEP_MS} ms")
    }

    // ---------- sessão ----------

    @Test
    fun offByDefaultTheCrossfaderStaysPut() {
        val r = rig()
        r.c.changeCrossfader(0f)
        r.c.togglePlay(DeckId.A)
        r.run(10_000)
        assertEquals(0f, r.c.state.value.crossfader)
    }

    @Test
    fun fadesFromDeckAEdgeToDeckBEdgeAndStaysThere() {
        val r = rig()
        r.c.toggleAutoFade()
        r.c.changeCrossfader(0f)
        r.c.togglePlay(DeckId.A)

        r.run(AutoFade.GLIDE_MS / 2)
        val half = r.c.state.value.crossfader
        assertTrue(half in 0.45f..0.55f, "no meio do caminho: $half")
        assertEquals(half, r.mixer.crossfader, "o motor acompanha")

        r.run(AutoFade.GLIDE_MS)
        assertEquals(1f, r.c.state.value.crossfader)
        assertFalse(r.c.state.value.autoFade, "ao chegar o botão solta")
        r.c.togglePlay(DeckId.A) // com o botão solto, tocar de novo não faz o fade voltar
        r.run(AutoFade.GLIDE_MS * 2)
        assertEquals(1f, r.c.state.value.crossfader, "sem vai e volta")
    }

    @Test
    fun aPausedDestinationStartsWhenTheFadeStartsAndTheOriginPausesOnArrival() {
        val r = rig()
        r.c.toggleAutoFade()
        r.c.changeCrossfader(0f)
        r.c.togglePlay(DeckId.A)
        assertFalse(r.c.b.playingNow)

        r.run(32)
        assertTrue(r.c.b.playingNow, "o deck B acordou")
        assertTrue(r.c.a.playingNow, "o A segue tocando durante o fade")

        r.run(AutoFade.GLIDE_MS / 2)
        assertTrue(r.c.a.playingNow, "ainda no meio do fade")

        r.run(AutoFade.GLIDE_MS)
        assertTrue(r.c.b.playingNow)
        assertFalse(r.c.a.playingNow, "chegou: o A pausou")
        assertFalse(r.c.state.value.autoFade, "o botão soltou")
    }

    @Test
    fun aDestinationAlreadyPlayingIsLeftAloneAndTheOriginKeepsPlaying() {
        val r = rig()
        r.c.togglePlay(DeckId.B)
        r.c.togglePlay(DeckId.A)
        r.c.toggleAutoFade()
        r.c.changeCrossfader(0f)
        r.run(AutoFade.GLIDE_MS + 500)
        assertEquals(1f, r.c.state.value.crossfader)
        assertTrue(r.c.a.playingNow && r.c.b.playingNow)
    }

    @Test
    fun movingByHandMidFadeKeepsBothDecksPlaying() {
        val r = rig()
        r.c.toggleAutoFade()
        r.c.changeCrossfader(0f)
        r.c.togglePlay(DeckId.A)
        r.run(1_000)
        r.c.changeCrossfader(0.4f)
        r.run(AutoFade.GLIDE_MS * 2)
        assertTrue(r.c.a.playingNow && r.c.b.playingNow, "sem o fade completo ninguém é pausado")
    }

    @Test
    fun aDestinationWithoutATrackIsNotStarted() {
        val r = Rig()
        r.c.loadTrack(DeckId.A, Track("a", rate, ShortArray(rate * 2 * 60)))
        r.c.toggleAutoFade()
        r.c.changeCrossfader(0f)
        r.c.togglePlay(DeckId.A)
        r.run(AutoFade.GLIDE_MS + 500)
        assertFalse(r.c.b.playingNow)
        assertTrue(r.c.a.playingNow, "sem destino tocando, o A não é pausado")
    }

    @Test
    fun startsFromInsideTheZoneAndStillGoesToTheOppositeEdge() {
        val r = rig()
        r.c.toggleAutoFade()
        r.c.changeCrossfader(0.2f)
        r.c.togglePlay(DeckId.A)
        r.run(AutoFade.GLIDE_MS + 500)
        assertEquals(1f, r.c.state.value.crossfader)
        r.c.toggleAutoFade()
        r.c.changeCrossfader(0.8f)
        r.c.togglePlay(DeckId.A) // A volta a tocar; o crossfader está na zona do B, que toca
        r.run(AutoFade.GLIDE_MS + 500)
        assertEquals(0f, r.c.state.value.crossfader)
    }

    @Test
    fun worksFromDeckBEdgeToDeckAEdge() {
        val r = rig()
        r.c.toggleAutoFade()
        r.c.changeCrossfader(1f)
        r.c.togglePlay(DeckId.B)
        r.run(AutoFade.GLIDE_MS + 500)
        assertEquals(0f, r.c.state.value.crossfader)
    }

    @Test
    fun pressingTheButtonAgainAfterArrivingFadesBack() {
        val r = rig()
        r.c.toggleAutoFade()
        r.c.changeCrossfader(0f)
        r.c.togglePlay(DeckId.A)
        r.run(AutoFade.GLIDE_MS + 500)
        assertEquals(1f, r.c.state.value.crossfader)
        r.c.togglePlay(DeckId.A) // o A pausou ao chegar; toca de novo, aperta o botão e leva o crossfader à ponta dele
        r.c.toggleAutoFade()
        r.c.changeCrossfader(0f)
        r.run(AutoFade.GLIDE_MS / 2)
        assertTrue(r.c.state.value.crossfader > 0.4f, "recomeçou")
    }

    @Test
    fun doesNothingWhileTheDeckOnThatSideIsStopped() {
        val r = rig()
        r.c.toggleAutoFade()
        r.c.changeCrossfader(0f)
        r.c.togglePlay(DeckId.B) // toca só o B, o crossfader está do lado do A
        r.run(10_000)
        assertEquals(0f, r.c.state.value.crossfader)
    }

    @Test
    fun movingTheCrossfaderByHandCancelsTheFade() {
        val r = rig()
        r.c.toggleAutoFade()
        r.c.changeCrossfader(0f)
        r.c.togglePlay(DeckId.A)
        r.run(1_000)
        r.c.changeCrossfader(0.3f)
        r.run(1_000)
        assertEquals(0.3f, r.c.state.value.crossfader, "o toque manual vence e não retoma fora da ponta")
    }

    @Test
    fun turningTheButtonOffStopsTheFade() {
        val r = rig()
        r.c.toggleAutoFade()
        r.c.changeCrossfader(0f)
        r.c.togglePlay(DeckId.A)
        r.run(1_000)
        r.c.toggleAutoFade()
        val stopped = r.c.state.value.crossfader
        r.run(3_000)
        assertEquals(stopped, r.c.state.value.crossfader)
        assertFalse(r.c.state.value.autoFade)
    }

    @Test
    fun theFadeKeepsGoingToTheEndWhenTheOriginStopsOrItsTrackEnds() {
        val r = rig()
        r.c.toggleAutoFade()
        r.c.changeCrossfader(0f)
        r.c.togglePlay(DeckId.A)
        r.run(1_000)
        r.c.togglePlay(DeckId.A) // a faixa de origem acabou (ou foi pausada) no meio do fade
        assertFalse(r.c.a.playingNow)
        r.run(AutoFade.GLIDE_MS)
        assertEquals(1f, r.c.state.value.crossfader, "chegou à ponta mesmo assim")
        assertTrue(r.c.b.playingNow)
        assertFalse(r.c.state.value.autoFade)
    }

    @Test
    fun aLongPauseBetweenFramesDoesNotJump() {
        val r = rig()
        r.c.toggleAutoFade()
        r.c.changeCrossfader(0f)
        r.c.togglePlay(DeckId.A)
        r.run(16)
        r.now += 60_000 // tela fora de cena por um minuto
        r.c.syncFromEngine()
        assertTrue(r.c.state.value.crossfader < 0.05f, "andou no máximo um passo: ${r.c.state.value.crossfader}")
    }
}
