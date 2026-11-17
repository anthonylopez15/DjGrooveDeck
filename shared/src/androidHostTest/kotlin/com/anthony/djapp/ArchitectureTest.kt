package com.anthony.djapp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Regras do ADR 0001 verificadas no código-fonte: o domínio não depende de plataforma nem de tela.
 * (Roda na JVM, onde dá para ler os arquivos; vale para o código comum de todas as plataformas.)
 */
class ArchitectureTest {
    private val domainDir = File("src/commonMain/kotlin/com/anthony/djapp/domain")

    private val forbidden = listOf(
        "import android.",
        "import androidx.",
        "import platform.", // iOS
        "import com.anthony.djapp.ui.",
        "import com.anthony.djapp.presentation.",
        "import com.anthony.djapp.data.",
    )

    @Test
    fun domainDependsOnlyOnEngineAndKotlin() {
        val files = domainDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "pasta do domínio não encontrada: ${domainDir.absolutePath}")
        val violations = files.flatMap { f ->
            f.readLines().filter { line -> forbidden.any { line.trim().startsWith(it) } }.map { "${f.name}: ${it.trim()}" }
        }
        if (violations.isNotEmpty()) fail("o domínio importa o que não pode (ADR 0001):\n" + violations.joinToString("\n"))
    }
}
