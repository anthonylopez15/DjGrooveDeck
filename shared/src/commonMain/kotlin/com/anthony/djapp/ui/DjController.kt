package com.anthony.djapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.anthony.djapp.engine.Deck
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.Eq3
import com.anthony.djapp.engine.Track

/** Estado observável (Compose) de um deck, ligado ao [Deck] do motor. */
class DeckController(private val deck: Deck) {
    var title by mutableStateOf("Sem faixa")
        private set
    var peaks by mutableStateOf(FloatArray(0))
        private set
    var playing by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var elapsedSec by mutableDoubleStateOf(0.0)
        private set
    var durationSec by mutableDoubleStateOf(0.0)
        private set

    /** Ajuste de tempo em % (-16..+16). */
    var tempoPercent by mutableFloatStateOf(0f)
        private set
    var volume by mutableFloatStateOf(1f)
        private set
    var low by mutableFloatStateOf(0.5f)
        private set
    var mid by mutableFloatStateOf(0.5f)
        private set
    var high by mutableFloatStateOf(0.5f)
        private set

    val hasTrack: Boolean get() = durationSec > 0.0

    fun load(track: Track) {
        deck.load(track)
        title = track.name
        peaks = track.peaks
        durationSec = track.durationSec
        poll()
    }

    fun togglePlay() = deck.togglePlay()
    fun cue() = deck.cue()
    fun seek(fraction: Float) = deck.seekToFraction(fraction)

    fun changeTempo(p: Float) {
        tempoPercent = p.coerceIn(-16f, 16f)
        deck.tempo = 1.0 + tempoPercent / 100.0
    }

    fun changeVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        deck.volume = volume
    }

    fun changeLow(k: Float) {
        low = k.coerceIn(0f, 1f)
        deck.lowDb = Eq3.knobToDb(low)
    }

    fun changeMid(k: Float) {
        mid = k.coerceIn(0f, 1f)
        deck.midDb = Eq3.knobToDb(mid)
    }

    fun changeHigh(k: Float) {
        high = k.coerceIn(0f, 1f)
        deck.highDb = Eq3.knobToDb(high)
    }

    /** Chamado a cada ~33 ms pela UI para refletir a posição vinda da thread de áudio. */
    fun poll() {
        val t = deck.track
        playing = deck.playing
        if (t != null && t.frames > 0) {
            val frac = (deck.positionFrames / t.frames).toFloat().coerceIn(0f, 1f)
            progress = frac
            elapsedSec = deck.positionFrames / t.sampleRate
        } else {
            progress = 0f
            elapsedSec = 0.0
        }
    }
}

class DjController(private val mixer: DjMixer) {
    val a = DeckController(mixer.deckA)
    val b = DeckController(mixer.deckB)

    var crossfader by mutableFloatStateOf(0.5f)
        private set
    var master by mutableFloatStateOf(0.8f)
        private set

    fun changeCrossfader(x: Float) {
        crossfader = x.coerceIn(0f, 1f)
        mixer.crossfader = crossfader
    }

    fun changeMaster(v: Float) {
        master = v.coerceIn(0f, 1f)
        mixer.masterVolume = master
    }

    fun poll() {
        a.poll()
        b.poll()
    }
}

enum class DeckId { A, B }
