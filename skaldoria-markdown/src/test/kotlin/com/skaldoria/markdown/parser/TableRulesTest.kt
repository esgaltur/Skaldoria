package com.skaldoria.markdown.parser

import com.skaldoria.markdown.models.SlideElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * AUT-17: GFM tables with and without the outer pipes, and the things that must *not* become
 * tables now that the rule is looser.
 *
 * The widening half is the feature; the second half is the regression surface. `TableRule` sits
 * above `HeadingRule` in `BLOCK_RULES` and now claims any line containing a pipe whose
 * neighbour is a delimiter, so the cases that must stay prose, stay a slide break, or stay code
 * are pinned here deliberately.
 */
class TableRulesTest {

    private fun parseOne(markdown: String) = MarkdownSlideParser.parse(markdown).single()

    private fun tableIn(markdown: String): SlideElement.Table? =
        parseOne(markdown).elements.filterIsInstance<SlideElement.Table>().singleOrNull()

    // ---- the grammar itself -------------------------------------------------

    @Test
    fun `a delimiter row needs a pipe, so a thematic break is not one`() {
        assertTrue(TableRules.isSeparatorRow("---|---"))
        assertTrue(TableRules.isSeparatorRow("| --- | --- |"))
        assertTrue(TableRules.isSeparatorRow("| :--- | ---: | :---: |"))
        assertFalse(TableRules.isSeparatorRow("---"), "a bare --- is a slide break, not a table")
        assertFalse(TableRules.isSeparatorRow("***"))
        assertFalse(TableRules.isSeparatorRow("___"))
    }

    @Test
    fun `a row of empty cells is data, not a delimiter`() {
        assertFalse(TableRules.isSeparatorRow("| |"), "no dash, so nothing is being delimited")
        assertFalse(TableRules.isSeparatorRow("|   |   |"))
    }

    // ---- the feature --------------------------------------------------------

    @Test
    fun `a table without outer pipes is parsed`() {
        val table = tableIn(
            """
            # Decks

            Deck | Purpose
            ---|---
            intro | the opening
            close | the ask
            """.trimIndent()
        )

        assertEquals(listOf("Deck", "Purpose"), table?.headers)
        assertEquals(listOf(listOf("intro", "the opening"), listOf("close", "the ask")), table?.rows)
    }

    @Test
    fun `a table with outer pipes still parses identically`() {
        val without = tableIn("# T\n\nDeck | Purpose\n---|---\nintro | the opening")
        val with = tableIn("# T\n\n| Deck | Purpose |\n| --- | --- |\n| intro | the opening |")

        assertEquals(with?.headers, without?.headers)
        assertEquals(with?.rows, without?.rows)
    }

    @Test
    fun `alignment markers and padded delimiters are accepted`() {
        val table = tableIn(
            """
            # T

            Left | Middle | Right
            :--- | :----: | ----:
            a | b | c
            """.trimIndent()
        )

        assertEquals(listOf("Left", "Middle", "Right"), table?.headers)
        assertEquals(listOf(listOf("a", "b", "c")), table?.rows)
    }

    // ---- the regression surface --------------------------------------------

    @Test
    fun `prose containing a pipe stays prose`() {
        val slide = parseOne("# T\n\nRun `a | b` to pipe the output, then stop.")

        assertTrue(
            slide.elements.none { it is SlideElement.Table },
            "a lone pipe in a sentence must not build a table: ${slide.elements}"
        )
    }

    @Test
    fun `a bare thematic break still splits slides`() {
        val slides = MarkdownSlideParser.parse("# One\n\n- a\n\n---\n\n# Two\n\n- b")

        assertEquals(2, slides.size, "--- stopped separating slides")
        assertEquals("One", slides[0].title)
        assertEquals("Two", slides[1].title)
    }

    @Test
    fun `a pipe line inside a fenced block stays code`() {
        val slide = parseOne(
            """
            # T

            ```text
            Deck | Purpose
            ---|---
            intro | the opening
            ```
            """.trimIndent()
        )

        assertTrue(
            slide.elements.none { it is SlideElement.Table },
            "a table drawn inside a code fence must render as code: ${slide.elements}"
        )
        assertTrue(slide.elements.any { it is SlideElement.CodeBlock }, "the fence was lost")
    }

    @Test
    fun `a pipe line with no delimiter anywhere stays prose`() {
        val slide = parseOne("# T\n\nDeck | Purpose\nintro | the opening")

        assertTrue(
            slide.elements.none { it is SlideElement.Table },
            "without a delimiter row these are two ordinary lines: ${slide.elements}"
        )
    }
}
