package com.skaldoria.core.pacing

import com.skaldoria.core.models.PacingStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F-06: the pacing formula is a headline feature of this product and had no test at all,
 * because it was computed by six properties on [com.skaldoria.state.PresentationState] that
 * read a live monotonic clock. Extracted as a pure function, every boundary is an assertion.
 *
 * The thresholds asserted here are the ones the shipped UI has always used; this test pins
 * them so a refactor cannot quietly move a band.
 */
class PacingCalculatorTest {

    private fun pacing(
        elapsed: Long,
        targetTotalSeconds: Long? = 600L,
        slideIndex: Int = 0,
        slideCount: Int = 10
    ) = PacingCalculator.compute(elapsed, targetTotalSeconds, slideIndex, slideCount)

    @Test
    fun `no target duration means pacing is off`() {
        val result = pacing(elapsed = 1234L, targetTotalSeconds = null)
        assertEquals(PacingStatus.OFF, result.status)
        assertEquals(0L, result.deltaSeconds)
        assertEquals(0L, result.remainingSeconds)
        assertEquals(0f, result.progressRatio)
    }

    @Test
    fun `seconds per slide divides the target across the deck`() {
        // 600s over 10 slides.
        assertEquals(60L, pacing(elapsed = 0L).secondsPerSlide)
    }

    @Test
    fun `seconds per slide never drops below one`() {
        // A very short target over many slides must not floor to zero and make every
        // slide's ideal elapsed time identical.
        val result = PacingCalculator.compute(0L, targetTotalSeconds = 5L, slideIndex = 3, slideCount = 100)
        assertEquals(1L, result.secondsPerSlide)
        assertEquals(3L, result.idealElapsedSeconds)
    }

    @Test
    fun `an empty deck does not divide by zero`() {
        val result = PacingCalculator.compute(10L, targetTotalSeconds = 600L, slideIndex = 0, slideCount = 0)
        assertEquals(0L, result.secondsPerSlide)
        assertEquals(0L, result.idealElapsedSeconds)
    }

    @Test
    fun `ideal elapsed time is the slide index times the per-slide budget`() {
        assertEquals(180L, pacing(elapsed = 0L, slideIndex = 3).idealElapsedSeconds)
    }

    @Test
    fun `delta is elapsed minus the ideal for the current slide`() {
        // Slide 3 of 10 in a 600s talk: ideal is 180s. Arriving at 200s is 20s behind.
        assertEquals(20L, pacing(elapsed = 200L, slideIndex = 3).deltaSeconds)
    }

    @Test
    fun `on track within the plus or minus twenty second band`() {
        assertEquals(PacingStatus.ON_TRACK, pacing(elapsed = 180L, slideIndex = 3).status)
        assertEquals(PacingStatus.ON_TRACK, pacing(elapsed = 200L, slideIndex = 3).status, "+20s is still on track")
        assertEquals(PacingStatus.ON_TRACK, pacing(elapsed = 160L, slideIndex = 3).status, "-20s is still on track")
    }

    @Test
    fun `more than twenty seconds late is behind`() {
        assertEquals(PacingStatus.BEHIND, pacing(elapsed = 201L, slideIndex = 3).status)
        assertEquals(PacingStatus.BEHIND, pacing(elapsed = 255L, slideIndex = 3).status, "+75s is the last BEHIND value")
    }

    @Test
    fun `more than seventy five seconds late is overtime even inside the target`() {
        // 256s elapsed against a 180s ideal is +76s: overtime, though the 600s talk budget
        // is nowhere near exhausted.
        val result = pacing(elapsed = 256L, slideIndex = 3)
        assertEquals(PacingStatus.OVERTIME, result.status)
        assertTrue(result.remainingSeconds > 0, "still inside the total budget")
    }

    @Test
    fun `more than twenty seconds early is ahead`() {
        assertEquals(PacingStatus.AHEAD, pacing(elapsed = 159L, slideIndex = 3).status)
    }

    @Test
    fun `exceeding the total budget is overtime regardless of the per-slide delta`() {
        // On the last slide the ideal is 540s, so 601s is only +61s — inside the BEHIND
        // band — but the talk itself has overrun and that has to win.
        val result = pacing(elapsed = 601L, slideIndex = 9)
        assertEquals(PacingStatus.OVERTIME, result.status)
        assertEquals(0L, result.remainingSeconds)
    }

    @Test
    fun `remaining time never goes negative`() {
        assertEquals(0L, pacing(elapsed = 5000L).remainingSeconds)
        assertEquals(400L, pacing(elapsed = 200L).remainingSeconds)
    }

    @Test
    fun `progress ratio is clamped to zero and one`() {
        assertEquals(0f, pacing(elapsed = 0L).progressRatio)
        assertEquals(0.5f, pacing(elapsed = 300L).progressRatio)
        assertEquals(1f, pacing(elapsed = 5000L).progressRatio)
    }

    @Test
    fun `a zero or negative target does not divide by zero`() {
        val result = PacingCalculator.compute(10L, targetTotalSeconds = 0L, slideIndex = 0, slideCount = 10)
        assertEquals(0f, result.progressRatio)
    }
}
