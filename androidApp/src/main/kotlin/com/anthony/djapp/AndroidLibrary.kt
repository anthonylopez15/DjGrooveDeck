package com.anthony.djapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.anthony.djapp.engine.AndroidTrackDecoder
import com.anthony.djapp.engine.Track
import com.anthony.djapp.engine.TrackInfo
import com.anthony.djapp.domain.Library
import com.anthony.djapp.domain.LibraryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Biblioteca baseada no Storage Access Framework: o usuário escolhe pastas uma vez,
 * a permissão fica persistida e o app lista/decodifica os áudios sem pedir de novo.
 *
 * O id de cada item é "<uriDaÁrvore>\n<documentId>".
 */
class AndroidLibrary(
    private val context: Context,
) : Library {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val _folders = MutableStateFlow(readFolders())
    override val folders: StateFlow<List<LibraryEntry>> = _folders

    private fun readFolders(): List<LibraryEntry> =
        (prefs.getStringSet(KEY_TREES, emptySet()) ?: emptySet())
            .map { rootEntry(Uri.parse(it)) }
            .sortedBy { it.name.lowercase() }

    private fun rootEntry(tree: Uri): LibraryEntry {
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val name = docId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Armazenamento" }
        return LibraryEntry("$tree\n$docId", name, isFolder = true)
    }

    /** Chamado pela Activity com o resultado do seletor de pasta. */
    fun onFolderPicked(tree: Uri) {
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val set = (prefs.getStringSet(KEY_TREES, emptySet()) ?: emptySet()) + tree.toString()
        prefs.edit().putStringSet(KEY_TREES, set).apply()
        _folders.value = readFolders()
    }

    /**
     * Abre o seletor de pastas. Quem fornece é a Activity à vista (registra no onCreate e devolve no
     * onDestroy): a biblioteca vive no processo e não pode segurar uma Activity já destruída.
     */
    @Volatile var folderPicker: (() -> Unit)? = null

    override fun requestAddFolder() {
        folderPicker?.invoke()
    }

    override fun removeFolder(folder: LibraryEntry) {
        val tree = folder.id.substringBefore('\n')
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(tree), Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val set = (prefs.getStringSet(KEY_TREES, emptySet()) ?: emptySet()) - tree
        prefs.edit().putStringSet(KEY_TREES, set).apply()
        _folders.value = readFolders()
    }

    override suspend fun list(folder: LibraryEntry): List<LibraryEntry> = withContext(Dispatchers.IO) {
        val tree = Uri.parse(folder.id.substringBefore('\n'))
        val docId = folder.id.substringAfter('\n')
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val out = ArrayList<LibraryEntry>()
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val stamp = c.getLong(3) * 31 + c.getLong(4) // muda se o arquivo for alterado
                if (isDir || isAudio(name, mime)) out += LibraryEntry("$tree\n$id", name, isDir, stamp)
            }
        }
        out.sortedWith(compareBy({ !it.isFolder }, { it.name.lowercase() }))
    }

    override suspend fun decode(file: LibraryEntry, onProgress: (Float) -> Unit): Track {
        val tree = Uri.parse(file.id.substringBefore('\n'))
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, file.id.substringAfter('\n'))
        return AndroidTrackDecoder.decode(context, uri, onProgress)
    }

    private val analysis = context.getSharedPreferences("analysis", Context.MODE_PRIVATE)

    private fun cacheKey(file: LibraryEntry) = "${file.id}#${file.stamp}"

    override fun cachedInfo(file: LibraryEntry): TrackInfo? {
        val raw = analysis.getString(cacheKey(file), null) ?: return null
        val (dur, bpm) = raw.split(';').takeIf { it.size == 2 } ?: return null
        return TrackInfo(dur.toDoubleOrNull() ?: return null, bpm.toDoubleOrNull())
    }

    override suspend fun analyze(file: LibraryEntry): TrackInfo? {
        cachedInfo(file)?.let { return it }
        val tree = Uri.parse(file.id.substringBefore('\n'))
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, file.id.substringAfter('\n'))
        val info = AndroidTrackDecoder.analyze(context, uri)
        analysis.edit().putString(cacheKey(file), "${info.durationSec};${info.bpm ?: "-"}").apply()
        return info
    }

    private fun isAudio(name: String, mime: String): Boolean =
        mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in AUDIO_EXT

    private companion object {
        const val KEY_TREES = "trees"
        val AUDIO_EXT = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma")
    }
}
