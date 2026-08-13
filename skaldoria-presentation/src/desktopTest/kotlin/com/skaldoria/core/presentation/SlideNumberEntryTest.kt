package com.skaldoria.core.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DEL-08: type a slide number during a presentation and jump to it.
 *
 * Grid overview (`G`) exists but is a visual scan; on a fifty-slide deck a speaker answering
 * "can you go back to the architecture slide?" needs to reach slide 27 directly.
 */
class SlideNumberEntryTest {

    @Test
    fun `accumulates digits and resolves to a zero-based index`() {
        val entry = SlideNumberEntry().withDigit('2').withDigit('7')
        assertEquals("27", entry.buffer)
        assertEquals(
            26,
            entry.targetIndex(totalSlides = 50),
            "the audience counts from 1, the deck indexes from 0"
        )
    }

    @Test
    fun `a single digit works`() {
        assertEquals(0, SlideNumberEntry().withDigit('1').targetIndex(totalSlides = 10))
    }

    @Test
    fun `an empty buffer resolves to nothing`() {
        assertNull(SlideNumberEntry().targetIndex(totalSlides = 10))
    }

    @Test
    fun `a number past the end of the deck resolves to nothing`() {
        // Jumping to a blank screen mid-talk is worse than ignoring the keystroke.
        assertNull(SlideNumberEntry().withDigit('9').withDigit('9').targetIndex(totalSlides = 10))
    }

    @Test
    fun `zero resolves to nothing because slides are numbered from one`() {
        assertNull(SlideNumberEntry().withDigit('0').targetIndex(totalSlides = 10))
    }

    @Test
    fun `non-digit input is ignored rather than corrupting the buffer`() {
        val entry = SlideNumberEntry().withDigit('1').withDigit('x').withDigit('2')
        assertEquals("12", entry.buffer)
    }

    @Test
    fun `clearing empties the buffer`() {
        assertEquals("", SlideNumberEntry().withDigit('4').cleared().buffer)
        assertTrue(SlideNumberEntry().withDigit('4').cleared().isEmpty)
    }

    @Test
    fun `the buffer is bounded`() {
        // An unbounded buffer would accumulate a keystroke stream into an ever-growing string
        // that can never resolve. No deck has a million slides.
        var entry = SlideNumberEntry()
        repeat(20) { entry = entry.withDigit('9') }
        assertTrue(entry.buffer.length <= SlideNumberEntry.MAX_DIGITS, "buffer must be capped")
    }

    @Test
    fun `an overlong run of digits still resolves against a real deck`() {
        // Capping must not leave the buffer in a state that resolves to a wrong slide.
        var entry = SlideNumberEntry()
        repeat(20) { entry = entry.withDigit('9') }
        assertNull(entry.targetIndex(totalSlides = 50))
    }
}
