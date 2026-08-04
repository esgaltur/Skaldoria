package com.skaldoria.core

import com.skaldoria.core.models.SlideLayoutType
import com.skaldoria.core.parser.MarkdownSlideParser
import com.skaldoria.export.DeckExporter
import com.skaldoria.state.PresentationState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PHASE 0 SAFETY NET — see docs/REMEDIATION_PLAN.md.
 *
 * These tests pin down what the app does *today*, so that the refactors in later
 * phases are visible instead of silent. They are deliberately broad rather than
 * deep: slide counts, titles, layout types, and document structure.
 *
 * IMPORTANT — some assertions below encode KNOWN-BUGGY behaviour on purpose.
 * They are tagged with the plan ID that will change them. When you fix that item,
 * you are expected to UPDATE the assertion, not preserve it. Any assertion NOT
 * tagged is intended behaviour and breaking it is a real regression.
 */
class CharacterizationTest {

    // ---------------------------------------------------------------------
    // Parser — built-in decks
    // ---------------------------------------------------------------------

    @Test
    fun `sample deck parses to a stable slide structure`() {
        val slides = MarkdownSlideParser.parse(PresentationState.DEFAULT_SAMPLE_MARKDOWN)

        assertEquals(9, slides.size, "sample deck slide count")
        assertEquals(
            listOf(
                "Next-Gen Multiplatform Systems",
                "The Cross-Platform Dilemma",
                "Distributed Pipeline Architecture",
                "Algorithmic Pacing Formula",
                "Clean Engine Architecture",
                "120 FPS",
                // No heading on the quote slide, so the parser falls back to a
                // positional placeholder title.
                "Slide 7",
                "Performance Comparison",
                "Empower Your Audience"
            ),
            slides.map { it.title }
        )
        assertEquals(
            listOf(
                SlideLayoutType.HERO_TITLE,
                SlideLayoutType.BULLET_LIST,
                SlideLayoutType.DIAGRAM,
                SlideLayoutType.MATH_FORMULA,
                SlideLayoutType.SPLIT_TEXT_CODE,
                SlideLayoutType.BIG_METRIC,
                SlideLayoutType.BIG_QUOTE,
                SlideLayoutType.DATA_TABLE,
                SlideLayoutType.BULLET_LIST
            ),
            slides.map { it.layoutType }
        )

        // Speaker notes must survive parsing — 7 of the 9 slides carry one.
        assertEquals(7, slides.count { it.notes.isNotEmpty() }, "slides carrying speaker notes")
    }

    @Test
    fun `blank starter deck parses to three slides`() {
        val slides = MarkdownSlideParser.parse(PresentationState.BLANK_STARTER_MARKDOWN)

        assertEquals(3, slides.size)
        assertEquals(listOf("Your Presentation Title", "First Topic", "Add Anything"), slides.map { it.title })
        assertEquals(SlideLayoutType.HERO_TITLE, slides[0].layoutType)
        assertEquals(SlideLayoutType.BULLET_LIST, slides[1].layoutType)
        assertEquals(SlideLayoutType.BIG_QUOTE, slides[2].layoutType)
    }

    @Test
    fun `example modular project deck parses to six slides`() {
        val slidesDir = File("examples/modular_project_deck/slides")
        assertTrue(slidesDir.isDirectory, "example deck missing at ${slidesDir.absolutePath}")

        val combined = slidesDir.listFiles { f -> f.extension == "md" }
            ?.sortedBy { it.name }
            ?.joinToString("\n\n---\n\n") { it.readText() }
            .orEmpty()

        val slides = MarkdownSlideParser.parse(combined)

        assertEquals(6, slides.size)
        assertEquals(
            listOf(
                "Ultra-Scale Distributed Systems",
                "The Monolithic Bottleneck",
                "Distributed Pipeline Architecture",
                "Reactive Slide Synchronization",
                "Multi-File Project Scaling Matrix",
                "Key Architecture Takeaways"
            ),
            slides.map { it.title }
        )
        assertEquals(SlideLayoutType.DIAGRAM, slides[2].layoutType, "mermaid slide must classify as DIAGRAM")
    }

    // ---------------------------------------------------------------------
    // Slide chunk operations — the COR-1 / COR-2 blast radius
    // ---------------------------------------------------------------------

    @Test
    fun `horizontal-rule split deck supports delete and move`() {
        val deck = """
            ## Alpha
            content a

            ---

            ## Beta
            content b

            ---

            ## Gamma
            content c
        """.trimIndent()

        val state = PresentationState()
        state.updateMarkdown(deck)
        assertEquals(listOf("Alpha", "Beta", "Gamma"), state.slides.map { it.title })

        state.deleteSlide(1)
        assertEquals(listOf("Alpha", "Gamma"), state.slides.map { it.title }, "deleting the middle slide")

        val moveState = PresentationState()
        moveState.updateMarkdown(deck)
        moveState.moveSlide(0, 2)
        assertEquals(listOf("Beta", "Gamma", "Alpha"), moveState.slides.map { it.title }, "moving first slide to last")
    }

