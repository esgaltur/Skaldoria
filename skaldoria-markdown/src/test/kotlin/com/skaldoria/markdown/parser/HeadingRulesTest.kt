package com.skaldoria.markdown.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeadingRulesTest {

    @Test
    fun `reports the level and text of an ATX heading`() {
        val heading = HeadingRules.heading("## Start here")

        assertEquals(2, heading?.level)
        assertEquals("Start here", heading?.text)
    }

    @Test
    fun `CommonMark requires whitespace after the hashes`() {
        assertNull(HeadingRules.heading("#hashtag"))
    }

    /**
     * `textStart` is what the writer's outline navigates by. Searching the line for the heading
     * text instead would find an earlier coincidence in every case below.
     */
    @Test
    fun `textStart points at the heading text, not at a coincidental earlier match`() {
        assertEquals(2, HeadingRules.heading("# a")?.textStart)
        assertEquals(4, HeadingRules.heading("### hashes ### inside")?.textStart)
        // "## nested" also occurs at offset 4; a naive indexOf would be right here by luck.
        assertEquals(4, HeadingRules.heading("### ## nested")?.textStart)
    }

    @Test
    fun `textStart is an offset into the original line, indent included`() {
        val line = "    ## Indented"

        val heading = HeadingRules.heading(line)

        assertEquals("Indented", heading?.text)
        assertEquals(7, heading?.textStart)
        assertEquals("Indented", line.substring(heading!!.textStart))
    }

    @Test
    fun `textStart survives extra space between the marker and the text`() {
        val line = "#     Spaced   "

        val heading = HeadingRules.heading(line)

        assertEquals("Spaced", heading?.text)
        assertEquals("Spaced", line.substring(heading!!.textStart, heading.textStart + heading.text.length))
    }
}
