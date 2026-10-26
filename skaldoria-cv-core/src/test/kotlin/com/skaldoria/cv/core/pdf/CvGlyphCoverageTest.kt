package com.skaldoria.cv.core.pdf

import com.skaldoria.cv.core.CvMarkdownAdapter
import com.skaldoria.cv.core.CvPaperSize
import com.skaldoria.cv.core.CvTemplateCatalog
import com.skaldoria.cv.core.DiagnosticSeverity
import com.skaldoria.cv.core.layout.CvLayoutEngine
import com.skaldoria.cv.core.layout.CvLayoutOptions
import com.skaldoria.cv.core.layout.FixedPitchMeasurer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Checked against the bundled Roboto, the font every CV falls back to. */
class CvGlyphCoverageTest {

    private val fonts: CvPdfFonts by lazy {
        val root = File("skaldoria-cv/src/desktopMain/resources/fonts")
        CvPdfFonts(
            regular = TrueTypeFont.parse(File(root, "Roboto.ttf").readBytes()),
            italic = TrueTypeFont.parse(File(root, "Roboto-Italic.ttf").readBytes())
        )
    }

    private fun check(markdown: String) = CvGlyphCoverage.check(
        CvLayoutEngine.resolve(
            document = CvMarkdownAdapter().parse(markdown),
            layout = CvTemplateCatalog.default.layout,
            paper = CvPaperSize.A4,
            measurer = FixedPitchMeasurer(),
            options = CvLayoutOptions()
        ),
        fonts
    )

    @Test
    fun `characters the font has raise nothing`() {
        val cv = "# Ada Lovelace-Björk\n\n## Profil\n\nErfaren ingenjör — Ελληνικά — café naïve, *kursiv*.\n"

        assertEquals(emptyList(), check(cv))
    }

    @Test
    fun `characters the font lacks are named on the line that holds them`() {
        val diagnostics = check("# Ada\n\n## Profile\n\nBefore ☃ middle ♞ after ☃ again.\n")

        val warning = diagnostics.single()
        assertEquals(CvGlyphCoverage.CODE, warning.code)
        assertEquals(DiagnosticSeverity.Warning, warning.severity)
        assertEquals(5, warning.source.startLine)
        assertContains(warning.message, "“☃” (U+2603), “♞” (U+265E) are not in Roboto")
    }

    @Test
    fun `each affected line gets its own warning`() {
        val diagnostics = check("# Ada\n\n## Skills\n\n- Chess ♞\n- Kotlin\n- Snow ☃\n")

        assertEquals(listOf(5, 7), diagnostics.map { it.source.startLine })
        assertTrue(diagnostics.all { "an empty box" in it.message })
    }
}
