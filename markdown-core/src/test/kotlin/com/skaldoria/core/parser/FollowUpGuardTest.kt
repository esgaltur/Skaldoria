package com.skaldoria.core.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The pre-scan guard on `extractFollowUpQuestions` must be a **superset** of what the real scan
 * can match — it may run the scan unnecessarily, but it must never skip a document that would
 * have produced an item.
 *
 * PRF-6 added the guard because the scan ran at full cost on every deck change even when the deck
 * contained no follow-ups at all. The failure mode being guarded against here is silent: a guard
 * that only looked for `parking-lot:` would drop every checkbox-derived item and no existing test
 * would have noticed, because `extractFollowUpQuestions` returning empty is indistinguishable from
 * a document that genuinely has none.
 */
class FollowUpGuardTest {

    @Test
    fun `directive comments are found`() {
        val items = MarkdownSlideParser.extractFollowUpQuestions(
            "# Slide\n\n<!-- parking-lot: [ ] Why does it do that? | id:abc -->\n"
        )
        assertEquals(1, items.size)
        assertEquals("Why does it do that?", items.single().question)
    }

    @Test
    fun `directive aliases are found`() {
        for (alias in listOf("parking-lot", "parking_lot", "followup", "follow-up")) {
            val items = MarkdownSlideParser.extractFollowUpQuestions(
                "<!-- $alias: [ ] Question for $alias? -->"
            )
            assertTrue(items.isNotEmpty(), "alias '$alias' must still be recognised")
        }
    }

    /** The case a `parking-lot:`-only guard would have silently dropped. */
    @Test
    fun `checkbox task lists are found with no directive comment anywhere`() {
        val markdown = """
            # Follow Ups

            - [ ] Does the guard let this through?
            - [x] Answered one — with an answer
        """.trimIndent()

        assertTrue(!markdown.contains("<!--"), "fixture must not contain a directive comment")

        val items = MarkdownSlideParser.extractFollowUpQuestions(markdown)
        assertEquals(2, items.size, "both checkbox items must survive the guard")
    }

    @Test
    fun `checkbox markers are found through tabs and extra spacing`() {
        // CHECKBOX_LINE_REGEX allows `\s*` between the dash and the bracket, so the guard has to
        // skip tabs as well as spaces or it would reject a line the scan would have matched.
        val items = MarkdownSlideParser.extractFollowUpQuestions("-\t[ ] Tab separated question?")
        assertTrue(items.isNotEmpty(), "a tab between the dash and bracket must not defeat the guard")
    }

    @Test
    fun `a document with neither marker yields nothing`() {
        val markdown = """
            # Ordinary Deck

            - A normal bullet
            - Another one, with a dash - inside it

            Some prose with a [link](https://example.com) in it.
        """.trimIndent()

        assertEquals(emptyList(), MarkdownSlideParser.extractFollowUpQuestions(markdown))
    }
}
