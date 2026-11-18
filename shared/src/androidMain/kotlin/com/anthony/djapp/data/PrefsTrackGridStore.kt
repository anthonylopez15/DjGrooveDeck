package com.anthony.djapp.data

import android.content.Context
import com.anthony.djapp.domain.GridOverride
import com.anthony.djapp.domain.LibraryEntry
import com.anthony.djapp.domain.TrackGridStore

/** [TrackGridStore] no Android: `SharedPreferences("grids")`, chave `id#stamp`, valor `bpm;primeiraBatida`. */
class PrefsTrackGridStore(context: Context) : TrackGridStore {
    private val prefs = context.getSharedPreferences("grids", Context.MODE_PRIVATE)

    private fun key(source: LibraryEntry) = AnalysisCachePruner.key(source)

    override fun get(source: LibraryEntry): GridOverride? {
        val parts = prefs.getString(key(source), null)?.split(';') ?: return null
        if (parts.size != 2) return null
        val bpm = parts[0].toDoubleOrNull() ?: return null
        val first = parts[1].toDoubleOrNull() ?: return null
        return GridOverride(bpm, first)
    }

    override fun put(source: LibraryEntry, grid: GridOverride) {
        prefs.edit().putString(key(source), "${grid.bpm};${grid.firstBeatSec}").apply()
    }

    override fun remove(source: LibraryEntry) {
        prefs.edit().remove(key(source)).apply()
    }
}
