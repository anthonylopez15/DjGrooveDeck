package com.anthony.djapp.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test
    fun volumePercent() {
        assertEquals("0%", formatPercent(0f))
        assertEquals("85%", formatPercent(0.85f))
        assertEquals("100%", formatPercent(1f))
    }

    @Test
    fun knobPercent() {
        assertEquals("0%", formatKnobPercent(0.5f))
        assertEquals("-100%", formatKnobPercent(0f))
        assertEquals("+100%", formatKnobPercent(1f))
        assertEquals("-37%", formatKnobPercent(0.315f))
    }
}
