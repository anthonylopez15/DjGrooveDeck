package com.anthony.djapp.ui

/**
 * Estado de um deck que dá para guardar em disco: de onde veio a faixa e como o deck estava.
 * O áudio não entra (é recarregado do arquivo); a grade de batidas sai de novo da análise.
 */
data class DeckSnapshot(
    val source: LibraryEntry,
    val positionSec: Double,
    val tempoPercent: Float,
    val tempoBeforeSync: Float,
    val volume: Float,
    val low: Float,
    val mid: Float,
    val high: Float,
    val syncOn: Boolean,
)

/**
 * Estado da sessão de DJ para sobreviver à morte do processo: os dois decks (null = vazio ou faixa
 * sem origem conhecida) e o que é do mixer.
 */
data class SessionSnapshot(
    val a: DeckSnapshot?,
    val b: DeckSnapshot?,
    val crossfader: Float,
    val master: Float,
    val keyLock: Boolean,
) {
    /** Texto simples "chave=valor" por linha; sem dependência de biblioteca de serialização. */
    fun encode(): String = buildString {
        line("v", VERSION.toString())
        line("crossfader", crossfader.toString())
        line("master", master.toString())
        line("keyLock", keyLock.toString())
        deck("a", a)
        deck("b", b)
    }

    private fun StringBuilder.line(key: String, value: String) {
        append(key).append('=').append(escape(value)).append('\n')
    }

    private fun StringBuilder.deck(p: String, d: DeckSnapshot?) {
        if (d == null) return
        line("$p.id", d.source.id)
        line("$p.name", d.source.name)
        line("$p.stamp", d.source.stamp.toString())
        line("$p.pos", d.positionSec.toString())
        line("$p.tempo", d.tempoPercent.toString())
        line("$p.tempoBeforeSync", d.tempoBeforeSync.toString())
        line("$p.volume", d.volume.toString())
        line("$p.low", d.low.toString())
        line("$p.mid", d.mid.toString())
        line("$p.high", d.high.toString())
        line("$p.sync", d.syncOn.toString())
    }

    companion object {
        private const val VERSION = 1

        /** Lê o que [encode] escreveu; null se o texto estiver vazio, corrompido ou de outra versão. */
        fun decode(text: String?): SessionSnapshot? {
            if (text.isNullOrBlank()) return null
            val m = HashMap<String, String>()
            for (raw in text.split('\n')) {
                val i = raw.indexOf('=')
                if (i > 0) m[raw.substring(0, i)] = unescape(raw.substring(i + 1))
            }
            if (m["v"]?.toIntOrNull() != VERSION) return null
            return try {
                SessionSnapshot(
                    a = deck(m, "a"),
                    b = deck(m, "b"),
                    crossfader = m.float("crossfader", 0.5f).coerceIn(0f, 1f),
                    master = m.float("master", 0.8f).coerceIn(0f, 1f),
                    keyLock = m["keyLock"]?.toBooleanStrictOrNull() ?: true,
                )
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        private fun deck(m: Map<String, String>, p: String): DeckSnapshot? {
            val id = m["$p.id"] ?: return null
            val name = m["$p.name"] ?: return null
            return DeckSnapshot(
                source = LibraryEntry(id, name, isFolder = false, stamp = m["$p.stamp"]?.toLongOrNull() ?: 0L),
                positionSec = (m["$p.pos"]?.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                tempoPercent = m.float("$p.tempo", 0f).coerceIn(-16f, 16f),
                tempoBeforeSync = m.float("$p.tempoBeforeSync", 0f).coerceIn(-16f, 16f),
                volume = m.float("$p.volume", 1f).coerceIn(0f, 1f),
                low = m.float("$p.low", 0.5f).coerceIn(0f, 1f),
                mid = m.float("$p.mid", 0.5f).coerceIn(0f, 1f),
                high = m.float("$p.high", 0.5f).coerceIn(0f, 1f),
                syncOn = m["$p.sync"]?.toBooleanStrictOrNull() ?: false,
            )
        }

        private fun Map<String, String>.float(key: String, default: Float): Float =
            this[key]?.toFloatOrNull()?.takeIf { !it.isNaN() } ?: default

        // o id de um arquivo tem "\n" (árvore + documento): escapa barra invertida e quebra de linha
        private fun escape(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n")

        private fun unescape(s: String): String {
            val out = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    out.append(if (s[i + 1] == 'n') '\n' else s[i + 1])
                    i += 2
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}
