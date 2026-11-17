package com.anthony.djapp.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Teste de carga do motor: dois decks com key lock tocando enquanto uma sequência pseudo-aleatória
 * (mas fixa, para ser reproduzível) de operações bate no motor sem parar: buscas, tempo, EQ, volume,
 * crossfader, alinhamento de batidas, troca de faixa, liga/desliga do key lock.
 * Verifica o que nunca pode acontecer: saída inválida (NaN/Infinito), estouro acima de ±1, posição
 * fora da faixa ou exceção. E mede quanto do tempo real o render consome (fator de tempo real).
 */
class EngineStressTest {
    private val rate = 44100
    private val chunk = 512

    private var seed = 20260101L
    private fun next(): Int {
        seed = seed * 6364136223846793005L + 1442695040888963407L
        return ((seed ushr 33) and 0x7fffffff).toInt()
    }
    private fun rnd(n: Int) = next() % n
    private fun rndD(lo: Double, hi: Double) = lo + (hi - lo) * (next() % 10000) / 10000.0

    /** "Música" sintética: grave, acorde e chimbais ruidosos em andamento fixo. */
    private fun song(bpm: Double, seconds: Double): Track {
        val n = (rate * seconds).toInt()
        val buf = FloatArray(n)
        val period = 60.0 / bpm
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            var v = 0.25 * sin(2 * PI * 55 * t) + 0.12 * sin(2 * PI * 220 * t) + 0.1 * sin(2 * PI * 330 * t)
            val ph = (t % period)
            v += 0.5 * exp(-ph * 40) * sin(2 * PI * 70 * ph) // bumbo
            val hp = ((t + period / 2) % period)
            if (hp < 0.01) v += 0.15 * (((next() % 2000) - 1000) / 1000.0) // chimbal
            buf[i] = v.toFloat()
        }
        val pcm = ShortArray(n * 2)
        for (i in 0 until n) {
            val s = (buf[i].coerceIn(-1f, 1f) * 30000).toInt().toShort()
            pcm[2 * i] = s
            pcm[2 * i + 1] = s
        }
        return Track("song$bpm", rate, pcm, beatGrid = BeatGrid(bpm, 0.0))
    }

    @Test
    fun randomOperationsNeverBreakTheEngine() {
        val mixer = DjMixer(rate)
        val tracks = listOf(song(124.0, 40.0), song(128.0, 40.0), song(132.0, 40.0), song(120.0, 40.0))
        mixer.deckA.load(tracks[0])
        mixer.deckB.load(tracks[1])
        mixer.deckA.play()
        mixer.deckB.play()

        val out = FloatArray(chunk * 2)
        val blocks = 4000 // ~46 s de áudio
        var ops = 0
        var maxAbs = 0f
        val started = TimeSource.Monotonic.markNow()

        for (b in 0 until blocks) {
            // entre 0 e 3 operações por bloco, como um DJ muito nervoso
            repeat(rnd(4)) {
                val deck = if (rnd(2) == 0) mixer.deckA else mixer.deckB
                val other = if (deck === mixer.deckA) mixer.deckB else mixer.deckA
                val t = deck.track ?: return@repeat
                ops++
                when (rnd(11)) {
                    0 -> deck.seekToFraction(rnd(1000) / 1000f)
                    1 -> deck.tempo = rndD(0.84, 1.16)
                    2 -> mixer.keyLock = !mixer.keyLock
                    3 -> {
                        deck.lowDb = rndD(-30.0, 6.0)
                        deck.midDb = rndD(-30.0, 6.0)
                        deck.highDb = rndD(-30.0, 6.0)
                    }
                    4 -> deck.volume = rnd(101) / 100f
                    5 -> mixer.crossfader = rnd(101) / 100f
                    6 -> mixer.masterVolume = rnd(101) / 100f
                    7 -> deck.restart()
                    8 -> {
                        val ot = other.track
                        if (ot != null) {
                            mixer.requestAlign(deck, other, BeatGrid(124.0, 0.0), BeatGrid(128.0, 0.0), startFollower = rnd(2) == 0)
                        }
                    }
                    9 -> { // troca de faixa só com o deck parado (a regra do app)
                        deck.pause()
                        if (deck.load(tracks[rnd(tracks.size)])) deck.play()
                    }
                    10 -> if (deck.playing) deck.pause() else deck.play()
                }
                assertTrue(t.frames > 0)
            }

            out.fill(0f)
            mixer.render(out, chunk)
            for (x in out) {
                assertTrue(!x.isNaN() && !x.isInfinite(), "saída inválida no bloco $b: $x")
                val a = abs(x)
                if (a > maxAbs) maxAbs = a
            }
            for (d in listOf(mixer.deckA, mixer.deckB)) {
                val len = d.track?.frames?.toDouble() ?: 0.0
                assertTrue(d.positionFrames >= 0.0 && d.positionFrames <= len + 1.0, "posição fora da faixa: ${d.positionFrames}")
                assertTrue(!d.level.isNaN() && d.level >= 0f)
            }
        }

        val elapsed = started.elapsedNow().toDouble(DurationUnit.SECONDS)
        val audioSeconds = blocks * chunk.toDouble() / rate
        val rtf = elapsed / audioSeconds
        println("STRESS: $ops operações em $blocks blocos (${audioSeconds.toInt()} s de áudio)")
        println("STRESS_RTF=$rtf pico=$maxAbs")
        assertTrue(maxAbs <= 1.0001f, "pico acima de 1: $maxAbs")
        assertTrue(rtf < 1.0, "render mais lento que o tempo real: $rtf")
    }

    @Test
    fun manyShortSeeksUnderKeyLockStayStable() {
        // arrastar a onda: centenas de buscas seguidas com o stretcher ligado
        val deck = Deck(rate)
        deck.load(song(126.0, 60.0))
        deck.tempo = 1.1
        deck.keyLock = true
        deck.play()
        val buf = FloatArray(chunk * 2)
        for (b in 0 until 3000) {
            if (b % 2 == 0) deck.seekToFraction(rnd(1000) / 1000f)
            buf.fill(0f)
            deck.renderAdd(buf, chunk, 1f)
            for (x in buf) assertTrue(!x.isNaN() && abs(x) <= 1.5f, "valor inválido: $x")
        }
        assertEquals(true, deck.playing || deck.positionFrames >= 0.0)
    }
}
