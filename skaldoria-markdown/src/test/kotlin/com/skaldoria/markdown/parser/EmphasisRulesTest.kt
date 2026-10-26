package com.skaldoria.markdown.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shared flanking rules. [InlineRunsTest] covers how runs nest; this covers *where* a
 * delimiter may open and close, which every inline renderer now takes from [EmphasisRules].
 */
class EmphasisRulesTest {

    private fun plain(markdown: String) = InlineRuns.parse(markdown).none { it.italic || it.bold }

    @Test
    fun `underscores inside a word are not emphasis`() {
        assertTrue(plain("call snake_case_name here"), "intraword _ must stay literal")
        assertEquals("call snake_case_name here", InlineRuns.parse("call snake_case_name here").single().text)
    }

    @Test
    fun `a handle with underscores survives a CV intact`() {
        assertTrue(plain("GitHub: jane_doe_dev · mail: jane_doe@example.com"))
    }

    @Test
    fun `spaced asterisks are arithmetic, not emphasis`() {
        assertTrue(plain("2 * 3 * 4 = 24"))
    }

    @Test
    fun `a delimiter followed by whitespace does not open`() {
        assertNull(EmphasisRules.match("* not a list *", 0))
    }

    @Test
    fun `ordinary emphasis still works`() {
        assertTrue(InlineRuns.parse("_word_").single().italic)
        assertTrue(InlineRuns.parse("*word*").single().italic)
        assertTrue(InlineRuns.parse("__word__").single().bold)
        assertTrue(InlineRuns.parse("**word**").single().bold)
    }

    @Test
    fun `underscore emphasis next to punctuation still works`() {
        val run = InlineRuns.parse("(_see_ above)").single { it.italic }
        assertEquals("see", run.text)
    }

    @Test
    fun `asterisks may emphasise inside a word`() {
        assertEquals("b", InlineRuns.parse("a*b*c").single { it.italic }.text)
    }

    @Test
    fun `a single delimiter does not close on half of a double`() {
        val runs = InlineRuns.parse("*see **this** too*")
        assertTrue(runs.all { it.italic }, "the italic must span the whole phrase: $runs")
        assertEquals("this", runs.single { it.bold }.text)
    }
}
