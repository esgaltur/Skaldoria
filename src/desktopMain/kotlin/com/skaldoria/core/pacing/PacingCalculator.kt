package com.skaldoria.core.pacing

import com.skaldoria.markdown.models.PacingStatus

/**
 * A fully-resolved pacing readout for one moment in a talk.
 *
 * Every field the presenter ribbon needs, computed together from one consistent set of
 * inputs. Previously these were six independent computed properties on `PresentationState`,
 * each re-reading `elapsedSeconds` — so a tick landing between two reads could render a
 * delta and a status that disagreed.
 */
data class Pacing(
    /** Traffic-light band for the ribbon. */
    val status: PacingStatus,
    /** Signed drift against the schedule: positive is behind, negative is ahead. */
    val deltaSeconds: Long,
    /** Where the clock *should* read on arriving at the current slide. */
    val idealElapsedSeconds: Long,
    /**
     * The budget for the slide being presented.
     *
     * DEL-11: this used to be one number for the whole deck, because every slide got the
     * same share. With per-slide budgets it is *this* slide's, which is the number a
     * speaker can act on. Identical to the old value for a deck that declares no budgets.
     */
    val secondsPerSlide: Long,
    /** Time left in the allotted talk, floored at zero. */
    val remainingSeconds: Long,
    /** Fraction of the talk budget consumed, clamped to `0f..1f`. */
    val progressRatio: Float
)

/**
 * A schedule for a talk: how long each slide is expected to take, in order.
 *
 * DEL-11. Slides that declare `<!-- pace: 90s -->` get exactly that; the rest split whatever
 * the target leaves over. A deck that declares nothing therefore produces the uniform schedule
 * the model has always used, which is asserted rather than assumed.
 */
data class PacingPlan(
    /** Budget per slide, in source order. */
    val slideSeconds: List<Long>,
    /**
     * True when the declared budgets alone already exceed the target duration.
     *
     * Not an error and not silently reconciled: scaling the author's `90s` down to fit would
     * make the directive mean something other than what it says. The schedule keeps the
     * declared budgets, unbudgeted slides get nothing left to share, and the plan simply
     * overruns — which is the truth about that deck, and something the UI can surface.
     */
    val isOverCommitted: Boolean
) {
    /** Where the clock should read on *arriving* at [slideIndex] — the sum of everything before it. */
    fun idealElapsedAt(slideIndex: Int): Long =
        slideSeconds.take(slideIndex.coerceAtLeast(0)).sum()

    /** The budget for [slideIndex], or 0 outside the deck. */
    fun budgetAt(slideIndex: Int): Long = slideSeconds.getOrElse(slideIndex) { 0L }
}

/**
 * The speaker-rhythm formula, as a pure function.
 *
 * ```
 * Δt = t_elapsed − idealElapsed(i)
 * ```
 *
 * where `idealElapsed(i)` is the sum of the budgets of the slides before `i`. With a uniform
 * schedule that reduces to the original `(T_target / N_total) · i_current`, which is what the
 * README documents and what a deck declaring no budgets still gets.
 *
 * PRF-4: extracted from `PresentationState`, where it was entangled with a live monotonic
 * clock and therefore verifiable only by reading it — despite being a headline feature of
 * the product. As a pure function every band boundary is a unit assertion
 * ([com.skaldoria.core.pacing.PacingCalculatorTest]).
 *
 * The thresholds are deliberately asymmetric. Running *late* is the failure the speaker must
 * act on, so it is split into a recoverable band ([PacingStatus.BEHIND]) and an urgent one
 * ([PacingStatus.OVERTIME]); running early only ever needs one band. The numbers are the ones
 * the shipped UI has always used and are named here rather than left inline.
 */
object PacingCalculator {

    /** Drift inside ±this many seconds reads as on schedule. */
    const val ON_TRACK_TOLERANCE_SECONDS = 20L

    /** Beyond this much drift the recoverable "behind" band escalates to overtime. */
    const val OVERTIME_DRIFT_SECONDS = 75L

