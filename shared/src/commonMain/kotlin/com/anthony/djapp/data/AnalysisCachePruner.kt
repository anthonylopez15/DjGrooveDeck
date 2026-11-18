package com.anthony.djapp.data

import com.anthony.djapp.domain.LibraryEntry

/**
 * Decide quais entradas do cache de análise (BPM e duração) não servem mais. As chaves têm o formato
 * `<id>#<stamp>`, e o id de um arquivo é `<árvore>\n<documentId>` (ver `TrackRepository`). Só decide;
 * quem apaga é a implementação da plataforma.
 */
object AnalysisCachePruner {
    /** Chave de [file] no cache: muda quando o arquivo é alterado (o `stamp` muda). */
    fun key(file: LibraryEntry) = "${file.id}#${file.stamp}"

    /**
     * Chaves a apagar depois de listar uma pasta, dado o que ela contém agora ([present]):
     * - de um arquivo que ainda está na pasta, mas foi alterado (o `stamp` não é mais o mesmo);
     * - de um arquivo que estava **diretamente** nesta pasta e não está mais (apagado ou movido).
     *
     * Arquivos de subpastas não são tocados (a subpasta não foi listada). Se o `documentId` não segue o
     * formato de caminho (`pasta/arquivo`), só o primeiro caso vale: sem como saber de quem é a entrada.
     */
    fun staleAfterListing(keys: Set<String>, folderId: String, present: List<LibraryEntry>): List<String> {
        val folderDoc = folderId.substringAfter('\n')
        val tree = folderId.substringBefore('\n')
        val presentKeys = present.filter { !it.isFolder }.map(::key).toSet()
        val presentIds = present.map { it.id }.toSet()
        return keys.filter { k ->
            val id = k.substringBeforeLast('#')
            when {
                k in presentKeys -> false
                id in presentIds -> true // mesmo arquivo, outro stamp: o BPM antigo pode estar errado
                else -> isDirectChild(id, tree, folderDoc)
            }
        }
    }

    /** Chaves de todos os arquivos de uma árvore (pasta vinculada) que o usuário removeu. */
    fun keysOfTree(keys: Set<String>, tree: String): List<String> = keys.filter { it.startsWith("$tree\n") }

    private fun isDirectChild(id: String, tree: String, folderDoc: String): Boolean {
        if (!id.startsWith("$tree\n")) return false
        val doc = id.substringAfter('\n')
        val prefix = "$folderDoc/"
        return doc.startsWith(prefix) && '/' !in doc.removePrefix(prefix)
    }
}
