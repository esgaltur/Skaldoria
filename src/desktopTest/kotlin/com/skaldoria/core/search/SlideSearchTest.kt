package com.skaldoria.core.search

import com.skaldoria.core.models.Slide
import com.skaldoria.core.models.SlideElement
import com.skaldoria.core.models.SlideLayoutType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F-05: slide search must cover **every** `SlideElement` variant.
 *
 * The filter lived inline in the `CommandPalette` composable and ended in `else -> false`
 * over a *sealed* hierarchy, so four element types were silently unsearchable: a speaker
 * could not jump to a slide by its poll option, diagram source, formula or image caption.
 *
 * The `else` is also the exact shape that caused EXP-3, where tables, images and polls
 * vanished from PNG export. Kotlin already gives the Visitor guarantee for a sealed
 * hierarchy — an exhaustive `when` is a *compile* error when a case is missing — but only
 * if nobody writes `else`. These tests pin the behaviour; the missing `else` keeps it.
 */
class SlideSearchTest {

    private fun slideWith(vararg elements: SlideElement) = Slide(
        index = 0,
        title = "Architecture",
        subtitle = "System overview",
        layoutType = SlideLayoutType.BULLET_LIST,
        elements = elements.toList(),
        notes = listOf("Remind the team about latency milestones")
    )

    private fun matches(query: String, vararg elements: SlideElement) =
        SlideSearch.matches(slideWith(*elements), query)

    // ---- the cases that already worked ----

    @Test
    fun `matches the title`() = assertTrue(matches("architect"))

    @Test
    fun `matches the subtitle`() = assertTrue(matches("overview"))

    @Test
    fun `matches speaker notes`() = assertTrue(matches("latency"))

    @Test
    fun `matches paragraph text`() =
        assertTrue(matches("throughput", SlideElement.Text("Sustained throughput")))

    @Test
    fun `matches bullet items`() =
        assertTrue(matches("resilient", SlideElement.BulletList(listOf("Resilient by default"))))

    @Test
    fun `matches code blocks`() =
        assertTrue(matches("suspend fun", SlideElement.CodeBlock("suspend fun render() {}")))

    @Test
    fun `matches quotes and their author`() {
        assertTrue(matches("prerequisite", SlideElement.Quote("Simplicity is prerequisite", "Dijkstra")))
        assertTrue(matches("dijkstra", SlideElement.Quote("Simplicity is prerequisite", "Dijkstra")))
    }

    @Test
    fun `matches metric value and label`() {
        assertTrue(matches("99.99", SlideElement.Metric("99.99%", "Uptime")))
        assertTrue(matches("uptime", SlideElement.Metric("99.99%", "Uptime")))
    }

    @Test
    fun `matches table headers and cells`() {
        val table = SlideElement.Table(listOf("Engine", "FPS"), listOf(listOf("Skaldoria", "120")))
        assertTrue(matches("engine", table))
        assertTrue(matches("skaldoria", table))
    }

    // ---- the four the `else` branch silently dropped ----

    @Test
    fun `matches poll question and options`() {
        val poll = SlideElement.Poll("Pick a datastore", listOf("PostgreSQL", "CockroachDB"))
        assertTrue(matches("datastore", poll), "poll question was unsearchable")
        assertTrue(matches("cockroach", poll), "poll options were unsearchable")
    }

    @Test
    fun `matches diagram source`() {
        val diagram = SlideElement.MermaidDiagram("graph TD\n  A[API Gateway] --> B[Worker]")
        assertTrue(matches("gateway", diagram), "diagram source was unsearchable")
        assertTrue(matches("flowchart", diagram), "diagram type was unsearchable")
    }

    @Test
    fun `matches math formulas`() {
        val math = SlideElement.MathFormula("\\Delta t = t_{elapsed}")
        assertTrue(matches("elapsed", math), "formulas were unsearchable")
    }

    @Test
    fun `matches image alt text and url`() {
        val image = SlideElement.Image(url = "assets/topology.png", altText = "Cluster topology")
        assertTrue(matches("cluster", image), "image alt text was unsearchable")
        assertTrue(matches("topology.png", image), "image path was unsearchable")
        assertTrue(
            matches("wiring", SlideElement.Image("a.png", "alt", caption = "Wiring detail")),
            "image caption was unsearchable"
        )
    }

    // ---- general behaviour ----

    @Test
    fun `search is case insensitive`() =
        assertTrue(matches("COCKROACH", SlideElement.Poll("q", listOf("CockroachDB"))))

    @Test
    fun `a non-matching query matches nothing`() =
        assertFalse(matches("kubernetes", SlideElement.Text("Sustained throughput")))

    @Test
    fun `a blank query returns every slide unfiltered`() {
        val slides = listOf(slideWith(), slideWith())
        assertEquals(slides, SlideSearch.filter(slides, "   "))
    }

    @Test
    fun `filter keeps only matching slides`() {
        val a = slideWith(SlideElement.Text("alpha"))
        val b = slideWith(SlideElement.Text("beta"))
        assertEquals(listOf(b), SlideSearch.filter(listOf(a, b), "beta"))
    }

    @Test
    fun `a surrounding-whitespace query is trimmed`() =
        assertTrue(matches("  architect  "))
}