    /**
     * COR-1 — FIXED. Was pinned as a known bug: the parser split on `##` headings while
     * the editor's private splitter recognised only a literal `---`, so a heading-only
     * deck was three slides but one chunk and deleteSlide() silently did nothing.
     * Boundaries now come from Slide.sourceLineRange, so both agree.
     */
    @Test
    fun `COR-1 heading-split deck can be edited by index`() {
        val deck = """
            ## Alpha
            content a
            ## Beta
            content b
            ## Gamma
            content c
        """.trimIndent()

        val state = PresentationState()
        state.updateMarkdown(deck)
        assertEquals(listOf("Alpha", "Beta", "Gamma"), state.slides.map { it.title }, "parser sees three slides")

        state.deleteSlide(1)
        assertEquals(
            listOf("Alpha", "Gamma"),
            state.slides.map { it.title },
            "COR-1: deleting the middle slide of a heading-split deck must remove exactly it"
        )
        assertTrue(state.markdownText.contains("content a"), "surviving slide content preserved")
        assertFalse(state.markdownText.contains("content b"), "deleted slide content removed")
    }

    /**
     * COR-1 — FIXED. HR_REGEX accepts `-{3,}` so the parser split on `----`, but the old
     * chunker matched only an exact `---`.
     */
    @Test
    fun `COR-1 four-dash rule is recognised by both parser and editor`() {
        val deck = "## A\ntext a\n\n----\n\n## B\ntext b\n"

        val slides = MarkdownSlideParser.parse(deck)
        assertEquals(listOf("A", "B"), slides.map { it.title }, "parser splits on four dashes")

        val state = PresentationState()
        state.updateMarkdown(deck)
        state.deleteSlide(0)
        assertEquals(
            listOf("B"),
            state.slides.map { it.title },
            "COR-1: editor must honour the same `----` split the parser used"
        )
    }

    // ---------------------------------------------------------------------
    // Parser edge cases slated for change
    // ---------------------------------------------------------------------

    /**
     * COR-4 — FIXED. A paragraph opening with a bare number is prose, not a KPI. The
     * metric regex now requires a unit (`%`, `x`, currency, or a magnitude suffix).
     */
    @Test
    fun `COR-4 numeric paragraph stays prose`() {
        val slides = MarkdownSlideParser.parse("## Roadmap\n\n2024 Roadmap Overview\n")

        assertFalse(
            slides[0].elements.any { it is com.skaldoria.core.models.SlideElement.Metric },
            "COR-4: a bare year must not become a Metric"
        )
        assertTrue(
            slides[0].elements.any { it is com.skaldoria.core.models.SlideElement.Text },
            "COR-4: it should be ordinary text"
        )
    }

    /** COR-4 — genuine metrics must still be recognised. */
    @Test
    fun `COR-4 real metrics are still detected`() {
        val cases = listOf(
            "99.9% Uptime Guarantee",
            "+140% Revenue Growth",
            "3x Faster Rendering",
            "\$42M Annual Recurring Revenue"
        )

        for (line in cases) {
            val slides = MarkdownSlideParser.parse("<!-- layout: metric -->\n$line\n")
            assertTrue(
                slides[0].elements.any { it is com.skaldoria.core.models.SlideElement.Metric },
                "COR-4: '$line' should still parse as a Metric"
            )
        }
    }

    // ---------------------------------------------------------------------
    // Export — structural golden test
    // ---------------------------------------------------------------------

    @Test
    fun `printable html export emits one section per slide`() {
        val state = PresentationState()
        state.updateMarkdown(PresentationState.DEFAULT_SAMPLE_MARKDOWN)

        val html = DeckExporter.generatePrintableHtml(state, autoTriggerPrint = false)

        assertEquals(9, Regex("<div class=\"slide\">").findAll(html).count(), "one .slide block per slide")
        assertTrue(html.contains("Next-Gen Multiplatform Systems"), "first slide title present")
        assertTrue(html.contains("Performance Comparison"), "table slide title present")
        assertTrue(html.contains("<table>"), "table element rendered")
        assertTrue(html.contains("class='mermaid'"), "mermaid block rendered")
        assertTrue(html.contains("@page { size: 16in 9in; margin: 0; }"), "print stylesheet present")

        // Code content must be HTML-escaped, not injected raw.
        assertTrue(html.contains("&quot;") || html.contains("&lt;"), "code content is escaped")
    }
}
