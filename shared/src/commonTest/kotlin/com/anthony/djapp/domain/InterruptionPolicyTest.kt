package com.anthony.djapp.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/** Reação às interrupções: a mesma em Android e iOS. */
class InterruptionPolicyTest {
    private val pauseSaveNotify = InterruptionReaction(pauseAll = true, persist = true, notify = true)
    private val nothing = InterruptionReaction(pauseAll = false, persist = false, notify = false)

    @Test
    fun interruptionsWhilePlayingPauseSaveAndNotify() {
        for (kind in listOf(Interruption.OutputDisconnected, Interruption.OtherAppPlaying, Interruption.Transient)) {
            assertEquals(pauseSaveNotify, InterruptionPolicy.reactTo(kind, anyPlaying = true), "$kind")
        }
    }

    @Test
    fun interruptionsWhileStoppedDoNothing() {
        for (kind in listOf(Interruption.OutputDisconnected, Interruption.OtherAppPlaying, Interruption.Transient)) {
            assertEquals(nothing, InterruptionPolicy.reactTo(kind, anyPlaying = false), "$kind")
        }
    }

    @Test
    fun deniedPlaybackOnlyNotifies() {
        val r = InterruptionPolicy.reactTo(Interruption.PlaybackDenied, anyPlaying = false)
        assertEquals(InterruptionReaction(pauseAll = false, persist = false, notify = true), r)
    }

    @Test
    fun explicitPauseAlwaysPausesAndSavesSilently() {
        for (playing in listOf(true, false)) {
            assertEquals(
                InterruptionReaction(pauseAll = true, persist = true, notify = false),
                InterruptionPolicy.reactTo(Interruption.PauseRequested, playing),
            )
        }
    }
}
