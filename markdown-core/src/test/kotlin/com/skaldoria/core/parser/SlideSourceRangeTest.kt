package com.skaldoria.core.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Direct assertions on `Slide.sourceLineRange` — the contract structural editing rests on.
 *
 * Before this test the property had **no direct coverage anywhere**: one grep hit in the whole
 * test tree, and it was a comment. It was exercised only indirectly, through `SlideDocumentTest`
 * and `DeckDocumentTest` behaviour, which would report a break as "duplicate put the slide in the
 * wrong place" rather than "the range contract changed".
 *
 * COR-1 exists because a second splitter once disagreed with the parser about boundaries and
 * edits silently hit the wrong slide. `move`, `duplicate`, `delete` and `insert` all read these
 * ranges, so anything that reshapes the parser has to keep every property below true.
 *
 * Deliberately property-based rather than golden constants: the invariants are what the editing
 * code actually depends on, and they stay meaningful when the corpus changes.
 */
class SlideSourceRangeTest {

    private fun assertRangeInvariants(markdown: String, case: String) {
        val lines = markdown.lines()
        val slides = MarkdownSlideParser.parse(markdown)

        assertTrue(slides.isNotEmpty(), "$case: expected at least one slide")

        var previousEnd = -1
        for ((index, slide) in slides.withIndex()) {
            val range = slide.sourceLineRange

            assertTrue(
                range.first >= 0 && range.last < lines.size,
                "$case: slide $index range $range outside document bounds 0..${lines.size - 1}"
            )
            assertTrue(
                range.first <= range.last,
                "$case: slide $index range $range is inverted"
            )
            assertTrue(
                range.first > previousEnd,
                "$case: slide $index range $range overlaps or precedes the previous slide " +
                    "(previous ended at $previousEnd)"
            )
            previousEnd = range.last
        }
    }

    @Test
    fun `ranges are ordered, in bounds and non-overlapping across split styles`() {
        assertRangeInvariants(
            """
            # First

            Prose one.

            ---

            # Second

            Prose two.
            """.trimIndent(),
            "horizontal rule split"
        )

        assertRangeInvariants(
            """
            ## Alpha

            Prose alpha.

            ## Beta

            Prose beta.

            ## Gamma

            Prose gamma.
            """.trimIndent(),
            "heading split"
        )
    }

    @Test
    fun `each slide's range slices back to source containing its own title`() {
        val markdown = """
            # Introduction

            Opening remarks.

            ---

            # Architecture

            The design.

            ---

            # Conclusion

            Wrapping up.
        """.trimIndent()

        val lines = markdown.lines()
        val slides = MarkdownSlideParser.parse(markdown)

        assertEquals(3, slides.size, "three slides expected")

        for (slide in slides) {
            val sliced = lines.subList(slide.sourceLineRange.first, slide.sourceLineRange.last + 1)
                .joinToString("\n")
            val title = slide.title
            assertTrue(
                sliced.contains(title),
                "slide titled '$title' has range ${slide.sourceLineRange}, which slices to text " +
                    "not containing its own title:\n$sliced"
            )
        }
    }

    @Test
    fun `a horizontal rule inside a code fence does not split the deck`() {
        // COR-1's exact failure shape: a splitter that recognises a bare `---` without tracking
        // fence state cuts the deck in the middle of a code sample.
        val markdown = """
            # Only Slide

            ```kotlin
            val separator = "---"
            ---
            val after = 1
            ```

            Text after the fence.
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(markdown)
        assertEquals(
            1, slides.size,
            "a `---` inside a fence must not create a slide boundary; got ${slides.size} slides"
        )
        assertRangeInvariants(markdown, "fence containing a rule")
    }

    @Test
    fun `ranges stay valid when the deck opens with a split and ends with blank lines`() {
        // Leading/trailing edge cases are where off-by-one range bugs hide.
        assertRangeInvariants(
            "---\n\n# After A Leading Rule\n\nBody.\n\n\n",
            "leading rule and trailing blanks"
        )
    }
}
