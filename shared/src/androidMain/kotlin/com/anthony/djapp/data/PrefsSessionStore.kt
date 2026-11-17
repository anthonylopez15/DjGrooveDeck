package com.anthony.djapp.data

import android.content.Context
import com.anthony.djapp.domain.SessionSnapshot
import com.anthony.djapp.domain.SessionStore

/** [SessionStore] no Android: `SharedPreferences("session")`, chave `state`, no formato do [SessionSnapshotCodec]. */
class PrefsSessionStore(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    override fun load(): SessionSnapshot? = SessionSnapshotCodec.decode(prefs.getString(KEY_STATE, null))

    /** Assíncrono (apply): barato na thread principal. */
    override fun save(snapshot: SessionSnapshot) {
        prefs.edit().putString(KEY_STATE, SessionSnapshotCodec.encode(snapshot)).apply()
    }

    private companion object {
        const val KEY_STATE = "state"
    }
}
