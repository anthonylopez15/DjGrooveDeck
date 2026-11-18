package com.anthony.djapp.data

import com.anthony.djapp.domain.GridOverride
import com.anthony.djapp.domain.LibraryEntry
import com.anthony.djapp.domain.TrackGridStore

/** [TrackGridStore] só em memória: plataformas ainda sem armazenamento (iOS) e testes. */
class InMemoryTrackGridStore : TrackGridStore {
    private val grids = HashMap<String, GridOverride>()

    private fun key(source: LibraryEntry) = AnalysisCachePruner.key(source)

    override fun get(source: LibraryEntry): GridOverride? = grids[key(source)]

    override fun put(source: LibraryEntry, grid: GridOverride) {
        grids[key(source)] = grid
    }

    override fun remove(source: LibraryEntry) {
        grids.remove(key(source))
    }
}
