package com.skaldoria.core.document

import com.skaldoria.core.parser.MarkdownSlideParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EDT-3 guard.
 *
 * The three deck shapes below are the ones COR-1 identified as divergent — a `---` separator,
 * a `#`/`##` heading split with no separator at all, and a four-dash `----` rule. A mapping
 * that quietly assumed one shape is exactly how the old private splitter came to disagree with
 * the parser, so every assertion here runs against all three.
 */
class SlideSourceLocatorTest {

    private val dashDelimited = """
        # Opening

        The first slide.

        ---

        # Middle

        The second slide.

        ---

        # Closing

        The third slide.
    """.trimIndent()

    private val headingSplit = """
        # Opening

        The first slide.

        ## Middle

        The second slide.

        ## Closing

        The third slide.
    """.trimIndent()

    private val ruleDelimited = """
        # Opening

        The first slide.

        ----

        # Middle

        The second slide.

        ----

        # Closing

        The third slide.
    """.trimIndent()

    private val decks = mapOf(
        "--- delimited" to dashDelimited,
        "heading split" to headingSplit,
        "---- rule" to ruleDelimited
    )

    @Test
    fun `every slide offset maps back to that slide`() {
        for ((shape, markdown) in decks) {
            val slides = MarkdownSlideParser.parse(markdown)
            assertEquals(3, slides.size, "$shape should parse to three slides")

            for (index in slides.indices) {
                val offset = SlideSourceLocator.offsetOfSlideIndex(markdown, slides, index)
                assertEquals(
                    index,
                    SlideSourceLocator.slideIndexAtOffset(markdown, slides, offset),
                    "$shape: offset $offset for slide $index did not round-trip"
                )
            }
        }
    }

    @Test
    fun `a slide offset lands on that slide's own text`() {
        for ((shape, markdown) in decks) {
            val slides = MarkdownSlideParser.parse(markdown)

            // The user-visible outcome, not an intermediate index: the caret must land where
            // the slide's source actually begins. Landing on the blank line the parser started
            // accumulating at, or on the separator above it, is the failure this catches.
            assertTrue(
                markdown.substring(SlideSourceLocator.offsetOfSlideIndex(markdown, slides, 1))
                    .startsWith("# Middle") ||
                    markdown.substring(SlideSourceLocator.offsetOfSlideIndex(markdown, slides, 1))
                        .startsWith("## Middle"),
                "$shape: slide 2's offset should sit on its heading"
            )
        }
    }

    @Test
    fun `every offset in the document maps to some slide`() {
        for ((shape, markdown) in decks) {
            val slides = MarkdownSlideParser.parse(markdown)

            // Separator lines belong to no slide's range. The mapping still has to answer, and
            // the answer has to be a real slide — a caret parked on a `---` is ordinary.
            for (offset in markdown.indices) {
                val index = SlideSourceLocator.slideIndexAtOffset(markdown, slides, offset)
                assertTrue(index in slides.indices, "$shape: offset $offset mapped to $index")
            }
        }
    }

    @Test
    fun `an offset inside a slide's body selects that slide, not the one before it`() {
        val markdown = dashDelimited
        val slides = MarkdownSlideParser.parse(markdown)

        val offset = markdown.indexOf("The second slide.")
        assertEquals(1, SlideSourceLocator.slideIndexAtOffset(markdown, slides, offset))
    }

    @Test
    fun `offsets are character offsets, not line-length sums, on CRLF source`() {
        // A deck authored on Windows. Summing `line.length + 1` drifts by one character per
        // line, so by the third slide the caret would land mid-word.
        val markdown = dashDelimited.replace("\n", "\r\n")
        val slides = MarkdownSlideParser.parse(markdown)

        val offset = SlideSourceLocator.offsetOfSlideIndex(markdown, slides, 2)
        assertTrue(
            markdown.substring(offset).startsWith("# Closing"),
            "expected the third slide's heading, got '${markdown.substring(offset).take(20)}'"
        )
    }

    @Test
    fun `an out-of-range offset is clamped rather than thrown`() {
        val slides = MarkdownSlideParser.parse(dashDelimited)

        assertEquals(0, SlideSourceLocator.slideIndexAtOffset(dashDelimited, slides, -50))
        assertEquals(
            slides.lastIndex,
            SlideSourceLocator.slideIndexAtOffset(dashDelimited, slides, dashDelimited.length + 500)
        )
    }

    @Test
    fun `empty markdown has a slide and a usable offset`() {
        val slides = MarkdownSlideParser.parse("")

        // The parser emits one synthetic slide with no source range at all.
        assertEquals(1, slides.size)
        assertEquals(0, SlideSourceLocator.offsetOfSlideIndex("", slides, 0))
        assertEquals(0, SlideSourceLocator.slideIndexAtOffset("", slides, 0))
    }

    @Test
    fun `an unknown slide index yields the start of the document`() {
        val slides = MarkdownSlideParser.parse(dashDelimited)
        assertEquals(0, SlideSourceLocator.offsetOfSlideIndex(dashDelimited, slides, 99))
    }
}
