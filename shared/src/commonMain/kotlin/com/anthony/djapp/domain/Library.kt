package com.anthony.djapp.domain

import com.anthony.djapp.engine.Track
import com.anthony.djapp.engine.TrackInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Item do navegador: pasta ou arquivo de áudio. [id] é opaco, definido pela plataforma. */
data class LibraryEntry(val id: String, val name: String, val isFolder: Boolean, val stamp: Long = 0L)

/**
 * Biblioteca de músicas do usuário: pastas "vinculadas" que o app lista e carrega.
 * Android: pastas via Storage Access Framework. iOS: a implementar (UIDocumentPicker).
 */
interface Library {
    /** Pastas raiz vinculadas pelo usuário. */
    val folders: StateFlow<List<LibraryEntry>>

    /** Abre o seletor de pasta da plataforma. */
    fun requestAddFolder()

    fun removeFolder(folder: LibraryEntry)

    /** Conteúdo de [folder] (subpastas e áudios), já ordenado. */
    suspend fun list(folder: LibraryEntry): List<LibraryEntry>

    /** Duração e BPM já analisados e guardados (resposta imediata), ou null se ainda não analisada. */
    fun cachedInfo(file: LibraryEntry): TrackInfo?

    /** Analisa [file] (decodifica só um trecho) e guarda o resultado; null se não conseguir. */
    suspend fun analyze(file: LibraryEntry): TrackInfo?

    /** Decodifica o áudio de [file]; [onProgress] recebe 0..1 (pode vir de outra thread). */
    suspend fun decode(file: LibraryEntry, onProgress: (Float) -> Unit = {}): Track
}

/** Biblioteca vazia, para plataformas sem implementação e para previews. */
object EmptyLibrary : Library {
    override val folders: StateFlow<List<LibraryEntry>> = MutableStateFlow(emptyList())
    override fun requestAddFolder() = Unit
    override fun removeFolder(folder: LibraryEntry) = Unit
    override suspend fun list(folder: LibraryEntry): List<LibraryEntry> = emptyList()
    override fun cachedInfo(file: LibraryEntry): TrackInfo? = null
    override suspend fun analyze(file: LibraryEntry): TrackInfo? = null
    override suspend fun decode(file: LibraryEntry, onProgress: (Float) -> Unit): Track = error("Biblioteca indisponível nesta plataforma")
}
