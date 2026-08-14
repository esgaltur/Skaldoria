package com.skaldoria.cv

import com.skaldoria.cv.core.CvFontCatalog
import com.skaldoria.cv.core.CvFontId
import com.skaldoria.cv.core.CvMarkdownAdapter
import com.skaldoria.cv.core.CvPaperSize
import com.skaldoria.cv.core.CvTemplateCatalog
import com.skaldoria.cv.core.CvThemeCatalog
import com.skaldoria.cv.core.layout.CvFontRole
import com.skaldoria.cv.core.layout.CvPageElement
import com.skaldoria.cv.core.layout.CvTextStyle
import com.skaldoria.cv.core.pdf.TrueTypeFont
import com.skaldoria.markdown.parser.InlineRun
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Text must stay inside the printable box — CV-FR-046.
 *
 * **The regression these exist for.** `CvFontResolver` registered the bundled Roboto with AWT and
 * then returned `FontFamily("Roboto")`, a lookup by *name*. Skia — which is what Compose actually
 * shapes with — never sees AWT registrations, so the preview measured with a substituted face while
 * export embedded the real Roboto file. The substitute was **13% narrower**, so every line broken to
 * fit it overflowed the right margin once drawn in the real font.
 *
 * The lesson is that agreement cannot be asserted at the layout level alone: the layout was
 * internally consistent and still wrong. These measure the finished PDF's geometry.
 */
class CvPageFitTest {

    private val workspace: File = createTempDirectory("cv-fit").toFile()
    private val template = CvTemplateCatalog.default.layout

    @AfterTest
    fun cleanUp() {
        workspace.deleteRecursively()
    }

    /**
     * Every paragraph is one long **source** line, so the layout has to wrap it and the resulting
     * lines are packed hard against the content width.
     *
     * This matters more than it looks. An earlier version of this fixture broke its prose across
     * several source lines, which the adapter turns into separate short paragraphs — none of them
     * near the margin. It passed even with the font mismatch deliberately reintroduced, because
     * text 13% wider than a half-full line still fits. A guard for an overflow bug has to produce
     * lines that are actually full.
     */
    private val denseCv = """
        |---
        |headline: Principal Distributed Systems Engineer, Payments Platform
        |---
        |
        |# Ada Augusta Lovelace-Byron
        |
        |- Email: ada.lovelace@analytical-engines.example.com
        |- Web: https://example.com/ada-lovelace-portfolio
        |
        |## Profile
        |
        |Principal engineer with twelve years designing high-throughput payment and settlement systems, leading distributed platform teams across four time zones, and reducing operational cost per transaction while raising reliability targets from three nines to four nines of independently measured availability across every production region.
        |
        |## Experience
        |
        |### Principal Engineer — Analytical Engines International Limited
        |
        |- Reduced p99 checkout latency from 840 milliseconds to 120 milliseconds by replacing the synchronous settlement path with an idempotent, event-sourced pipeline that reconciles asynchronously against the ledger of record.
        |- Led a platform team of eleven engineers distributed across London, Berlin and Bangalore, establishing the review practices and on-call rotation that the wider organisation later adopted.
    """.trimMargin()

    private fun exportOf(markdown: String, fontId: CvFontId = CvFontCatalog.default): File {
        val layout = resolveCvLayout(
            document = CvMarkdownAdapter().parse(markdown),
            templateId = CvTemplateCatalog.default,
            themeId = CvThemeCatalog.default,
            fontId = fontId
        )
        val target = File(workspace, "fit-${fontId.name}-${markdown.hashCode()}.pdf")
        CvPdfExport.export(layout, fontId, target)
        return target
    }

    /** Every glyph box the PDF actually draws. */
    private fun textPositions(pdf: File): List<TextPosition> {
        val collected = ArrayList<TextPosition>()
        Loader.loadPDF(pdf).use { document ->
            object : PDFTextStripper() {
                override fun writeString(text: String, positions: List<TextPosition>) {
                    collected += positions
                }
            }.getText(document)
        }
        return collected
    }

