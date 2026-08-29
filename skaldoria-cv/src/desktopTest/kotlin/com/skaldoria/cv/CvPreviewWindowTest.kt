package com.skaldoria.cv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The arithmetic behind page virtualisation and the fit controls — CV-NFR-022 and CV-FR-047.
 *
 * Pure functions on purpose: what pages are on screen and what scale fits a viewport are decisions
 * that can be wrong in ways a rendered screenshot would never show, and neither needs a display to
 * check.
 */
class CvPreviewWindowTest {

    // A sheet a thousand pixels tall, the usual gap and padding, in a viewport showing most of one.
    private val pageExtent = 1000f
    private val gap = 24f
    private val firstPageTop = 28f
    private val viewport = 800f
    private val pageCount = 10

    private fun visibleAt(scroll: Float) = CvPageWindow.visible(
        scrollOffset = scroll,
        viewportExtent = viewport,
        firstPageTop = firstPageTop,
        pageExtent = pageExtent,
        gap = gap,
        pageCount = pageCount
    )

    // ---------------------------------------------------------------------
    // Which pages get drawn
    // ---------------------------------------------------------------------

    @Test
    fun `the top of the document draws the first page and its neighbour`() {
        assertEquals(0..1, visibleAt(0f))
    }

    @Test
    fun `scrolling moves the window with one drawn page of slack on each side`() {
        // Three strides down: the viewport is over page four, so pages three to five are drawn.
        assertEquals(2..4, visibleAt(3f * (pageExtent + gap) + firstPageTop))
    }

    @Test
    fun `the window never runs past either end of the document`() {
        val atStart = visibleAt(0f)
        val atEnd = visibleAt(pageCount * (pageExtent + gap))

        assertTrue(atStart.first >= 0, "drew a page before the first: $atStart")
        assertTrue(atEnd.last <= pageCount - 1, "drew a page past the last: $atEnd")
    }

    @Test
    fun `a document of one page draws that page`() {
        assertEquals(
            0..0,
            CvPageWindow.visible(0f, viewport, firstPageTop, pageExtent, gap, pageCount = 1)
        )
    }

    @Test
    fun `an empty document asks for nothing`() {
        assertTrue(
            CvPageWindow.visible(0f, viewport, firstPageTop, pageExtent, gap, pageCount = 0).isEmpty()
        )
    }

    @Test
    fun `an unmeasured viewport draws everything rather than flashing blank`() {
        // First composition happens before layout has run. Resolving that to "draw nothing" would
        // show the user an empty grey pane for a frame.
        assertEquals(
            0 until pageCount,
            CvPageWindow.visible(0f, viewportExtent = 0f, firstPageTop, pageExtent, gap, pageCount)
        )
    }

    @Test
    fun `virtualisation is what keeps a long document cheap`() {
        val hundredPages = CvPageWindow.visible(0f, viewport, firstPageTop, pageExtent, gap, 100)
        assertTrue(
            hundredPages.count() < 10,
            "a hundred-page CV should not draw ${hundredPages.count()} sheets at once"
        )
    }

    // ---------------------------------------------------------------------
    // Page indicator and navigation
    // ---------------------------------------------------------------------

    @Test
    fun `the indicator names the page the viewport is centred on`() {
        fun pageAt(scroll: Float) = CvPageWindow.currentPage(
            scrollOffset = scroll,
            viewportExtent = viewport,
            firstPageTop = firstPageTop,
            pageExtent = pageExtent,
            gap = gap,
            pageCount = pageCount
        )

        assertEquals(1, pageAt(0f))
        assertEquals(4, pageAt(3f * (pageExtent + gap)))
    }

    @Test
    fun `navigating to a page lands on its top edge`() {
        assertEquals(firstPageTop, CvPageWindow.offsetOfPage(1, firstPageTop, pageExtent, gap))
        assertEquals(
            firstPageTop + 2 * (pageExtent + gap),
            CvPageWindow.offsetOfPage(3, firstPageTop, pageExtent, gap)
        )
    }

    // ---------------------------------------------------------------------
    // Fit modes — CV-FR-047
    // ---------------------------------------------------------------------

    private fun fit(mode: CvZoomFit, width: Float, height: Float) =
        CvZoomPolicy.fitPercent(mode, width, height, pageWidth = 600f, pageHeight = 800f)

    @Test
    fun `fit width uses the whole viewport width`() {
        assertEquals(150, fit(CvZoomFit.Width, width = 900f, height = 400f))
    }

    @Test
    fun `fit page is bounded by whichever axis runs out first`() {
        // Width alone would allow 150%, but only half the page's height is on screen.
        assertEquals(50, fit(CvZoomFit.Page, width = 900f, height = 400f))
    }

    @Test
    fun `fit never leaves the sheet a fraction wider than the space it was given`() {
        // 905/600 is 150.83%, and rounding up would put the page outside the viewport it just
        // claimed to fit — a horizontal scrollbar on the control whose job is removing one.
        assertEquals(150, fit(CvZoomFit.Width, width = 905f, height = 2000f))
    }

    @Test
    fun `fit stays inside the zoom policy's own bounds`() {
        assertEquals(CvZoomPolicy.MaximumPercent, fit(CvZoomFit.Width, width = 9000f, height = 9000f))
        assertEquals(CvZoomPolicy.MinimumPercent, fit(CvZoomFit.Width, width = 60f, height = 80f))
    }

    @Test
    fun `an unmeasured viewport is not a request to shrink`() {
        assertEquals(CvZoomPolicy.DefaultPercent, fit(CvZoomFit.Page, width = 0f, height = 0f))
        assertEquals(CvZoomPolicy.DefaultPercent, fit(CvZoomFit.None, width = 900f, height = 400f))
    }
}
