package com.skaldoria.core.pacing

import com.skaldoria.core.models.PacingStatus

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
    /** The per-slide budget the target duration implies. */
    val secondsPerSlide: Long,
    /** Time left in the allotted talk, floored at zero. */
    val remainingSeconds: Long,
    /** Fraction of the talk budget consumed, clamped to `0f..1f`. */
    val progressRatio: Float
)

/**
 * The speaker-rhythm formula, as a pure function.
 *
 * ```
 * Δt = t_elapsed − (T_target / N_total) · i_current
 * ```
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
     * @param elapsedSeconds time on the talk clock.
     * @param targetTotalSeconds the allotted talk length, or null when pacing is off.
     * @param slideIndex zero-based index of the slide being presented.
     * @param slideCount total slides in the deck.
     */
    fun compute(
        elapsedSeconds: Long,
        targetTotalSeconds: Long?,
        slideIndex: Int,
        slideCount: Int
    ): Pacing {
        if (targetTotalSeconds == null) return OFF

        val secondsPerSlide = if (slideCount > 0) {
            // Floored at 1: a target short enough to divide to zero would give every slide
            // the same ideal elapsed time, so the drift would stop tracking progress.
            (targetTotalSeconds / slideCount).coerceAtLeast(1L)
        } else {
            0L
        }

        val idealElapsed = slideIndex * secondsPerSlide
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
