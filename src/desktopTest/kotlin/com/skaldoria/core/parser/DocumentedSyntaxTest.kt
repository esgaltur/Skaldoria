package com.skaldoria.core.parser

import com.skaldoria.core.models.SlideElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The authoring syntax the user guide documents is the syntax the parser accepts.
 *
 * `USER_GUIDE.md` documented a `::: notes` fenced block for speaker notes. **The parser has
 * never supported it.** A `:::` line falls through to the paragraph rule, so anyone following
 * the guide got their private notes rendered on the slide as visible body text — the opposite
 * of what a speaker note is for.
 *
 * Documentation drift is not catchable by testing the parser against itself, so this pins the
 * syntax the docs promise.
 */
class DocumentedSyntaxTest {

    private fun parseOne(markdown: String) = MarkdownSlideParser.parse(markdown).single()

    @Test
    fun `an HTML comment note is captured and never rendered`() {
        val slide = parseOne("# Architecture\n\n<!-- note: Emphasize latency -->\n\n- A point")

        assertEquals(listOf("Emphasize latency"), slide.notes)
        assertTrue(
            slide.elements.none { it is SlideElement.Text && it.content.contains("Emphasize") },
            "a speaker note must not reach the slide body"
        )
    }

    @Test
    fun `a blockquote note is captured and never rendered`() {
        val slide = parseOne("# Architecture\n\n> note: Check the time here\n\n- A point")

        assertEquals(listOf("Check the time here"), slide.notes)
        assertTrue(
            slide.elements.none { it is SlideElement.Quote },
            "`> note:` is a directive, not a quotation"
        )
    }

    @Test
    fun `speaker is accepted as a synonym for note`() {
        assertEquals(listOf("Same thing"), parseOne("# H\n\n<!-- speaker: Same thing -->").notes)
    }

    @Test
    fun `several notes on one slide all survive`() {
        val slide = parseOne(
            """
            # Architecture

            <!-- note: First -->
            <!-- note: Second -->

            > note: Third
            """.trimIndent()
        )
        assertEquals(listOf("First", "Second", "Third"), slide.notes)
    }

    /**
     * The syntax the guide used to promise. Kept as a test so that if `:::` is ever
     * implemented, this fails and the documentation is updated deliberately rather than the
     * guide quietly becoming right again by accident.
     */
    @Test
    fun `the fenced colon syntax is still not a note`() {
        val slide = parseOne("# H\n\n::: notes\nPrivate thought\n:::")
        assertTrue(slide.notes.isEmpty(), "if this now works, update USER_GUIDE.md")
    }

    @Test
    fun `the documented poll directive is the one the parser reads`() {
        val poll = parseOne("## Pick one\n\n<!-- poll: A | B | C -->")
            .elements.filterIsInstance<SlideElement.Poll>().single()
        assertEquals(listOf("A", "B", "C"), poll.options)
    }

    @Test
    fun `the documented code-highlight syntax parses`() {
        val code = parseOne("## Code\n\n```kotlin [1-2|4]\nval a = 1\nval b = 2\nval c = 3\nval d = 4\n```")
            .elements.filterIsInstance<SlideElement.CodeBlock>().single()

        assertEquals("kotlin", code.language)
        assertTrue(code.highlightedLines.containsAll(listOf(1, 2, 4)), "actual: ${code.highlightedLines}")
        assertFalse(3 in code.highlightedLines)
    }
}
