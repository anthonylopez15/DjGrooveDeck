package com.anthony.djapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.anthony.djapp.engine.AndroidTrackDecoder
import com.anthony.djapp.engine.Track
import com.anthony.djapp.ui.Library
import com.anthony.djapp.ui.LibraryEntry
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
    private val launchFolderPicker: () -> Unit,
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

    override fun requestAddFolder() = launchFolderPicker()

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
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                if (isDir || isAudio(name, mime)) out += LibraryEntry("$tree\n$id", name, isDir)
            }
        }
        out.sortedWith(compareBy({ !it.isFolder }, { it.name.lowercase() }))
    }

    override suspend fun decode(file: LibraryEntry, onProgress: (Float) -> Unit): Track {
        val tree = Uri.parse(file.id.substringBefore('\n'))
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, file.id.substringAfter('\n'))
        return AndroidTrackDecoder.decode(context, uri, onProgress)
    }

    private fun isAudio(name: String, mime: String): Boolean =
        mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in AUDIO_EXT

    private companion object {
        const val KEY_TREES = "trees"
        val AUDIO_EXT = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma")
    }
}
