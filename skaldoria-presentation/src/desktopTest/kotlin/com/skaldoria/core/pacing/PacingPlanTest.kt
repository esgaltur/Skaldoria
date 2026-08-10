package com.skaldoria.core.pacing

import com.skaldoria.markdown.models.PacingStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * DEL-11: per-slide time budgets.
 *
 * The pacing model divided the target duration by the slide count, so a title card and a
 * fifteen-line code walkthrough were allotted the same time. On any real deck the front is
 * quick and the middle is slow, so the gauge read "behind" almost immediately and stayed
 * wrong — the headline feature measuring drift against a schedule nobody intends to keep.
 *
 * The first test here is the one that matters most: **a deck declaring no budgets must behave
 * exactly as it did before.** Everything else is the new capability.
 */
class PacingPlanTest {

    // ---- backwards compatibility -------------------------------------------

    @Test
    fun `a deck with no declared budgets keeps the uniform schedule exactly`() {
        val target = 600L
        val slideCount = 10

        val plan = PacingCalculator.plan(target, List(slideCount) { null })

        for (index in 0 until slideCount) {
            assertEquals(
                index * (target / slideCount),
                plan.idealElapsedAt(index),
                "slide $index drifted from the documented (T/N)*i formula"
            )
        }
    }

    @Test
    fun `the uniform overload and an all-null plan agree on every field`() {
        for (index in 0 until 10) {
            val old = PacingCalculator.compute(250L, 600L, index, slideCount = 10)
            val new = PacingCalculator.compute(
                250L, 600L, index, PacingCalculator.plan(600L, List(10) { null })
            )
            assertEquals(old, new, "the two entry points disagree at slide $index")
        }
    }

    // ---- the feature --------------------------------------------------------

    @Test
    fun `a declared budget is honoured and the rest split what is left`() {
        // 600s total, one slide claims 300s, the other three share the remaining 300s.
        val plan = PacingCalculator.plan(600L, listOf(null, 300L, null, null))

        assertEquals(listOf(100L, 300L, 100L, 100L), plan.slideSeconds)
        assertFalse(plan.isOverCommitted)
    }

    @Test
    fun `ideal elapsed accumulates the real budgets, not an average`() {
        val plan = PacingCalculator.plan(600L, listOf(null, 300L, null, null))

        assertEquals(0L, plan.idealElapsedAt(0))
        assertEquals(100L, plan.idealElapsedAt(1))
        assertEquals(400L, plan.idealElapsedAt(2), "the 300s slide must count for 300s")
        assertEquals(500L, plan.idealElapsedAt(3))
    }

    @Test
    fun `the defect this fixes - a slow slide no longer reads as behind`() {
        // A 10-minute talk: four quick slides, then a five-minute demo.
        val budgets = listOf(30L, 30L, 30L, 30L, 300L, null, null)
        val plan = PacingCalculator.plan(600L, budgets)

        // Arriving at the slide *after* the demo, 7 minutes in, is exactly on schedule.
        val onSchedule = PacingCalculator.compute(420L, 600L, slideIndex = 5, plan = plan)
        assertEquals(PacingStatus.ON_TRACK, onSchedule.status, "delta was ${onSchedule.deltaSeconds}s")

        // The old uniform model expected 5/7 of 600s = 420s… by coincidence the same here, so
        // check the slide *before* the demo, where the two models genuinely disagree.
        val uniform = PacingCalculator.compute(120L, 600L, slideIndex = 4, slideCount = 7)
        val budgeted = PacingCalculator.compute(120L, 600L, slideIndex = 4, plan = plan)
        assertEquals(PacingStatus.ON_TRACK, budgeted.status, "budgeted: ${budgeted.deltaSeconds}s")
        assertEquals(PacingStatus.AHEAD, uniform.status, "uniform: ${uniform.deltaSeconds}s")
    }

    @Test
    fun `secondsPerSlide reports the current slide's budget, not the deck average`() {
        val plan = PacingCalculator.plan(600L, listOf(null, 300L, null, null))

        assertEquals(300L, PacingCalculator.compute(0L, 600L, 1, plan).secondsPerSlide)
        assertEquals(100L, PacingCalculator.compute(0L, 600L, 2, plan).secondsPerSlide)
    }

    // ---- edges ---------------------------------------------------------------

    @Test
    fun `budgets that exceed the target are reported, not silently rescaled`() {
        // Rescaling would make `pace: 600s` mean something other than 600 seconds.
        val plan = PacingCalculator.plan(600L, listOf(600L, 600L))

        assertTrue(plan.isOverCommitted)
        assertEquals(listOf(600L, 600L), plan.slideSeconds, "declared budgets must survive intact")
    }

    @Test
    fun `unbudgeted slides still get a nonzero share when the budgets consume everything`() {
        val plan = PacingCalculator.plan(600L, listOf(600L, null, null))

        // A share of zero would give both remaining slides the same ideal elapsed time, so
        // drift would stop tracking progress across them — the reason the old divisor had a floor.
        assertEquals(listOf(600L, 1L, 1L), plan.slideSeconds)
    }

    @Test
    fun `an empty deck plans nothing rather than dividing by zero`() {
        val plan = PacingCalculator.plan(600L, emptyList())

        assertEquals(emptyList(), plan.slideSeconds)
        assertEquals(0L, plan.idealElapsedAt(3))
        assertEquals(0L, plan.budgetAt(0))
    }

    @Test
    fun `pacing stays off when no target is set, whatever the deck declares`() {
        val plan = PacingCalculator.plan(0L, listOf(90L, 90L))

        assertEquals(PacingStatus.OFF, PacingCalculator.compute(100L, null, 1, plan).status)
    }
}
