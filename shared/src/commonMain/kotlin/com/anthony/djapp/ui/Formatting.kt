package com.anthony.djapp.ui



// Formatação de números para a tela (funções puras, testadas em FormatTest).

/** Percentual para exibir: 0..1 vira "0%".."100%". */
internal fun formatPercent(fraction: Float): String = "${(fraction * 100f + 0.5f).toInt()}%"

/** Knob de EQ (0,5 = neutro): "-100%" (corta) .. "+100%". */
internal fun formatKnobPercent(knob: Float): String {
    val p = ((knob - 0.5f) * 200f).let { if (it >= 0) (it + 0.5f).toInt() else (it - 0.5f).toInt() }
    return if (p > 0) "+$p%" else "$p%"
}

internal fun formatBpm(bpm: Double): String {
    val t = kotlin.math.round(bpm * 10).toInt()
    return "${t / 10}.${t % 10}"
}

internal fun formatTempo(p: Float): String {
    val t = kotlin.math.round(p * 10).toInt()
    val sign = if (t >= 0) "+" else "-"
    val a = kotlin.math.abs(t)
    return "$sign${a / 10}.${a % 10}%"
}

/** Décimos de segundo → m:ss.t */
internal fun formatTenths(tenths: Int): String {
    val t = tenths.coerceAtLeast(0)
    val s = (t / 10) % 60
    return "${t / 600}:${if (s < 10) "0" else ""}$s.${t % 10}"
}
