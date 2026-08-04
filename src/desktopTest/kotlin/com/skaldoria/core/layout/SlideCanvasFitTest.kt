package com.skaldoria.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OVF-1 — covers the slide sizing arithmetic. This logic used to live inline in
 * `SlideSurface`, where it was unreachable by tests (the project has no Compose UI test
 * harness); extracting it is what makes these edge cases verifiable.
 */
class SlideCanvasFitTest {

    private val designW = 1280f
    private val designH = 720f

    // -----------------------------------------------------------------
    // fitDesignCanvas — letterboxing the 16:9 canvas into the window
    // -----------------------------------------------------------------

    @Test
    fun `exact design size fits at scale one`() {
        val fit = SlideCanvasFit.fitDesignCanvas(1280f, 720f, designW, designH)

        assertEquals(1280f, fit.width, 0.01f)
        assertEquals(720f, fit.height, 0.01f)
        assertEquals(1f, fit.scale, 0.001f)
    }

    @Test
    fun `wider-than-16to9 window is bound by height and pillarboxed`() {
        // 2000x720 is wider than 16:9, so height decides.
        val fit = SlideCanvasFit.fitDesignCanvas(2000f, 720f, designW, designH)

        assertEquals(720f, fit.height, 0.01f)
        assertEquals(1280f, fit.width, 0.01f, "width should stay at the 16:9 ratio, not fill 2000")
        assertEquals(1f, fit.scale, 0.001f)
    }

    @Test
    fun `taller-than-16to9 window is bound by width and letterboxed`() {
        // 1280x2000 is taller than 16:9, so width decides.
        val fit = SlideCanvasFit.fitDesignCanvas(1280f, 2000f, designW, designH)

        assertEquals(1280f, fit.width, 0.01f)
        assertEquals(720f, fit.height, 0.01f, "height should stay at the 16:9 ratio, not fill 2000")
    }

    @Test
    fun `larger window scales the design canvas up uniformly`() {
        val fit = SlideCanvasFit.fitDesignCanvas(2560f, 1440f, designW, designH)

        assertEquals(2f, fit.scale, 0.001f)
        assertEquals(2560f, fit.width, 0.01f)
        assertEquals(1440f, fit.height, 0.01f)
    }

    /**
     * Compose emits unmeasured/degenerate bounds during the first measure pass. These
     * must not produce NaN sizes or a zero scale, which would blank the slide.
     */
    @Test
    fun `degenerate bounds fall back to the design size`() {
        val cases = listOf(
            "zero" to Pair(0f, 0f),
            "negative" to Pair(-100f, -50f),
            "NaN" to Pair(Float.NaN, Float.NaN),
            "infinite" to Pair(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )

        for ((label, bounds) in cases) {
            val fit = SlideCanvasFit.fitDesignCanvas(bounds.first, bounds.second, designW, designH)

            assertTrue(fit.width.isFinite() && fit.width > 0f, "$label: width must be usable")
            assertTrue(fit.height.isFinite() && fit.height > 0f, "$label: height must be usable")
            assertTrue(fit.scale.isFinite() && fit.scale > 0f, "$label: scale must be usable")
        }
    }

    // -----------------------------------------------------------------
    // contentScale — shrinking overflowing content
    // -----------------------------------------------------------------

    @Test
    fun `content that already fits is never enlarged`() {
        // Authored size must be preserved; blowing small content up would break typography.
        assertEquals(1f, SlideCanvasFit.contentScale(600, 300, 1280, 720), 0.001f)
        assertEquals(1f, SlideCanvasFit.contentScale(1280, 720, 1280, 720), 0.001f)
    }

    @Test
    fun `vertically overflowing content shrinks to fit`() {
        // 1440 tall in a 720 box -> half size. This is the bullet-list overflow case.
        assertEquals(0.5f, SlideCanvasFit.contentScale(1280, 1440, 1280, 720), 0.001f)
    }

    @Test
    fun `horizontally overflowing content shrinks to fit`() {
        // The wide-diagram case: a node chain that outgrew the canvas.
        assertEquals(0.5f, SlideCanvasFit.contentScale(2560, 720, 1280, 720), 0.001f)
    }

    @Test
    fun `the tighter of the two axes wins`() {
        // Width would allow 0.8, height only 0.6 -> 0.6 must win.
        val scale = SlideCanvasFit.contentScale(1600, 1200, 1280, 720)
        assertEquals(0.6f, scale, 0.001f)
    }

    @Test
    fun `scale never drops below the legibility floor`() {
        // 10x too tall would compute 0.1; the floor stops it at 0.5.
        val scale = SlideCanvasFit.contentScale(1280, 7200, 1280, 720, minScale = 0.5f)
        assertEquals(0.5f, scale, 0.001f)
    }

    @Test
    fun `overflow beyond the floor is reported so the author can be warned`() {
        assertFalse(
            SlideCanvasFit.isOverflowing(1280, 720, 1280, 720),
            "content that fits is not overflowing"
        )
        assertFalse(
            SlideCanvasFit.isOverflowing(1280, 1440, 1280, 720, minScale = 0.5f),
            "content that fits once shrunk to exactly the floor is not overflowing"
        )
        assertTrue(
            SlideCanvasFit.isOverflowing(1280, 7200, 1280, 720, minScale = 0.5f),
            "content still clipping at the floor must be reported"
        )
    }

    @Test
    fun `degenerate measurements do not produce a broken scale`() {
        assertEquals(1f, SlideCanvasFit.contentScale(0, 0, 1280, 720), 0.001f)
        assertEquals(1f, SlideCanvasFit.contentScale(-5, -5, 1280, 720), 0.001f)
        assertEquals(1f, SlideCanvasFit.contentScale(1280, 720, 0, 0), 0.001f)
        assertFalse(SlideCanvasFit.isOverflowing(0, 0, 1280, 720))
    }
}
