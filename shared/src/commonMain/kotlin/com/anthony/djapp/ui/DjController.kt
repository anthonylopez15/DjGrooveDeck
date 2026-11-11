package com.anthony.djapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.anthony.djapp.engine.Deck
import com.anthony.djapp.engine.DjMixer
import com.anthony.djapp.engine.Eq3
import com.anthony.djapp.engine.Track

/** Estado observável (Compose) de um deck, ligado ao [Deck] do motor. */
class DeckController(private val deck: Deck) {
    var track by mutableStateOf<Track?>(null)
        private set
    var playing by mutableStateOf(false)
        private set

    /** Posição em segundos, atualizada a cada frame. Leia só em fase de desenho (Canvas). */
    var positionSec by mutableDoubleStateOf(0.0)
        private set

    /** Nível de pico (0..1) para o medidor, a cada frame. Leia só em fase de desenho. */
    var level by mutableFloatStateOf(0f)
        private set

    /** Posição em décimos de segundo: muda 10x/s, segura para textos. */
    var elapsedTenths by mutableIntStateOf(0)
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

    /** Nome da faixa em decodificação, ou null se não há carregamento em curso. */
    var loadingName by mutableStateOf<String?>(null)
        private set

    /** Progresso 0..1 da decodificação em curso. */
    var loadProgress by mutableFloatStateOf(0f)
        private set

    val loading: Boolean get() = loadingName != null

    fun startLoading(name: String) {
        loadProgress = 0f
        loadingName = name
    }

    fun updateLoadProgress(p: Float) {
        loadProgress = p.coerceIn(0f, 1f)
    }

    fun cancelLoading() {
        loadingName = null
    }

    val hasTrack: Boolean get() = track != null
    val title: String get() = track?.name ?: ""
    val durationSec: Double get() = track?.durationSec ?: 0.0
    val durationTenths: Int get() = (durationSec * 10).toInt()

    fun progress(): Float {
        val d = durationSec
        return if (d > 0.0) (positionSec / d).toFloat().coerceIn(0f, 1f) else 0f
    }

    fun load(track: Track) {
        deck.load(track)
        this.track = track
        loadingName = null
        poll()
    }

    fun togglePlay() = deck.togglePlay()
    fun cue() = deck.cue()
    fun seek(fraction: Float) = deck.seekToFraction(fraction)

    /** Arrastar a onda ampliada: move a posição em [deltaSec] segundos. */
    fun scrubBySeconds(deltaSec: Double) {
        val d = durationSec
        if (d <= 0.0) return
        val target = (positionSec + deltaSec).coerceIn(0.0, d)
        positionSec = target
        deck.seekToFraction((target / d).toFloat())
    }

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

    /** Chamado a cada frame pela UI para refletir a posição vinda da thread de áudio. */
    fun poll() {
        val t = deck.track
        playing = deck.playing
        level = deck.level
        if (t != null) {
            val s = deck.positionFrames / t.sampleRate
            positionSec = s
            elapsedTenths = (s * 10).toInt()
        } else {
            positionSec = 0.0
            elapsedTenths = 0
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

    fun deck(id: DeckId): DeckController = if (id == DeckId.A) a else b

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
