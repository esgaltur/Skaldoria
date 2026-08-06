package com.skaldoria.core.parser

import com.skaldoria.core.models.SlideElement
import com.skaldoria.core.models.SlideLayoutType
import com.skaldoria.core.models.SlideTransition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F-15 / F-16: directive handling, pinned before the duplication is removed.
 *
 * `<!-- poll: … -->` and the bare `poll: …` line form were handled by two **copy-pasted**
 * blocks, and `applyDirective` took three setter lambdas purely because its targets were
 * locals of a 364-line function. These tests fix the observable behaviour of both forms —
 * including the asymmetry between them — so the cleanup cannot change it silently.
 */
class SlideDirectivesTest {

    private fun parseOne(markdown: String) = MarkdownSlideParser.parse(markdown).single()

    private fun pollOf(markdown: String) =
        parseOne(markdown).elements.filterIsInstance<SlideElement.Poll>().single()

    // ---- poll, both spellings ----

    @Test
    fun `comment poll after a title uses the title as the question`() {
        val poll = pollOf(
            """
            ## Pick a datastore
            <!-- poll: PostgreSQL | MongoDB | Redis -->
            """.trimIndent()
        )
        assertEquals("Pick a datastore", poll.question)
        assertEquals(listOf("PostgreSQL", "MongoDB", "Redis"), poll.options)
    }

    @Test
    fun `comment poll before any title falls back to a generic question`() {
        val poll = pollOf(
            """
            <!-- poll: Yes | No -->
            ## Later heading
            """.trimIndent()
        )
        assertEquals("Audience Poll", poll.question)
    }

    /**
     * The line form is only recognised *before* a title, so its question can never be the
     * title — the copy-pasted `if (title.isNotEmpty())` in that branch was unreachable.
     * Pinned deliberately: this is behaviour, not an accident to preserve blindly, but it
     * must not change as part of a de-duplication.
     */
    @Test
    fun `line poll always falls back to a generic question`() {
        val poll = pollOf(
            """
            poll: Alpha | Beta
            ## Heading after
            """.trimIndent()
        )
        assertEquals("Audience Poll", poll.question)
        assertEquals(listOf("Alpha", "Beta"), poll.options)
    }

    @Test
    fun `both poll spellings select the poll layout`() {
        assertEquals(SlideLayoutType.POLL, parseOne("<!-- poll: A | B -->\n## H").layoutType)
        assertEquals(SlideLayoutType.POLL, parseOne("poll: A | B\n## H").layoutType)
    }

    @Test
    fun `vote is an accepted alias for poll`() {
        assertEquals(listOf("A", "B"), pollOf("<!-- vote: A | B -->\n## H").options)
        assertEquals(listOf("A", "B"), pollOf("vote: A | B\n## H").options)
    }

    @Test
    fun `blank poll options are discarded`() {
        assertEquals(listOf("A", "B"), pollOf("## H\n<!-- poll: A |  | B |  -->").options)
    }

    @Test
    fun `a poll with no usable options produces no poll element`() {
        val slide = parseOne("## Heading\n<!-- poll:  |  -->")
        assertTrue(slide.elements.none { it is SlideElement.Poll })
        assertEquals(0, slide.elements.count { it is SlideElement.Poll })
    }

    @Test
    fun `poll options keep their internal spacing but are trimmed`() {
        assertEquals(listOf("Option A", "Option B"), pollOf("## H\n<!-- poll:   Option A |  Option B  -->").options)
    }

    // ---- layout ----

    @Test
    fun `layout aliases all resolve`() {
        val cases = mapOf(
            "hero" to SlideLayoutType.HERO_TITLE,
            "title" to SlideLayoutType.HERO_TITLE,
            "section" to SlideLayoutType.SECTION_HEADER,
            "bullets" to SlideLayoutType.BULLET_LIST,
            "split_code" to SlideLayoutType.SPLIT_TEXT_CODE,
            "image_split" to SlideLayoutType.SPLIT_TEXT_MEDIA,
            "grid" to SlideLayoutType.DATA_TABLE,
            "quote" to SlideLayoutType.BIG_QUOTE,
            "kpi" to SlideLayoutType.BIG_METRIC,
            "terminal" to SlideLayoutType.FULL_CODE,
            "mermaid" to SlideLayoutType.DIAGRAM,
            "formula" to SlideLayoutType.MATH_FORMULA,
            "survey" to SlideLayoutType.POLL
        )
        cases.forEach { (alias, expected) ->
            assertEquals(expected, parseOne("<!-- layout: $alias -->\n## Heading").layoutType, "alias: $alias")
        }
    }

    @Test
    fun `layout aliases are case and hyphen insensitive`() {
        assertEquals(SlideLayoutType.SPLIT_TEXT_CODE, parseOne("<!-- layout: SPLIT-CODE -->\n## H").layoutType)
        assertEquals(SlideLayoutType.BIG_METRIC, parseOne("<!-- layout:  Big-Metric  -->\n## H").layoutType)
    }

    @Test
    fun `an unknown layout falls back to automatic classification`() {
        // Must not throw, and must not pin an arbitrary layout.
        val slide = parseOne("<!-- layout: definitely-not-a-layout -->\n## Heading\n\n- one\n- two")
        assertEquals(SlideLayoutType.BULLET_LIST, slide.layoutType)
    }

    // ---- background and transition ----

    @Test
    fun `background accepts both spellings`() {
        assertEquals("#101828", parseOne("<!-- bg: #101828 -->\n## H").customBackground)
        assertEquals("#101828", parseOne("<!-- background: #101828 -->\n## H").customBackground)
    }

    @Test
    fun `a blank background is ignored`() {
        assertNull(parseOne("<!-- bg:    -->\n## H").customBackground)
    }

    @Test
    fun `transition aliases all resolve`() {
        assertEquals(SlideTransition.FADE, parseOne("<!-- transition: fade -->\n## H").customTransition)
        assertEquals(SlideTransition.SLIDE_HORIZONTAL, parseOne("<!-- transition: slide -->\n## H").customTransition)
        assertEquals(SlideTransition.ZOOM, parseOne("<!-- transition: zoom -->\n## H").customTransition)
        assertEquals(SlideTransition.VERTICAL_SLIDE, parseOne("<!-- transition: vertical -->\n## H").customTransition)
    }

    @Test
    fun `an unknown transition leaves the slide default`() {
        assertNull(parseOne("<!-- transition: barrel-roll -->\n## H").customTransition)
    }

    // ---- interaction with other content ----

    @Test
    fun `a directive comment never renders as slide text`() {
        val slide = parseOne("## Heading\n<!-- layout: quote -->\n> Quoted line")
        assertTrue(
            slide.elements.none { it is SlideElement.Text && (it as SlideElement.Text).content.contains("<!--") },
            "directive markup leaked into rendered content"
        )
    }

    @Test
    fun `the last directive of a kind wins`() {
        assertEquals(SlideTransition.ZOOM, parseOne("<!-- transition: fade -->\n<!-- transition: zoom -->\n## H").customTransition)
    }

    @Test
    fun `a line directive after the title is treated as prose, not a directive`() {
        // The line form is deliberately restricted to the pre-title region so ordinary text
        // such as "background: dark blue" cannot be swallowed as configuration.
        val slide = parseOne("## Heading\ntransition: zoom")
        assertNull(slide.customTransition)
        assertNotNull(slide.elements.filterIsInstance<SlideElement.Text>().firstOrNull { it.content == "transition: zoom" })
    }
}
