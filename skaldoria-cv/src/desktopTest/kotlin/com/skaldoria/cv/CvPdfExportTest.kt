package com.skaldoria.cv

import com.skaldoria.cv.core.CvFontCatalog
import com.skaldoria.cv.core.CvMarkdownAdapter
import com.skaldoria.cv.core.CvTemplateCatalog
import com.skaldoria.cv.core.CvThemeCatalog
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The export path end to end, through the **real** Compose text measurer.
 *
 * `CvPdfConformanceTest` in `:skaldoria-cv-core` proves the PDF is well formed against a
 * deterministic fake. This proves the thing the user actually runs: real shaping, real bundled
 * fonts, real file writing — and that the pages on screen are the pages in the file.
 */
class CvPdfExportTest {

    private val workspace: File = createTempDirectory("cv-export").toFile()

    @AfterTest
    fun cleanUp() {
        workspace.deleteRecursively()
    }

    private fun layoutOf(markdown: String): CvPreviewLayout {
        val document = CvMarkdownAdapter().parse(markdown)
        return resolveCvLayout(
            document = document,
            templateId = CvTemplateCatalog.default,
            themeId = CvThemeCatalog.default,
            fontId = CvFontCatalog.default
        )
    }

    private fun exportExample(name: String = "cv.pdf"): Pair<CvExportResult, CvPreviewLayout> {
        val markdown = CvExamples.softwareEngineer()
        val layout = layoutOf(markdown)
        val result = CvPdfExport.export(layout, CvFontCatalog.default, File(workspace, name))
        return result to layout
    }

    @Test
    fun `the bundled example exports as extractable text`() {
        val (result, _) = exportExample()

        assertTrue(result.file.exists(), "no PDF was written")
        assertTrue(result.file.length() > 1000, "PDF is implausibly small: ${result.file.length()}")

        Loader.loadPDF(result.file).use { document ->
            val text = PDFTextStripper().getText(document)
            assertTrue(text.isNotBlank(), "the exported CV has no extractable text")
            assertTrue(
                text.count { it.isLetter() } > 200,
                "an ATS would find almost no words in this CV"
            )
        }
    }

    @Test
    fun `the exported page count is the page count the preview showed`() {
        val (result, layout) = exportExample()
        Loader.loadPDF(result.file).use { document ->
            assertEquals(
                layout.resolved.pageCount,
                document.numberOfPages,
                "preview and export disagree about pagination — the shared layout is not shared"
            )
            assertEquals(layout.resolved.pageCount, result.pageCount)
        }
    }

    @Test
    fun `every line the layout resolved appears in the exported text`() {
        val (result, layout) = exportExample()
        Loader.loadPDF(result.file).use { document ->
            val extracted = PDFTextStripper().getText(document).replace("\r\n", "\n")
            layout.resolved.extractText()
                .lines()
                .map { it.trim() }
                .filter { it.length > 12 }
                .forEach { assertContains(extracted, it) }
        }
    }

    @Test
    fun `the bundled font needs no substitution notice`() {
        val (result, _) = exportExample()
        assertNull(result.fontNotice, "Roboto is bundled, so nothing should have been substituted")
    }

    // ---------------------------------------------------------------------
    // CV-FR-064 / CV-NFR-040 — atomic export
    // ---------------------------------------------------------------------

    @Test
    fun `exporting over an existing file replaces it completely`() {
        val target = File(workspace, "existing.pdf")
        target.writeText("this is not a PDF, and is longer than the real one would be at zero pages")

        val layout = layoutOf(CvExamples.softwareEngineer())
        CvPdfExport.export(layout, CvFontCatalog.default, target)

        val head = target.readBytes().copyOfRange(0, 8).toString(Charsets.ISO_8859_1)
        assertEquals("%PDF-1.7", head, "the previous contents were not fully replaced")
    }

    @Test
    fun `no partial sibling is left behind`() {
        exportExample()
        val leftovers = workspace.listFiles()?.filter { it.name.contains(".part") }.orEmpty()
        assertTrue(leftovers.isEmpty(), "temporary export files were left on disk: $leftovers")
    }

    @Test
    fun `a failed export leaves an existing file untouched`() {
        val target = File(workspace, "guarded.pdf")
        val (_, _) = exportExample("guarded.pdf")
        val before = target.readBytes()

        // A directory where the file should be is the simplest reproducible write failure.
        val blocked = File(workspace, "blocked")
        blocked.mkdirs()
        runCatching {
            CvPdfExport.export(layoutOf(CvExamples.softwareEngineer()), CvFontCatalog.default, blocked)
        }

        assertTrue(target.readBytes().contentEquals(before), "an unrelated export corrupted this file")
    }

    // ---------------------------------------------------------------------
    // CV-FR-066 — the source is never mutated
    // ---------------------------------------------------------------------

    @Test
    fun `exporting does not touch the markdown source`() {
        val markdown = CvExamples.softwareEngineer()
        val source = File(workspace, "cv.md").apply { writeText(markdown) }
        val before = source.readBytes()

        CvPdfExport.export(layoutOf(markdown), CvFontCatalog.default, File(workspace, "out.pdf"))

        assertTrue(source.readBytes().contentEquals(before), "export rewrote the Markdown")
    }

    // ---------------------------------------------------------------------
    // CV-NFR-041 — determinism through the real text stack
    // ---------------------------------------------------------------------

    @Test
    fun `two exports of the same CV are byte-identical`() {
        val markdown = CvExamples.softwareEngineer()
        val first = File(workspace, "first.pdf")
        val second = File(workspace, "second.pdf")

        CvPdfExport.export(layoutOf(markdown), CvFontCatalog.default, first)
        CvPdfExport.export(layoutOf(markdown), CvFontCatalog.default, second)

        assertTrue(
            first.readBytes().contentEquals(second.readBytes()),
            "the same CV exported differently twice"
        )
    }
}
