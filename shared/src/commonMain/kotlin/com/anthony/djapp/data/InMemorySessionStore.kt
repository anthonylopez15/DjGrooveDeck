package com.anthony.djapp.data

import com.anthony.djapp.domain.SessionSnapshot
import com.anthony.djapp.domain.SessionStore

/** [SessionStore] só em memória: para plataformas ainda sem armazenamento (iOS) e para testes. */
class InMemorySessionStore(private var saved: SessionSnapshot? = null) : SessionStore {
    var saves = 0
        private set

    override fun load(): SessionSnapshot? = saved

    override fun save(snapshot: SessionSnapshot) {
        saved = snapshot
        saves++
    }
}
