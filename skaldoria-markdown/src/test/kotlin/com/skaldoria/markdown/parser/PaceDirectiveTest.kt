package com.skaldoria.markdown.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * DEL-11: the `<!-- pace: 90s -->` directive and the duration grammar behind it.
 *
 * Lives in this module rather than beside `PacingPlanTest` because
 * [MarkdownSlideParser.parseDurationSeconds] is `internal` — parser grammar is not app-module
 * API, and making it public so a test elsewhere could reach it would be exposing an
 * implementation detail for the convenience of the test that checks it.
 */
class PaceDirectiveTest {

    @Test
    fun `the pace directive is parsed off a slide`() {
        val slides = MarkdownSlideParser.parse(
            "# Intro\n\n<!-- pace: 45s -->\n\n- a\n\n---\n\n# Demo\n\n<!-- pace: 5m -->\n\n- b\n\n---\n\n# Rest\n\n- c"
        )

        assertEquals(45L, slides[0].paceSeconds)
        assertEquals(300L, slides[1].paceSeconds)
        assertNull(slides[2].paceSeconds, "a slide that declares nothing takes an even share")
    }

    @Test
    fun `time and budget are accepted as synonyms`() {
        assertEquals(60L, MarkdownSlideParser.parse("# A\n\n<!-- time: 60s -->\n\n- x").single().paceSeconds)
        assertEquals(60L, MarkdownSlideParser.parse("# A\n\n<!-- budget: 1m -->\n\n- x").single().paceSeconds)
    }

    @Test
    fun `durations are accepted in the forms a speaker writes them`() {
        assertEquals(90L, MarkdownSlideParser.parseDurationSeconds("90"))
        assertEquals(90L, MarkdownSlideParser.parseDurationSeconds("90s"))
        assertEquals(120L, MarkdownSlideParser.parseDurationSeconds("2m"))
        assertEquals(90L, MarkdownSlideParser.parseDurationSeconds("1m30s"))
        assertEquals(90L, MarkdownSlideParser.parseDurationSeconds(" 1M 30S "))
    }

    @Test
    fun `nonsense and zero durations are rejected rather than becoming a zero budget`() {
        // A zero-second slide would vanish from the schedule, which is never what was meant.
        assertNull(MarkdownSlideParser.parseDurationSeconds("0"))
        assertNull(MarkdownSlideParser.parseDurationSeconds("0s"))
        assertNull(MarkdownSlideParser.parseDurationSeconds("soon"))
        assertNull(MarkdownSlideParser.parseDurationSeconds("5x"))
        assertNull(MarkdownSlideParser.parseDurationSeconds("90s later"))
        assertNull(MarkdownSlideParser.parseDurationSeconds(""))
    }

    @Test
    fun `a mistyped budget leaves the deck usable`() {
        // Consistent with every other directive here: unparseable values are ignored. A talk
        // must not fail to open because one budget was typed wrong.
        val slides = MarkdownSlideParser.parse("# Intro\n\n<!-- pace: aboutaminute -->\n\n- a")

        assertEquals(1, slides.size, "the deck still parses")
        assertNull(slides[0].paceSeconds, "the slide simply takes an even share")
    }

    @Test
    fun `the directive never renders as slide content`() {
        val slide = MarkdownSlideParser.parse("# Intro\n\n<!-- pace: 45s -->\n\n- a point").single()

        assertEquals(
            emptyList(),
            slide.elements.filter { it.toString().contains("pace", ignoreCase = true) },
            "the budget leaked onto the slide: ${slide.elements}"
        )
    }
}
