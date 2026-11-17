package com.anthony.djapp.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Exceção de desempenho do ADR 0001 (regra 5): posição e nível do medidor mudam a cada quadro e não
 * entram no [DjUiState] (recomporiam a tela inteira 60x/s). Ficam aqui, em estado Compose próprio,
 * atualizados por [DjViewModel.onFrame]. [positionSec] e [level]: leia só na fase de desenho (Canvas);
 * [elapsedTenths] muda 10x/s e serve para textos.
 */
class DeckMeters {
    var positionSec by mutableDoubleStateOf(0.0)
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var elapsedTenths by mutableIntStateOf(0)
        private set

    internal fun update(position: Double, peak: Float) {
        positionSec = position
        level = peak
        elapsedTenths = (position * 10).toInt()
    }

    /** Fração 0..1 da faixa já tocada. */
    fun progress(durationSec: Double): Float =
        if (durationSec > 0.0) (positionSec / durationSec).toFloat().coerceIn(0f, 1f) else 0f
}