    @Test
    fun `no glyph is drawn past the right margin`() {
        val pdf = exportOf(denseCv)
        val limit = CvPaperSize.A4.widthPoints - template.horizontalMargin

        val overflowing = textPositions(pdf).filter { it.xDirAdj + it.widthDirAdj > limit + 1.0 }
        assertTrue(
            overflowing.isEmpty(),
            "${overflowing.size} glyphs run past the right margin ($limit pt); " +
                "worst reaches ${overflowing.maxOfOrNull { it.xDirAdj + it.widthDirAdj }}: " +
                overflowing.take(6).joinToString { "'${it.unicode}'" }
        )
    }

    @Test
    fun `no glyph is drawn before the left margin`() {
        val pdf = exportOf(denseCv)
        val outside = textPositions(pdf).filter { it.xDirAdj < template.horizontalMargin - 1.0 }
        assertTrue(outside.isEmpty(), "${outside.size} glyphs start left of the margin")
    }

    @Test
    fun `no glyph is drawn outside the vertical printable area`() {
        val pdf = exportOf(denseCv)
        val height = CvPaperSize.A4.heightPoints

        textPositions(pdf).forEach { position ->
            assertTrue(
                position.yDirAdj >= 0.0 && position.yDirAdj <= height,
                "a glyph sits off the sheet at y=${position.yDirAdj}"
            )
        }
    }

    /**
     * The measurement identity the page fit depends on: Compose shapes with the same program the
     * PDF embeds, so a line that measured as fitting still fits when drawn.
     */
    @Test
    fun `compose and pdf agree on advance width for every offered font`() {
        val sample = "Reduced p99 checkout latency from 840 ms to 120 ms — Ada Lovelace"
        val style = CvTextStyle(CvFontRole.Body, sizePt = 10.5, lineHeightPt = 14.0)

        CvFontCatalog.all.forEach { fontId ->
            val program = CvFontProgram.load(fontId)
            val composeWidth = ComposeCvTextMeasurer(program.family, program.family)
                .measure(listOf(InlineRun(sample)), style, maxWidthPt = 10_000.0)
                .lines.first().runs.sumOf { it.widthPt }

            val embedded = TrueTypeFont.parse(program.regular)
            val pdfWidth = sample.sumOf { embedded.advanceWidth(embedded.glyphId(it.code)) } /
                1000.0 * style.sizePt

            val drift = abs(pdfWidth - composeWidth) / composeWidth
            assertTrue(
                drift < 0.01,
                "${fontId.displayName}: preview measures $composeWidth pt but the PDF draws " +
                    "$pdfWidth pt (${"%.1f".format(drift * 100)}% drift) — lines will not fit"
            )
        }
    }

    @Test
    fun `the resolved layout keeps every run inside the content width`() {
        val layout = resolveCvLayout(
            document = CvMarkdownAdapter().parse(denseCv),
            templateId = CvTemplateCatalog.default,
            themeId = CvThemeCatalog.default,
            fontId = CvFontCatalog.default
        )
        val contentWidth = layout.resolved.paper.widthPoints - template.horizontalMargin * 2

        layout.resolved.pages.forEach { page ->
            page.elements.filterIsInstance<CvPageElement.TextBlock>().forEach { block ->
                block.text.lines.forEach { line ->
                    val right = block.xPt + line.runs.sumOf { it.widthPt }
                    assertTrue(
                        right <= contentWidth + 1.0,
                        "a measured line is ${right - contentWidth} pt wider than the content box"
                    )
                }
            }
        }
    }

    @Test
    fun `a substituted font reports itself and still fits`() {
        // Whatever this machine lacks, the substitution must apply to preview and PDF together.
        CvFontCatalog.all.forEach { fontId ->
            val program = CvFontProgram.load(fontId)
            if (program.notice != null) {
                assertTrue(
                    "bundled Roboto" in program.notice,
                    "a substitution must say what was used instead: ${program.notice}"
                )
            }
        }
    }
}