    /**
     * DEL-11: turns declared budgets into a schedule.
     *
     * @param slideBudgets one entry per slide: declared seconds, or null to take a share.
     */
    fun plan(targetTotalSeconds: Long, slideBudgets: List<Long?>): PacingPlan {
        if (slideBudgets.isEmpty()) return PacingPlan(emptyList(), isOverCommitted = false)

        val declaredTotal = slideBudgets.filterNotNull().sum()
        val unbudgeted = slideBudgets.count { it == null }
        val leftOver = (targetTotalSeconds - declaredTotal).coerceAtLeast(0L)

        // Floored at 1 for the same reason the uniform divisor was: a share of zero gives every
        // remaining slide the same ideal elapsed time, so drift stops tracking progress.
        val share = if (unbudgeted > 0) (leftOver / unbudgeted).coerceAtLeast(1L) else 0L

        return PacingPlan(
            slideSeconds = slideBudgets.map { it ?: share },
            isOverCommitted = declaredTotal > targetTotalSeconds
        )
    }

    /**
     * The uniform schedule, kept so every existing call site and the README formula still hold.
     *
     * Delegates to [plan] with no declared budgets, so there is one implementation of the
     * schedule rather than two that can drift apart.
     */
    fun compute(
        elapsedSeconds: Long,
        targetTotalSeconds: Long?,
        slideIndex: Int,
        slideCount: Int
    ): Pacing = compute(
        elapsedSeconds = elapsedSeconds,
        targetTotalSeconds = targetTotalSeconds,
        slideIndex = slideIndex,
        plan = if (targetTotalSeconds == null) {
            PacingPlan(emptyList(), isOverCommitted = false)
        } else {
            plan(targetTotalSeconds, List(slideCount.coerceAtLeast(0)) { null })
        }
    )

    /**
     * @param elapsedSeconds time on the talk clock.
     * @param targetTotalSeconds the allotted talk length, or null when pacing is off.
     * @param slideIndex zero-based index of the slide being presented.
     * @param plan the schedule, from [plan].
     */
    fun compute(
        elapsedSeconds: Long,
        targetTotalSeconds: Long?,
        slideIndex: Int,
        plan: PacingPlan
    ): Pacing {
        if (targetTotalSeconds == null) return OFF

        val secondsPerSlide = plan.budgetAt(slideIndex)
        val idealElapsed = plan.idealElapsedAt(slideIndex)
        val delta = elapsedSeconds - idealElapsed

        return Pacing(
            status = statusFor(elapsedSeconds, targetTotalSeconds, delta),
            deltaSeconds = delta,
            idealElapsedSeconds = idealElapsed,
            secondsPerSlide = secondsPerSlide,
            remainingSeconds = (targetTotalSeconds - elapsedSeconds).coerceAtLeast(0L),
            progressRatio = if (targetTotalSeconds > 0) {
                (elapsedSeconds.toFloat() / targetTotalSeconds.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        )
    }

    private fun statusFor(elapsed: Long, targetTotal: Long, delta: Long): PacingStatus = when {
        // Overrunning the whole talk outranks the per-slide drift: on a late slide the ideal
        // is large, so the drift can still look merely "behind" while the talk has overrun.
        elapsed > targetTotal -> PacingStatus.OVERTIME
        delta > OVERTIME_DRIFT_SECONDS -> PacingStatus.OVERTIME
        delta > ON_TRACK_TOLERANCE_SECONDS -> PacingStatus.BEHIND
        delta < -ON_TRACK_TOLERANCE_SECONDS -> PacingStatus.AHEAD
        else -> PacingStatus.ON_TRACK
    }

    /** The readout when no target duration is set — a free-running talk. */
    private val OFF = Pacing(
        status = PacingStatus.OFF,
        deltaSeconds = 0L,
        idealElapsedSeconds = 0L,
        secondsPerSlide = 0L,
        remainingSeconds = 0L,
        progressRatio = 0f
    )
}
