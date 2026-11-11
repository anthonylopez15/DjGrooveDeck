package com.anthony.djapp.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Cores e tema do app.

internal val DeckAColor = Color(0xFF22D3EE)
internal val DeckBColor = Color(0xFFF472B6)
private val PanelColor = Color(0xFF151B23)

internal val DjColors = darkColorScheme(
    background = Color(0xFF0B0F14),
    surface = PanelColor,
    primary = DeckAColor,
    onSurface = Color(0xFFE6EDF3),
    onBackground = Color(0xFFE6EDF3),
)

/** Cor de cada deck (A ciano, B rosa). */
internal fun deckColor(id: DeckId): Color = if (id == DeckId.A) DeckAColor else DeckBColor
