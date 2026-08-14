package com.skaldoria.cv.core.layout

import com.skaldoria.cv.core.CvMarkdownAdapter
import com.skaldoria.cv.core.CvPaperSize
import com.skaldoria.cv.core.CvTemplateCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CvLayoutEngineTest {

    private val layout = CvTemplateCatalog.default.layout
    private val measurer = FixedPitchMeasurer()

    private fun resolve(
        markdown: String,
        paper: CvPaperSize = CvPaperSize.A4,
        options: CvLayoutOptions = CvLayoutOptions()
    ) = CvLayoutEngine.resolve(
        document = CvMarkdownAdapter().parse(markdown),
        layout = layout,
        paper = paper,
        measurer = measurer,
        options = options
    )

    /** The candidate name comes from the first H1, the headline from front matter. */
    private fun cv(body: String) = """
        |---
        |headline: Analytical Engine Programmer
        |---
        |
        |# Ada Lovelace
        |
        |$body
    """.trimMargin()

    // ---------------------------------------------------------------------
    // Geometry
    // ---------------------------------------------------------------------

    @Test
    fun `content never leaves the printable box`() {
        val resolved = resolve(cv((1..60).joinToString("\n") { "## Section $it\n\n- point $it\n" }))
        val contentHeight = CvPaperSize.A4.heightPoints - layout.topMargin - layout.bottomReserved
        val contentWidth = CvPaperSize.A4.widthPoints - layout.horizontalMargin * 2

        assertTrue(resolved.pageCount > 1, "the fixture must actually overflow")
        for (page in resolved.pages) {
            for (element in page.elements) {
                assertTrue(element.yPt >= -0.001, "element above the content box on page ${page.pageNumber}")
                val bottom = when (element) {
                    is CvPageElement.TextBlock -> element.yPt + element.text.heightPt
                    is CvPageElement.Rule -> element.yPt + element.thicknessPt
                }
                assertTrue(
                    bottom <= contentHeight + 0.001,
                    "element overflows page ${page.pageNumber}: $bottom > $contentHeight"
                )
                if (element is CvPageElement.Rule) {
                    assertTrue(element.xPt + element.widthPt <= contentWidth + 0.001)
                }
            }
        }
    }

    @Test
    fun `paper size changes pagination`() {
        val source = cv((1..40).joinToString("\n") { "- Achievement number $it with supporting evidence." })
        val a4 = resolve(source, CvPaperSize.A4)
        val letter = resolve(source, CvPaperSize.Letter)

        assertEquals(a4.extractText(), letter.extractText(), "same content, different sheet")
        assertTrue(
            letter.pages.first().elements.size <= a4.pages.first().elements.size,
            "Letter is shorter than A4, so it fits no more on the first page"
        )
    }

    // ---------------------------------------------------------------------
    // Reading order and content — CV-FR-061
    // ---------------------------------------------------------------------

    @Test
    fun `reading order follows the source`() {
        val resolved = resolve(cv("## Experience\n\n### Staff Engineer\n\n- Led the migration.\n"))
        val text = resolved.extractText()

        val order = listOf("Ada Lovelace", "Experience", "Staff Engineer", "Led the migration.")
        var cursor = -1
        for (fragment in order) {
            val at = text.indexOf(fragment)
            assertTrue(at > cursor, "'$fragment' is out of reading order in:\n$text")
            cursor = at
        }
    }

    @Test
    fun `inline markdown is rendered without its delimiters`() {
        val text = resolve(cv("## Profile\n\nShipped **fast** and *safely*.\n")).extractText()
        assertTrue("Shipped fast and safely." in text, "got: $text")
        assertTrue("**" !in text && "*safely*" !in text)
    }

    @Test
    fun `contact links survive into the layout`() {
        val resolved = resolve(
            """
            |# Ada Lovelace
            |
            |- Email: ada@example.com
            |
            |## Profile
            |
            |Text.
            """.trimMargin()
        )
        val links = resolved.pages.flatMap { it.elements }
            .filterIsInstance<CvPageElement.TextBlock>()
            .flatMap { block -> block.text.lines.flatMap { it.runs } }
            .mapNotNull { it.link }

        assertTrue(links.any { "ada@example.com" in it }, "contact target must reach the renderer, got $links")
    }

    // ---------------------------------------------------------------------
    // Widows and orphans — CV-FR-042 and CV-FR-043
    // ---------------------------------------------------------------------

    @Test
    fun `a section heading never ends a page alone`() {
        // Fill most of a page, then start a section right at the boundary.
        val filler = (1..34).joinToString("\n") { "- Evidence line $it that occupies vertical space." }
        val resolved = resolve(cv("## History\n\n$filler\n\n## Skills\n\n- Kotlin\n"))

        for (page in resolved.pages) {
            val blocks = page.elements.filterIsInstance<CvPageElement.TextBlock>()
            val last = blocks.lastOrNull()?.text?.plainText?.trim()
            assertTrue(
                last != "Skills" && last != "History",
                "page ${page.pageNumber} ends on a bare section heading"
            )
        }
    }

    @Test
    fun `an entry heading never ends a page alone`() {
        val filler = (1..32).joinToString("\n") { "- Evidence line $it that occupies vertical space." }
        val resolved = resolve(cv("## Experience\n\n$filler\n\n### Principal Engineer\n\n- Did the work.\n"))

        for (page in resolved.pages) {
            val last = page.elements.filterIsInstance<CvPageElement.TextBlock>()
                .lastOrNull()?.text?.plainText?.trim()
            assertTrue(last != "Principal Engineer", "page ${page.pageNumber} strands an entry heading")
        }
    }

    @Test
    fun `a section rule stays with its heading`() {
        val resolved = resolve(cv((1..30).joinToString("\n\n") { "## Section $it\n\n- point" }))
        for (page in resolved.pages) {
            val ruleIndices = page.elements.withIndex()
                .filter { it.value is CvPageElement.Rule }
                .map { it.index }
            for (index in ruleIndices) {
                assertTrue(index > 0, "page ${page.pageNumber} opens with an orphaned section rule")
            }
        }
    }

    // ---------------------------------------------------------------------
    // Determinism — CV-NFR-041
    // ---------------------------------------------------------------------

    @Test
    fun `identical input resolves to an identical layout`() {
        val source = cv("## Experience\n\n### Staff Engineer\n\n- Led the migration.\n")
        assertEquals(resolve(source), resolve(source), "layout must be a pure function of its inputs")
    }

    @Test
    fun `uppercase sections is a theme choice the engine honours`() {
        val source = cv("## Experience\n\n- Work.\n")
        assertTrue("EXPERIENCE" in resolve(source, options = CvLayoutOptions(uppercaseSections = true)).extractText())
        assertTrue("Experience" in resolve(source).extractText())
    }

    // ---------------------------------------------------------------------
    // Footers and metadata
    // ---------------------------------------------------------------------

    @Test
    fun `every page carries a numbered footer`() {
        val resolved = resolve(cv((1..60).joinToString("\n") { "- Line $it" }))
        assertTrue(resolved.pageCount > 1)
        resolved.pages.forEach { page ->
            val footer = assertNotNull(page.footer, "page ${page.pageNumber} has no footer")
            assertEquals("Page ${page.pageNumber} of ${resolved.pageCount}", footer.text.plainText)
        }
    }

    @Test
    fun `footers can be turned off`() {
        val resolved = resolve(cv("## Profile\n\nText.\n"), options = CvLayoutOptions(showPageFooter = false))
        assertNull(resolved.pages.single().footer)
    }

    @Test
    fun `document metadata comes from the candidate name`() {
        val resolved = resolve(cv("## Profile\n\nText.\n"))
        assertEquals("Ada Lovelace", resolved.author)
        assertEquals("Ada Lovelace — CV", resolved.title)
    }

    @Test
    fun `a nameless CV still lays out and says so`() {
        val resolved = resolve("## Profile\n\nText.\n")
        assertNull(resolved.author)
        assertTrue("Candidate name required" in resolved.extractText())
        assertTrue(
            resolved.pages.first().elements.filterIsInstance<CvPageElement.TextBlock>()
                .first().style.color == CvColorRole.Missing
        )
    }

    @Test
    fun `an empty document produces one page`() {
        assertEquals(1, resolve("").pageCount)
    }

    /**
     * The bullet and its text are separate blocks, so wrapped lines hang under the text rather than
     * under the marker. The gap between them has to be explicit — a measurer reports width to the
     * last visible glyph, so a trailing space in the marker string measures as nothing and the text
     * sits flush against the bullet.
     */
    @Test
    fun `list text is inset past its marker by a visible gap`() {
        val resolved = resolve(cv("## Skills\n\n- Kotlin and Compose Multiplatform\n"))
        val blocks = resolved.pages.first().elements.filterIsInstance<CvPageElement.TextBlock>()

        val marker = blocks.single { it.text.plainText == "•" }
        val body = blocks.single { "Kotlin" in it.text.plainText }
        val markerWidth = marker.text.lines.first().runs.sumOf { it.widthPt }

        assertEquals(0.0, marker.xPt, "the marker sits at the left edge of the content box")
        assertTrue(
            body.xPt >= markerWidth + layout.listMarkerGap - 0.001,
            "list text starts at ${body.xPt} but the marker ends at $markerWidth"
        )
    }
}
