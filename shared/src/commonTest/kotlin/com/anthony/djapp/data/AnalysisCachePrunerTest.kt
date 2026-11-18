package com.anthony.djapp.data

import com.anthony.djapp.domain.LibraryEntry
import kotlin.test.Test
import kotlin.test.assertEquals

/** Limpeza do cache de BPM/duração: só o que sobrou de arquivos que mudaram ou sumiram. */
class AnalysisCachePrunerTest {
    private val tree = "content://tree/primary%3AMusic"
    private val folder = LibraryEntry("$tree\nprimary:Music/Bachata", "Bachata", isFolder = true)

    private fun file(name: String, stamp: Long, dir: String = "primary:Music/Bachata", treeUri: String = tree) =
        LibraryEntry("$treeUri\n$dir/$name", name, isFolder = false, stamp = stamp)

    private fun keys(vararg files: LibraryEntry) = files.map(AnalysisCachePruner::key).toSet()

    @Test
    fun keepsUnchangedFilesDropsChangedAndMissingOnes() {
        val a = file("a.mp3", 1)
        val b = file("b.mp3", 1)
        val gone = file("gone.mp3", 1)
        val cache = keys(a, b, gone) + AnalysisCachePruner.key(b.copy(stamp = 0)) // b foi editado: chave antiga

        val stale = AnalysisCachePruner.staleAfterListing(cache, folder.id, present = listOf(a, b))

        assertEquals(setOf(AnalysisCachePruner.key(gone), AnalysisCachePruner.key(b.copy(stamp = 0))), stale.toSet())
    }

    @Test
    fun filesInSubfoldersAreNotTouchedWhenListingTheParent() {
        val deep = file("deep.mp3", 1, dir = "primary:Music/Bachata/Sub")
        val sub = LibraryEntry("$tree\nprimary:Music/Bachata/Sub", "Sub", isFolder = true)
        val stale = AnalysisCachePruner.staleAfterListing(keys(deep), folder.id, present = listOf(sub))
        assertEquals(emptyList(), stale, "a subpasta não foi listada: não dá para saber se o arquivo sumiu")
    }

    @Test
    fun otherFoldersAndOtherTreesAreNotTouched() {
        val other = file("x.mp3", 1, dir = "primary:Music/Merengue")
        val otherTree = file("y.mp3", 1, treeUri = "content://tree/outra")
        val stale = AnalysisCachePruner.staleAfterListing(keys(other, otherTree), folder.id, present = emptyList())
        assertEquals(emptyList(), stale)
    }

    @Test
    fun aPrefixOfTheFolderNameIsNotTheFolder() {
        // "Bachata2" começa com "Bachata", mas não é filha de "Bachata/"
        val sibling = file("z.mp3", 1, dir = "primary:Music/Bachata2")
        val stale = AnalysisCachePruner.staleAfterListing(keys(sibling), folder.id, present = emptyList())
        assertEquals(emptyList(), stale)
    }

    @Test
    fun documentIdsWithoutPathsOnlyDropChangedStamps() {
        // provedor que não usa "pasta/arquivo": sem como saber de quem é a entrada, só descarta o stamp velho
        val f = LibraryEntry("$tree\n1234", "a.mp3", isFolder = false, stamp = 5)
        val old = AnalysisCachePruner.key(f.copy(stamp = 4))
        val missing = "$tree\n9999#1"
        val stale = AnalysisCachePruner.staleAfterListing(setOf(old, missing), "$tree\n77", present = listOf(f))
        assertEquals(listOf(old), stale)
    }

    @Test
    fun removingATreeDropsOnlyItsEntries() {
        val mine = keys(file("a.mp3", 1), file("b.mp3", 2, dir = "primary:Music/Merengue"))
        val theirs = keys(file("c.mp3", 1, treeUri = "content://tree/outra"))
        val dropped = AnalysisCachePruner.keysOfTree(mine + theirs, tree)
        assertEquals(mine, dropped.toSet())
    }

    @Test
    fun treeUriThatIsAPrefixOfAnotherIsNotConfused() {
        val longer = file("a.mp3", 1, treeUri = "${tree}X")
        assertEquals(emptyList(), AnalysisCachePruner.keysOfTree(keys(longer), tree))
    }
}
