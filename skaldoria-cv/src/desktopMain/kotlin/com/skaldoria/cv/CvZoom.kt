package com.skaldoria.cv

import kotlin.math.floor
import kotlin.math.min

/**
 * How the preview chooses its scale — CV-FR-047.
 *
 * [None] is an explicit percentage the user set and the window must not move. [Page] and [Width]
 * are standing instructions: they re-resolve whenever the viewport changes, which is why the
 * chosen mode is state and the resulting percentage is not.
 */
enum class CvZoomFit { None, Page, Width }

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

    /**
     * The percentage that makes a sheet fit the viewport.
     *
     * Rounded **down**, so fit-width never leaves the page a fraction of a point wider than the
     * space it was asked to fit into — which would show as a horizontal scrollbar on a view whose
     * entire purpose is not needing one.
     *
     * Export dimensions are untouched by any of this: zoom scales the drawing, never the layout.
     *
     * @return [DefaultPercent] for [CvZoomFit.None], and for a viewport not yet measured — a
     *   zero-sized viewport is the first composition, not a request to shrink to the minimum.
     */
    fun fitPercent(
        fit: CvZoomFit,
        viewportWidth: Float,
        viewportHeight: Float,
        pageWidth: Float,
        pageHeight: Float
    ): Int {
        if (fit == CvZoomFit.None) return DefaultPercent
        if (viewportWidth <= 0f || viewportHeight <= 0f || pageWidth <= 0f || pageHeight <= 0f) {
            return DefaultPercent
        }

        val widthRatio = viewportWidth / pageWidth
        val ratio = when (fit) {
            CvZoomFit.Width -> widthRatio
            CvZoomFit.Page -> min(widthRatio, viewportHeight / pageHeight)
            CvZoomFit.None -> return DefaultPercent
        }

        return floor(ratio * 100f).toInt().coerceIn(MinimumPercent, MaximumPercent)
    }
}
