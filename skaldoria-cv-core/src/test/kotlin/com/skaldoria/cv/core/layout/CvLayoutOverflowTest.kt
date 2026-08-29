package com.skaldoria.cv.core.layout

import com.skaldoria.cv.core.CvMarkdownAdapter
import com.skaldoria.cv.core.CvPaperSize
import com.skaldoria.cv.core.CvTemplateCatalog
import com.skaldoria.cv.core.DiagnosticSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * CV-FR-046: content that cannot fit is reported, never silently dropped.
 *
 * The gap these guard: a flow item taller than the content box is the one shape pagination cannot
 * resolve. [com.skaldoria.cv.core.PagePacker] starts a fresh page for it, the item still does not
 * fit, and the remainder is drawn past the sheet — clipped in preview, off-page in the PDF. Before
 * this report the text just disappeared, with nothing anywhere saying it had.
 */
class CvLayoutOverflowTest {

    private val template = CvTemplateCatalog.default.layout
    private val measurer = FixedPitchMeasurer()

    private fun resolve(markdown: String, paper: CvPaperSize = CvPaperSize.A4) =
        CvLayoutEngine.resolve(
            document = CvMarkdownAdapter().parse(markdown),
            layout = template,
            paper = paper,
            measurer = measurer,
            options = CvLayoutOptions()
        )

    private fun contentHeight(paper: CvPaperSize) =
        paper.heightPoints - template.topMargin - template.bottomReserved

    /** One Markdown line, so the adapter produces exactly one unbreakable paragraph block. */
    private fun oversizedParagraph() = "evidence ".repeat(1200).trim()

    @Test
    fun `a paragraph taller than the page is reported against the page it lands on`() {
        val resolved = resolve(
            """
            |# Ada Lovelace
            |
            |## Profile
            |
            |${oversizedParagraph()}
            """.trimMargin()
        )

        assertEquals(1, resolved.overflows.size, "expected exactly one overflow: ${resolved.overflows}")
        val overflow = resolved.overflows.single()

        assertTrue("paragraph" in overflow.label, "unhelpful label: ${overflow.label}")
        assertEquals(5, overflow.source?.startLine, "must point at the offending Markdown line")
        assertEquals(contentHeight(CvPaperSize.A4), overflow.availablePt, 0.001)
        assertTrue(
            overflow.requiredPt > overflow.availablePt,
            "a reported overflow must actually exceed the box: ${overflow.requiredPt}"
        )
        assertTrue(overflow.excessPt > 0.0)
    }

    @Test
    fun `the report reaches the user as a blocking diagnostic`() {
        val overflow = resolve(
            """
            |# Ada Lovelace
            |
            |## Profile
            |
            |${oversizedParagraph()}
            """.trimMargin()
        ).overflows.single()

        val diagnostic = overflow.toDiagnostic()

        assertEquals("CV_CONTENT_OVERFLOW", diagnostic.code)
        // Error, not Warning: the exported PDF is missing text the author wrote.
        assertEquals(DiagnosticSeverity.Error, diagnostic.severity)
        assertEquals(5, diagnostic.source.startLine)
        assertTrue(diagnostic.action.isNotBlank(), "a diagnostic without an action is not actionable")
    }

    @Test
    fun `content that merely spills onto another page is not an overflow`() {
        // Sixty sections paginate across several sheets; every individual item still fits one.
        val resolved = resolve(
            "# Ada Lovelace\n\n" + (1..60).joinToString("\n") { "## Section $it\n\n- point $it\n" }
        )

        assertTrue(resolved.pageCount > 1, "the fixture must actually paginate")
        assertTrue(
            resolved.overflows.isEmpty(),
            "ordinary pagination reported as content loss: ${resolved.overflows}"
        )
    }

    @Test
    fun `a shorter sheet can overflow content that fitted A4`() {
        // Sized to fit A4's content box and exceed Letter's, which is 49.89 pt shorter. The fake
        // measurer wraps this at ten words per line, so 520 words is 52 lines: 733 pt of text,
        // between Letter's 704 pt box and A4's 753.89 pt one.
        val betweenTheTwo = "evidence ".repeat(520).trim()
        val markdown = "# Ada Lovelace\n\n## Profile\n\n$betweenTheTwo"

        val a4 = resolve(markdown, CvPaperSize.A4)
        val letter = resolve(markdown, CvPaperSize.Letter)

        assertTrue(a4.overflows.isEmpty(), "should still fit A4: ${a4.overflows}")
        assertEquals(1, letter.overflows.size, "should not fit the shorter Letter sheet")
        assertEquals(contentHeight(CvPaperSize.Letter), letter.overflows.single().availablePt, 0.001)
    }

    // ---------------------------------------------------------------------
    // Source anchoring — what outline navigation steers by (CV-FR-024)
    // ---------------------------------------------------------------------

    @Test
    fun `elements carry the line they were built from`() {
        val resolved = resolve(
            """
            |# Ada Lovelace
            |
            |## Experience
            |
            |### Staff Engineer
            |
            |- Led the migration.
            """.trimMargin()
        )

        assertEquals(1, resolved.pageContaining(3), "the Experience heading is on page one")
        assertEquals(1, resolved.pageContaining(5), "the Staff Engineer entry is on page one")
        assertEquals(1, resolved.pageContaining(7), "the bullet is on page one")
    }

    @Test
    fun `a section pushed onto a later page reports that page`() {
        val resolved = resolve(
            "# Ada Lovelace\n\n" + (1..60).joinToString("\n") { "## Section $it\n\n- point $it\n" }
        )
        assertTrue(resolved.pageCount > 1)

        // The last section's heading line, counting the title, its blank line, and four lines per
        // section block.
        val lastSectionLine = 3 + (59 * 4)
        val page = resolved.pageContaining(lastSectionLine)

        assertNotNull(page, "the final section must appear on some page")
        assertEquals(resolved.pageCount, page, "and it is the last one")
    }

    @Test
    fun `a line that produced nothing has no page`() {
        val resolved = resolve("# Ada Lovelace\n\n## Profile\n\nText.\n")
        assertEquals(null, resolved.pageContaining(2), "line two is blank")
        assertEquals(null, resolved.pageContaining(900), "line nine hundred does not exist")
    }
}
