package com.skaldoria.cv.core.pdf

import com.skaldoria.cv.core.CvDiagnostic
import com.skaldoria.cv.core.DiagnosticSeverity
import com.skaldoria.cv.core.SourceRange
import com.skaldoria.cv.core.layout.CvPageElement
import com.skaldoria.cv.core.layout.CvResolvedLayout

/**
 * Reports characters the embedded font cannot draw — CV-NFR-042.
 *
 * **Why this is a check rather than a fallback.** The preview shapes text with Skia, which quietly
 * borrows a glyph from any installed system font when the CV font lacks one. The PDF cannot do
 * that: it embeds exactly one program per face, and a character outside its `cmap` is drawn as
 * `.notdef` — an empty box. So a CV could look right on screen and print with holes in it, and
 * nothing told the user. Embedding a second font for the missing characters would hide the problem
 * only on this machine, since the fallback depends on what is installed.
 *
 * The answer is read from the font's own `cmap` via [TrueTypeFont.hasGlyph], for the face
 * [CvPdfFonts.faceFor] picks for each run — the same decision the renderer makes — so the report
 * names exactly the characters that will become boxes, and nothing else.
 */
object CvGlyphCoverage {

    const val CODE = "CV_GLYPH_MISSING"

    /** One warning per source line, listing its missing characters in order of appearance. */
    fun check(layout: CvResolvedLayout, fonts: CvPdfFonts): List<CvDiagnostic> {
        val missingByLine = LinkedHashMap<Int, MissingOnLine>()

        for (page in layout.pages) {
            for (block in page.elements.filterIsInstance<CvPageElement.TextBlock>()) {
                val line = block.source?.startLine ?: 1
                for (run in block.text.lines.flatMap { it.runs }) {
                    val face = fonts.faceFor(run)
                    run.text.codePoints().forEach { codePoint ->
                        if (!Character.isISOControl(codePoint) && !face.hasGlyph(codePoint)) {
                            missingByLine.getOrPut(line) { MissingOnLine() }.add(codePoint, face.familyName)
                        }
                    }
                }
            }
        }

        return missingByLine.map { (line, missing) -> missing.toDiagnostic(line) }
    }

    private class MissingOnLine {
        private val codePoints = LinkedHashSet<Int>()
        private val faces = LinkedHashSet<String>()

        fun add(codePoint: Int, face: String) {
            codePoints += codePoint
            faces += face
        }

        fun toDiagnostic(line: Int): CvDiagnostic {
            val single = codePoints.size == 1
            val they = if (single) "it" else "them"
            return CvDiagnostic(
                code = CODE,
                severity = DiagnosticSeverity.Warning,
                message = "${quoted()} ${if (single) "is" else "are"} not in ${faces.joinToString(" / ")}, " +
                    "so the PDF will show ${if (single) "an empty box" else "empty boxes"} there.",
                action = "Replace $they with a character the font has, or choose a font that includes $they. " +
                    "The preview may still show $they, borrowed from a system font.",
                source = SourceRange(line)
            )
        }

        private fun quoted(): String = codePoints.joinToString(", ") { codePoint ->
            "“${String(Character.toChars(codePoint))}” (U+${codePoint.toString(16).uppercase().padStart(4, '0')})"
        }
    }
}
