package com.skaldoria.cv

/** Stable preview-only zoom policy. Integer percentages avoid cumulative floating-point drift. */
object CvZoomPolicy {
    const val DefaultPercent: Int = 100
    const val MinimumPercent: Int = 50
    const val MaximumPercent: Int = 200
    const val StepPercent: Int = 10

    fun zoomIn(currentPercent: Int): Int =
        (currentPercent + StepPercent).coerceAtMost(MaximumPercent)

    fun zoomOut(currentPercent: Int): Int =
        (currentPercent - StepPercent).coerceAtLeast(MinimumPercent)

    fun scale(percent: Int): Float = percent / 100f
}
