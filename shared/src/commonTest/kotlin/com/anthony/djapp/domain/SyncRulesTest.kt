package com.anthony.djapp.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regras do SYNC e do transporte, sem motor nem tela. */
class SyncRulesTest {
    private fun deck(bpm: Double? = 126.0, tempo: Float = 0f, sync: Boolean = false, playing: Boolean = false) =
        DeckView(bpm, tempo, sync, playing)

    @Test
    fun effectiveBpmAppliesTempo() {
        assertEquals(129.78, deck(126.0, tempo = 3f).effectiveBpm!!, 0.001)
        assertNull(deck(bpm = null).effectiveBpm)
    }

    @Test
    fun bpmMatchedUsesEffectiveBpmWithTolerance() {
        assertTrue(SyncRules.bpmMatched(deck(126.0), deck(126.04)))
        assertFalse(SyncRules.bpmMatched(deck(126.0), deck(126.1)))
        assertTrue(SyncRules.bpmMatched(deck(120.0, tempo = 5f), deck(126.0)))
        assertFalse(SyncRules.bpmMatched(deck(bpm = null), deck(126.0)))
    }

    @Test
    fun lockedNeedsPhaseLockBothPlayingAndSomeoneSyncing() {
        val a = deck(sync = true, playing = true)
        val b = deck(playing = true)
        assertTrue(SyncRules.locked(true, a, b))
        assertFalse(SyncRules.locked(false, a, b))
        assertFalse(SyncRules.locked(true, a, b.copy(playing = false)))
        assertFalse(SyncRules.locked(true, a.copy(syncOn = false), b))
    }

    @Test
    fun syncStateAndButtonAvailability() {
        assertEquals(SyncState.Locked, SyncRules.syncState(deck(), locked = true))
        assertEquals(SyncState.Armed, SyncRules.syncState(deck(sync = true), locked = false))
        assertEquals(SyncState.Off, SyncRules.syncState(deck(), locked = false))
        assertTrue(SyncRules.canToggleSync(deck(), deck(), locked = false))
        assertFalse(SyncRules.canToggleSync(deck(), deck(bpm = null), locked = false))
        assertTrue(SyncRules.canToggleSync(deck(sync = true), deck(bpm = null), locked = false))
    }

    @Test
    fun followerTempoMatchesLeaderAndIsClamped() {
        assertEquals(3.278f, SyncRules.followerTempo(deck(122.0), deck(126.0))!!, 0.01f)
        // leader acelerado: o seguidor iguala o BPM efetivo, não o original
        assertEquals(8.443f, SyncRules.followerTempo(deck(122.0), deck(126.0, tempo = 5f))!!, 0.01f) // 126 × 1,05 = 132,3 BPM
        // fora do alcance: limitado a ±16%
        assertEquals(16f, SyncRules.followerTempo(deck(100.0), deck(150.0)))
        assertEquals(-16f, SyncRules.followerTempo(deck(150.0), deck(100.0)))
        assertNull(SyncRules.followerTempo(deck(bpm = null), deck()))
    }

    @Test
    fun beatsLockOnlyWhenTempoAlreadyMatches() {
        assertTrue(SyncRules.canLockBeats(deck(122.0, tempo = 3.278f), deck(126.0)))
        assertFalse(SyncRules.canLockBeats(deck(122.0), deck(126.0)))
        assertFalse(SyncRules.canLockBeats(deck(bpm = null), deck(bpm = null)))
    }

    @Test
    fun toggleSyncDecisions() {
        // ligar parado: só arma
        assertEquals(SyncDecision.TurnOn(matchNow = false, lockNow = false), SyncRules.toggleSync(deck(), deck(), false))
        // ligar tocando com o outro parado: iguala o andamento, sem travar
        assertEquals(SyncDecision.TurnOn(true, false), SyncRules.toggleSync(deck(playing = true), deck(), false))
        // os dois tocando: iguala e trava
        assertEquals(SyncDecision.TurnOn(true, true), SyncRules.toggleSync(deck(playing = true), deck(playing = true), false))
        // desligar o próprio; o outro também segue e toca: ele passa a seguir este
        assertEquals(
            SyncDecision.TurnOff(rematchOther = true),
            SyncRules.toggleSync(deck(sync = true), deck(sync = true, playing = true), false),
        )
        assertEquals(SyncDecision.TurnOff(rematchOther = false), SyncRules.toggleSync(deck(sync = true), deck(), false))
        // tudo travado, toque no líder: desfaz o vínculo
        assertEquals(SyncDecision.BreakLink, SyncRules.toggleSync(deck(playing = true), deck(sync = true, playing = true), true))
        // sem BPM: nada
        assertEquals(SyncDecision.Ignore, SyncRules.toggleSync(deck(bpm = null), deck(), false))
    }

    @Test
    fun playDecisions() {
        assertEquals(PlayDecision.Pause, SyncRules.togglePlay(deck(playing = true), deck()))
        assertEquals(PlayDecision.Start, SyncRules.togglePlay(deck(), deck()))
        assertEquals(PlayDecision.Start, SyncRules.togglePlay(deck(sync = true), deck())) // o outro parado
        assertEquals(PlayDecision.AlignedStart(thisFollows = true), SyncRules.togglePlay(deck(sync = true), deck(playing = true)))
        assertEquals(PlayDecision.AlignedStart(thisFollows = false), SyncRules.togglePlay(deck(), deck(sync = true, playing = true)))
        assertEquals(PlayDecision.Start, SyncRules.togglePlay(deck(sync = true, bpm = null), deck(playing = true)))
    }

    @Test
    fun restartRelocksOnlyWithBothPlayingAndSync() {
        assertTrue(SyncRules.relockAfterRestart(deck(playing = true), deck(sync = true, playing = true)))
        assertFalse(SyncRules.relockAfterRestart(deck(playing = true), deck(playing = true)))
        assertFalse(SyncRules.relockAfterRestart(deck(sync = true), deck(playing = true)))
    }

    @Test
    fun trackIsNotReplacedWhilePlaying() {
        assertFalse(SyncRules.canReplaceTrack(deck(playing = true)))
        assertTrue(SyncRules.canReplaceTrack(deck()))
    }

    @Test
    fun deckIdOther() {
        assertEquals(DeckId.B, DeckId.A.other)
        assertEquals(DeckId.A, DeckId.B.other)
    }
}
