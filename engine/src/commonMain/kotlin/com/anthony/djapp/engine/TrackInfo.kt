package com.anthony.djapp.engine

/** Resumo de uma faixa para listas: duração e BPM (null se não houver ritmo claro). */
data class TrackInfo(val durationSec: Double, val bpm: Double?)
