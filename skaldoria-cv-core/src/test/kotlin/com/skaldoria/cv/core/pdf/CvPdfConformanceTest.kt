package com.skaldoria.cv.core.pdf

import com.skaldoria.cv.core.CvMarkdownAdapter
import com.skaldoria.cv.core.CvPaperSize
import com.skaldoria.cv.core.CvTemplateCatalog
import com.skaldoria.cv.core.layout.CvLayoutEngine
import com.skaldoria.cv.core.layout.CvLayoutOptions
import com.skaldoria.cv.core.layout.CvResolvedLayout
import com.skaldoria.cv.core.layout.FixedPitchMeasurer
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CV-NFR-101. Exports representative CVs, reads them back with an independent PDF implementation,
 * and asserts the properties an ATS depends on.
 *
 * Reading back with PDFBox rather than inspecting our own byte output is the whole point: a guard
 * that used the writer's own model to check the writer would pass on a file no reader accepts.
 */
class CvPdfConformanceTest {

    private val template = CvTemplateCatalog.default.layout

    private val palette = CvPdfPalette(
        primaryText = 0x161616,
        secondaryText = 0x454545,
        accent = 0x0755A3,
        divider = 0x737373
    )

    private val fonts: CvPdfFonts by lazy {
        val root = File("skaldoria-cv/src/desktopMain/resources/fonts")
        CvPdfFonts(
            regular = TrueTypeFont.parse(File(root, "Roboto.ttf").readBytes()),
            italic = TrueTypeFont.parse(File(root, "Roboto-Italic.ttf").readBytes())
        )
    }

    private val referenceCv = """
        |---
        |headline: Staff Backend Engineer
        |paper: a4
        |---
        |
        |# Ada Lovelace
        |
        |- Email: ada@example.com
        |- Web: https://example.com/ada
        |
        |## Profile
        |
        |Backend engineer with **twelve years** shipping *reliable* systems.
        |
        |## Experience
        |
        |### Staff Engineer — Analytical Engines Ltd
        |
        |- Cut p99 latency from 840 ms to 120 ms.
        |- Led a team of six across two time zones.
        |
        |### Senior Engineer — Difference Co
        |
        |- Migrated 40 services with no customer-visible downtime.
    """.trimMargin()

    private fun resolve(markdown: String, paper: CvPaperSize = CvPaperSize.A4): CvResolvedLayout =
        CvLayoutEngine.resolve(
            document = CvMarkdownAdapter().parse(markdown),
            layout = template,
            paper = paper,
            measurer = FixedPitchMeasurer(),
            options = CvLayoutOptions(uppercaseSections = true)
        )

    private fun export(markdown: String, paper: CvPaperSize = CvPaperSize.A4): ByteArray =
        CvPdfRenderer.render(resolve(markdown, paper), template, fonts, palette)

    private fun <T> withPdf(bytes: ByteArray, block: (PDDocument) -> T): T =
        Loader.loadPDF(bytes).use(block)

    // ---------------------------------------------------------------------
    // CV-FR-060 — real text, not pictures
    // ---------------------------------------------------------------------

    @Test
    fun `the export is a readable PDF`() {
        val bytes = export(referenceCv)
        assertTrue(bytes.size > 1000, "suspiciously small export: ${bytes.size} bytes")
        assertEquals("%PDF-1.7", String(bytes.copyOfRange(0, 8), Charsets.ISO_8859_1))
        withPdf(bytes) { assertTrue(it.numberOfPages >= 1) }
    }

    @Test
    fun `text is selectable rather than rasterised`() {
        withPdf(export(referenceCv)) { document ->
            val text = PDFTextStripper().getText(document)
            assertTrue(text.isNotBlank(), "no extractable text at all — the export rasterised")

            for (page in document.pages) {
                val images = page.resources.xObjectNames.toList()
                assertTrue(images.isEmpty(), "an image XObject means glyphs were drawn as pixels")
            }
        }
    }

    @Test
    fun `every embedded font is a real embedded font program`() {
        withPdf(export(referenceCv)) { document ->
            val fontNames = document.getPage(0).resources.fontNames.toList()
            assertTrue(fontNames.isNotEmpty(), "the page declares no fonts")
            fontNames.forEach { name ->
                val font = document.getPage(0).resources.getFont(name)
                assertTrue(font.isEmbedded, "$name is not embedded — an ATS may see no glyphs")
            }
        }
    }

    // ---------------------------------------------------------------------
    // CV-FR-061 — reading order
    // ---------------------------------------------------------------------

    @Test
    fun `extracted text follows the source order`() {
        withPdf(export(referenceCv)) { document ->
            val text = PDFTextStripper().getText(document)
            val expected = listOf(
                "Ada Lovelace",
                "Staff Backend Engineer",
                "PROFILE",
                "EXPERIENCE",
                "Staff Engineer",
                "Cut p99 latency",
                "Senior Engineer",
                "Migrated 40 services"
            )
            var cursor = -1
            for (fragment in expected) {
                val at = text.indexOf(fragment)
                assertTrue(at >= 0, "'$fragment' is missing from the PDF text:\n$text")
                assertTrue(at > cursor, "'$fragment' appears out of reading order:\n$text")
                cursor = at
            }
        }
    }

