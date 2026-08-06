package com.skaldoria.core.deck

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F-13: where the deck is, separated from what the deck is.
 *
 * Navigation was five members on `PresentationState` interleaved with parsing, file I/O and
 * the companion server. The bounds arithmetic is the sort that is obviously right until a
 * deck is empty or shrinks under the cursor — both of which happen here, because editing the
 * markdown reparses the deck while a slide is selected.
 */
class SlideNavigatorTest {

    private var count = 5
    private val nav = SlideNavigator { count }

    @Test
    fun `starts on the first slide`() {
        assertEquals(0, nav.currentIndex)
        assertFalse(nav.hasPrevious)
        assertTrue(nav.hasNext)
    }

    @Test
    fun `next and previous walk the deck`() {
        nav.next(); nav.next()
        assertEquals(2, nav.currentIndex)
        nav.previous()
        assertEquals(1, nav.currentIndex)
    }

    @Test
    fun `navigation stops at the ends rather than wrapping`() {
        repeat(20) { nav.next() }
        assertEquals(4, nav.currentIndex, "must not run past the last slide")
        assertFalse(nav.hasNext)

        repeat(20) { nav.previous() }
        assertEquals(0, nav.currentIndex, "must not run before the first slide")
        assertFalse(nav.hasPrevious)
    }

    @Test
    fun `goTo accepts an in-range index and ignores anything else`() {
        assertTrue(nav.goTo(3))
        assertEquals(3, nav.currentIndex)

        assertFalse(nav.goTo(99), "out of range should be refused")
        assertEquals(3, nav.currentIndex, "a refused jump must not move the cursor")

        assertFalse(nav.goTo(-1))
        assertEquals(3, nav.currentIndex)
    }

    /** Editing the markdown reparses the deck, which can delete the slide being shown. */
    @Test
    fun `the cursor is clamped when the deck shrinks beneath it`() {
        nav.goTo(4)
        count = 2
        nav.clampToDeck()
        assertEquals(1, nav.currentIndex, "should land on the new last slide")
    }

    @Test
    fun `clamping does nothing while the cursor is still valid`() {
        nav.goTo(1)
        count = 5
        nav.clampToDeck()
        assertEquals(1, nav.currentIndex)
    }

    @Test
    fun `an empty deck reports no navigation and clamps to zero`() {
        count = 0
        nav.clampToDeck()
        assertEquals(0, nav.currentIndex)
        assertFalse(nav.hasNext)
        assertFalse(nav.hasPrevious)
        assertFalse(nav.goTo(0), "there is no slide 0 in an empty deck")
    }

    @Test
    fun `a single-slide deck has nowhere to go`() {
        count = 1
        assertFalse(nav.hasNext)
        assertFalse(nav.hasPrevious)
        nav.next()
        assertEquals(0, nav.currentIndex)
    }

    /**
     * `moveTo` clamps where `goTo` refuses. Structural edits compute the landing index from
     * indices that were valid *before* the edit reparsed the deck, so "as close as possible"
     * is correct there — declining to move would leave the cursor on the wrong slide.
     */
    @Test
    fun `moveTo clamps instead of refusing`() {
        nav.moveTo(99)
        assertEquals(4, nav.currentIndex, "should land on the last slide, not refuse")

        nav.moveTo(-5)
        assertEquals(0, nav.currentIndex, "should land on the first slide, not refuse")

        nav.moveTo(2)
        assertEquals(2, nav.currentIndex)
    }

    @Test
    fun `moveTo on an empty deck lands at zero rather than a negative index`() {
        count = 0
        nav.moveTo(3)
        assertEquals(0, nav.currentIndex)
    }

    @Test
    fun `deleting the last slide lands on its predecessor`() {
        // The shape every delete path relies on: index-1, after the deck has already shrunk.
        nav.goTo(4)
        count = 4
        nav.moveTo(4 - 1)
        assertEquals(3, nav.currentIndex)
    }

    @Test
    fun `reset returns to the first slide`() {
        nav.goTo(4)
        nav.reset()
        assertEquals(0, nav.currentIndex)
    }

    @Test
    fun `next reports whether it actually moved`() {
        count = 2
        assertTrue(nav.next(), "moving from 0 to 1 is a move")
        assertFalse(nav.next(), "already on the last slide")
    }
}
