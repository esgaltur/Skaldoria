package com.skaldoria.markdown.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineRunsTest {

    private fun textOf(markdown: String) = InlineRuns.parse(markdown).joinToString("") { it.text }

    @Test
    fun `plain text is one run`() {
        assertEquals(InlineRuns.parse("Just prose."), listOf(InlineRun("Just prose.")))
    }

    @Test
    fun `delimiters are removed from the rendered text`() {
        assertEquals("bold italic code struck", textOf("**bold** *italic* `code` ~~struck~~"))
    }

    @Test
    fun `each marker sets its own flag`() {
        val runs = InlineRuns.parse("**b** *i* `c` ~~s~~")
        assertTrue(runs.single { it.text == "b" }.bold)
        assertTrue(runs.single { it.text == "i" }.italic)
        assertTrue(runs.single { it.text == "c" }.code)
        assertTrue(runs.single { it.text == "s" }.strikethrough)
    }

    @Test
    fun `emphasis nests`() {
        val inner = InlineRuns.parse("**bold with *italic* inside**").single { it.text == "italic" }
        assertTrue(inner.bold && inner.italic, "the inner run inherits bold")
    }

    @Test
    fun `underscore forms work too`() {
        assertTrue(InlineRuns.parse("__b__").single().bold)
        assertTrue(InlineRuns.parse("_i_").single().italic)
    }

    @Test
    fun `inline code is literal`() {
        val runs = InlineRuns.parse("`a **b** c`")
        assertEquals("a **b** c", runs.single().text, "markers inside code are not markup")
        assertTrue(runs.single().code)
    }

    @Test
    fun `a link carries its target and drops its syntax`() {
        val runs = InlineRuns.parse("Mail [Ada](mailto:ada@example.com) now")
        val link = runs.single { it.link != null }
        assertEquals("Ada", link.text)
        assertEquals("mailto:ada@example.com", link.link)
        assertEquals("Mail Ada now", textOf("Mail [Ada](mailto:ada@example.com) now"))
    }

    @Test
    fun `a link label may carry emphasis`() {
        val run = InlineRuns.parse("[**Site**](https://example.com)").single()
        assertTrue(run.bold)
        assertEquals("https://example.com", run.link)
    }

    @Test
    fun `an unterminated marker stays literal`() {
        // Half-typed emphasis must not restyle the rest of the CV while the user is editing.
        assertEquals("A **partial line", textOf("A **partial line"))
        assertEquals("50% * 2 things", textOf("50% * 2 things"))
    }

    @Test
    fun `adjacent runs with identical formatting are merged`() {
        assertEquals(1, InlineRuns.parse("plain text with no markers").size)
        // `a`, `b`(italic), `c` -> three runs, not more.
        assertEquals(3, InlineRuns.parse("a*b*c").size)
    }

    @Test
    fun `empty input yields no runs`() {
        assertTrue(InlineRuns.parse("").isEmpty())
    }
}