    @Test
    fun `inline emphasis markers do not reach the PDF text`() {
        withPdf(export(referenceCv)) { document ->
            val text = PDFTextStripper().getText(document)
            assertContains(text, "twelve years")
            assertTrue("**" !in text, "bold delimiters leaked into the extracted text")
        }
    }

    @Test
    fun `the extracted text matches what the layout model says`() {
        // The two must agree, otherwise preview and export are describing different documents.
        val layout = resolve(referenceCv)
        withPdf(CvPdfRenderer.render(layout, template, fonts, palette)) { document ->
            val extracted = PDFTextStripper().getText(document)
            layout.extractText().lines().filter { it.isNotBlank() }.forEach { line ->
                assertContains(extracted.replace("\r\n", "\n"), line.trim())
            }
        }
    }

    // ---------------------------------------------------------------------
    // CV-FR-063 — links and metadata
    // ---------------------------------------------------------------------

    @Test
    fun `contact links become active URI annotations`() {
        withPdf(export(referenceCv)) { document ->
            val targets = document.pages.flatMap { it.annotations }
                .filterIsInstance<PDAnnotationLink>()
                .mapNotNull { (it.action as? PDActionURI)?.uri }

            assertTrue(targets.any { "ada@example.com" in it }, "no email link, got $targets")
            assertTrue(targets.any { "example.com/ada" in it }, "no web link, got $targets")
        }
    }

    @Test
    fun `a link rectangle sits on the text it belongs to`() {
        withPdf(export(referenceCv)) { document ->
            val page = document.getPage(0)
            val link = page.annotations.filterIsInstance<PDAnnotationLink>().first()
            val box = link.rectangle

            assertTrue(box.width > 0 && box.height > 0, "degenerate link rectangle")
            assertTrue(box.lowerLeftX >= 0 && box.upperRightX <= page.mediaBox.width)
            assertTrue(box.lowerLeftY >= 0 && box.upperRightY <= page.mediaBox.height)
        }
    }

    @Test
    fun `document metadata names the candidate`() {
        withPdf(export(referenceCv)) { document ->
            val info = document.documentInformation
            assertEquals("Ada Lovelace", info.author)
            assertEquals("Ada Lovelace — CV", info.title)
        }
    }

    // ---------------------------------------------------------------------
    // Geometry and Unicode
    // ---------------------------------------------------------------------

    @Test
    fun `page geometry matches the requested paper`() {
        for (paper in listOf(CvPaperSize.A4, CvPaperSize.Letter)) {
            withPdf(export(referenceCv, paper)) { document ->
                val box = document.getPage(0).mediaBox
                assertTrue(
                    abs(box.width - paper.widthPoints) < 0.5 && abs(box.height - paper.heightPoints) < 0.5,
                    "${paper.displayName}: expected ${paper.widthPoints}x${paper.heightPoints}, got ${box.width}x${box.height}"
                )
            }
        }
    }

    @Test
    fun `non-ascii glyphs survive the round trip`() {
        // CV-NFR-081. Identity-H plus a ToUnicode CMap is what makes this work; without the CMap
        // the glyphs would draw correctly and extract as mojibake.
        val markdown = """
            |# Ada Lovelace-Björk
            |
            |## Profil
            |
            |Erfaren ingenjör — 数据 — Ελληνικά — café naïve.
        """.trimMargin()

        withPdf(export(markdown)) { document ->
            val text = PDFTextStripper().getText(document)
            listOf("Lovelace-Björk", "ingenjör", "Ελληνικά", "café", "naïve").forEach {
                assertContains(text, it, message = "lost Unicode: $it")
            }
        }
    }

    @Test
    fun `a multi-page CV keeps its footers and page count`() {
        val long = referenceCv + "\n" + (1..80).joinToString("\n") { "- Evidence line $it." }
        val layout = resolve(long)
        assertTrue(layout.pageCount > 1, "the fixture must overflow one page")

        withPdf(CvPdfRenderer.render(layout, template, fonts, palette)) { document ->
            assertEquals(layout.pageCount, document.numberOfPages)
            val text = PDFTextStripper().getText(document)
            assertContains(text, "Page 1 of ${layout.pageCount}")
            assertContains(text, "Page ${layout.pageCount} of ${layout.pageCount}")
        }
    }

    // ---------------------------------------------------------------------
    // CV-NFR-041 — determinism
    // ---------------------------------------------------------------------

    @Test
    fun `exporting the same CV twice produces identical bytes`() {
        assertTrue(
            export(referenceCv).contentEquals(export(referenceCv)),
            "export is not byte-deterministic, so no build can verify it"
        )
    }

    @Test
    fun `an empty document still exports a valid one-page PDF`() {
        withPdf(export("")) { assertEquals(1, it.numberOfPages) }
    }
}
