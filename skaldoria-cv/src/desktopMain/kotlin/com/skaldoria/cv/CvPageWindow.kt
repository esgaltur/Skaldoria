package com.skaldoria.cv

import kotlin.math.floor

/**
 * Which sheets the preview actually has to draw — CV-NFR-022.
 *
 * The preview used to compose and paint every page in one column. That is fine for the two-page
 * reference CV and quadratically less fine as decks of pages grow: the hundred-page stress fixture
 * of CV-NFR-024 paid for a hundred `Canvas` passes and a hundred sets of `TextLayoutResult`
 * lookups on every frame, all but two of them off screen.
 *
 * Pages outside the window keep their **space** — a spacer of identical size holds the scroll
 * geometry still, so nothing reflows and the scrollbar does not jump as the window moves. Only the
 * drawing is skipped.
 *
 * Everything here is in one consistent unit (pixels, at whatever zoom the caller already applied)
 * and is deliberately free of Compose types, so the arithmetic can be tested without a display.
 */
object CvPageWindow {

    /** How many off-screen sheets on each side stay drawn, so scrolling reveals paint, not blanks. */
    const val AdjacentPages: Int = 1

    /**
     * The pages to draw, as zero-based indices.
     *
     * @param scrollOffset how far the content has scrolled up out of the viewport.
     * @param viewportExtent the visible height.
     * @param firstPageTop the padding above page one.
     * @param pageExtent one sheet's height, and [gap] the space between two sheets.
     */
    fun visible(
        scrollOffset: Float,
        viewportExtent: Float,
        firstPageTop: Float,
        pageExtent: Float,
        gap: Float,
        pageCount: Int,
        adjacent: Int = AdjacentPages
    ): IntRange {
        if (pageCount <= 0) return IntRange.EMPTY

        val stride = pageExtent + gap
        // An unmeasured viewport must not resolve to "draw nothing"; the first composition happens
        // before layout has run, and a blank first frame would be a visible flash.
        if (stride <= 0f || viewportExtent <= 0f) return 0 until pageCount

        val top = scrollOffset - firstPageTop
        val first = floor(top / stride).toInt() - adjacent
        val last = floor((top + viewportExtent) / stride).toInt() + adjacent

        return first.coerceIn(0, pageCount - 1)..last.coerceIn(0, pageCount - 1)
    }

    /**
     * The one-based page the viewport is looking at, for the page indicator.
     *
     * Measured from the viewport's centre rather than its top edge: with the top edge, a sheet
     * one pixel from scrolling away still claims to be the current page, and the counter changes
     * a whole page before the view appears to.
     */
    fun currentPage(
        scrollOffset: Float,
        viewportExtent: Float,
        firstPageTop: Float,
        pageExtent: Float,
        gap: Float,
        pageCount: Int
    ): Int {
        if (pageCount <= 0) return 1
        val stride = pageExtent + gap
        if (stride <= 0f) return 1

        val centre = scrollOffset + viewportExtent / 2f - firstPageTop
        return (floor(centre / stride).toInt() + 1).coerceIn(1, pageCount)
    }

    /** Where to scroll so that [pageNumber] sits at the top of the viewport. */
    fun offsetOfPage(
        pageNumber: Int,
        firstPageTop: Float,
        pageExtent: Float,
        gap: Float
    ): Float = firstPageTop + (pageNumber - 1).coerceAtLeast(0) * (pageExtent + gap)
}
