package com.anthony.djapp.engine

/** EQ de 3 bandas estilo mixer de DJ: low shelf 250 Hz, médio 1 kHz, high shelf 4 kHz. */
class Eq3(private val sampleRate: Int) {
    private val low = Biquad()
    private val mid = Biquad()
    private val high = Biquad()

    private var lowDb = Double.NaN
    private var midDb = Double.NaN
    private var highDb = Double.NaN

    /** Só recalcula os coeficientes quando o ganho muda. */
    fun update(lowDb: Double, midDb: Double, highDb: Double) {
        if (lowDb != this.lowDb) {
            this.lowDb = lowDb
            low.setLowShelf(sampleRate, 250.0, lowDb)
        }
        if (midDb != this.midDb) {
            this.midDb = midDb
            mid.setPeaking(sampleRate, 1000.0, 0.7, midDb)
        }
        if (highDb != this.highDb) {
            this.highDb = highDb
            high.setHighShelf(sampleRate, 4000.0, highDb)
        }
    }

    fun processL(x: Float): Float = high.processL(mid.processL(low.processL(x)))
    fun processR(x: Float): Float = high.processR(mid.processR(low.processR(x)))

    companion object {
        const val KILL_DB = -30.0
        const val MAX_DB = 6.0

        /** Knob 0..1 (0,5 = neutro) → dB. Totalmente à esquerda "mata" a banda. */
        fun knobToDb(knob: Float): Double {
            val k = knob.coerceIn(0f, 1f).toDouble()
            return if (k < 0.5) KILL_DB * (1.0 - k / 0.5) else MAX_DB * (k - 0.5) / 0.5
        }
    }
}
