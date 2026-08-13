package com.skaldoria.state

import com.skaldoria.PresentationStateTestBase
import com.skaldoria.theme.BuiltinThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AUT-01: `T` cycles colour themes.
 *
 * The README shortcut table has documented this since before 1.2.0. There was no `Key.T`
 * anywhere in the source, so the key did nothing — the table described a feature that had
 * never been bound.
 */
class ThemeCyclingTest : PresentationStateTestBase() {

    @Test
    fun `cycling advances to the next available theme`() {
        val state = presentationState()
        val first = state.currentTheme

        state.cycleTheme()

        assertTrue(state.currentTheme.id != first.id, "the theme must actually change")
        assertTrue(state.availableThemes.any { it.id == state.currentTheme.id })
    }

    @Test
    fun `cycling visits every public theme and wraps`() {
        val state = presentationState()
        val expected = state.availableThemes.size
        val start = state.currentTheme.id

        val seen = mutableSetOf(start)
        repeat(expected) {
            state.cycleTheme()
            seen += state.currentTheme.id
        }

        assertEquals(expected, seen.size, "every available theme should be reachable")
        assertEquals(start, state.currentTheme.id, "a full cycle returns to the start")
    }

    @Test
    fun `cycling never selects a locked corporate theme`() {
        // The corporate theme sits behind an access code. Cycling past it would hand out a
        // gated theme for the price of pressing a key.
        val state = presentationState()
        repeat(state.availableThemes.size * 2) {
            state.cycleTheme()
            assertTrue(
                state.currentTheme.id != BuiltinThemes.DeutscheBorseExecutive.id,
                "a locked theme must never be reachable by cycling"
            )
        }
    }

    @Test
    fun `cycling includes the corporate theme once unlocked`() {
        val state = presentationState()
        state.isCorporateThemeUnlocked = true

        val seen = mutableSetOf<String>()
        repeat(state.availableThemes.size + 1) {
            state.cycleTheme()
            seen += state.currentTheme.id
        }

        assertTrue(
            seen.contains(BuiltinThemes.DeutscheBorseExecutive.id),
            "an unlocked theme should join the rotation"
        )
    }

    @Test
    fun `cycling from a theme no longer available starts from the beginning`() {
        // Locking the corporate theme while it is active leaves `currentTheme` outside the
        // available list; cycling must recover rather than sticking or throwing.
        val state = presentationState()
        state.isCorporateThemeUnlocked = true
        state.currentTheme = BuiltinThemes.DeutscheBorseExecutive
        state.isCorporateThemeUnlocked = false

        state.cycleTheme()

        assertTrue(state.availableThemes.any { it.id == state.currentTheme.id })
    }
}
