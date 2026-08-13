package com.skaldoria.core.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HUD-1 / HUD-2: the presentation HUD's visibility model.
 *
 * The HUD was an unconditional overlay ~44dp tall sitting 24dp from the bottom, so it covered
 * the last lines of a full-bleed code slide *and* the deck's own slide-number footer, with no
 * way to hide it.
 */
class HudVisibilityTest {

    @Test
    fun `cycling returns to a visible state from every starting point`() {
        // HUD-1: a speaker who hides the HUD must always be able to get it back. If any state
        // could not reach a visible one by cycling, the shortcut would strand them.
        for (start in HudVisibility.entries) {
            var state = start
            var reachedVisible = false
            repeat(HudVisibility.entries.size) {
                state = state.next()
                if (state.isOnScreen(isIdle = false)) reachedVisible = true
            }
            assertTrue(reachedVisible, "cycling from $start never reaches a visible state")
        }
    }

    @Test
    fun `cycling is a closed loop over every state`() {
        val seen = mutableSetOf<HudVisibility>()
        var state = HudVisibility.AUTO
        repeat(HudVisibility.entries.size) {
            seen += state
            state = state.next()
        }
        assertEquals(HudVisibility.entries.toSet(), seen, "cycling must visit every state")
        assertEquals(HudVisibility.AUTO, state, "and return to where it started")
    }

    @Test
    fun `pinned is always on screen and hidden never is`() {
        assertTrue(HudVisibility.PINNED.isOnScreen(isIdle = true))
        assertTrue(HudVisibility.PINNED.isOnScreen(isIdle = false))
        assertFalse(HudVisibility.HIDDEN.isOnScreen(isIdle = true))
        assertFalse(
            HudVisibility.HIDDEN.isOnScreen(isIdle = false),
            "HIDDEN means hidden — pointer movement must not resurrect it, or it is just AUTO"
        )
    }

    @Test
    fun `auto follows pointer activity`() {
        assertTrue(HudVisibility.AUTO.isOnScreen(isIdle = false), "active pointer shows the HUD")
        assertFalse(HudVisibility.AUTO.isOnScreen(isIdle = true), "idle hides it")
    }

    @Test
    fun `auto is the default`() {
        assertEquals(
            HudVisibility.AUTO,
            HudVisibility.DEFAULT,
            "the reported problem is the HUD covering content, so out-of-the-box it must yield"
        )
    }

    @Test
    fun `round trips through its persisted form`() {
        // DED-2: the choice survives a restart. An unknown or corrupt value must degrade to
        // the default rather than throwing on startup.
        for (value in HudVisibility.entries) {
            assertEquals(value, HudVisibility.fromStorage(value.storageValue))
        }
        assertEquals(HudVisibility.DEFAULT, HudVisibility.fromStorage("nonsense"))
        assertEquals(HudVisibility.DEFAULT, HudVisibility.fromStorage(null))
    }
}
