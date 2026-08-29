package com.skaldoria.cv.core.layout

import com.skaldoria.cv.core.CvDiagnostic
import com.skaldoria.cv.core.CvPaperSize
import com.skaldoria.cv.core.DiagnosticSeverity
import com.skaldoria.cv.core.SourceRange
import com.skaldoria.markdown.parser.InlineRun

/**
 * The resolved layout model — CV-FR-041 and CV-NFR-041.
 *
 * **Why this exists as a model rather than a rendering.** Pagination used to live inside the
 * preview's `SubcomposeLayout`, which meant it existed only while Compose was measuring and could
 * not be inspected, tested, or reused. Export had nothing to share, so a PDF renderer would have
 * had to decide page breaks a second time — and any disagreement between the two would show as a
 * CV that exports differently from what the user approved on screen.
 *
 * Everything here is in **typographic points**, the unit PDF uses natively and the one
 * [CvPaperSize] already states its geometry in.
 *
 * Compose-free on purpose: measurement arrives through [CvTextMeasurer], so the engine can be
 * unit-tested against a deterministic fake while the application supplies a real text stack.
 */

/** A colour named by its job. The renderer maps it; the layout must not know about pixels. */
enum class CvColorRole { PrimaryText, SecondaryText, Accent, Divider, Missing }

/** Which of the two configured typefaces a run uses. */
enum class CvFontRole { Body, Heading }

enum class CvFontWeight { Normal, Medium, SemiBold, Bold }

data class CvTextStyle(
    val fontRole: CvFontRole,
    val sizePt: Double,
    val lineHeightPt: Double,
    val weight: CvFontWeight = CvFontWeight.Normal,
    val letterSpacingPt: Double = 0.0,
    val color: CvColorRole = CvColorRole.PrimaryText
)

/** One run of text on one line, already positioned within that line. */
data class CvPositionedRun(
    val text: String,
    val xPt: Double,
    val widthPt: Double,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strikethrough: Boolean = false,
    val link: String? = null
)

/**
 * One laid-out line.
 *
 * [baselinePt] is measured from the top of the block, because that is what a PDF text object needs
 * — PDF positions text by baseline, not by box.
 */
data class CvTextLine(
    val runs: List<CvPositionedRun>,
    val topPt: Double,
    val baselinePt: Double,
    val heightPt: Double
)

/**
 * @param sourceRuns and [maxWidthPt] are carried so a renderer can ask the measurer for this exact
 *   measurement again. The preview uses them to redraw the very layout the engine paginated from,
 *   rather than reconstructing an approximation of it from the positioned output.
 */
data class CvMeasuredText(
    val lines: List<CvTextLine>,
    val heightPt: Double,
    val sourceRuns: List<InlineRun> = emptyList(),
    val maxWidthPt: Double = 0.0
) {
    /** The text as a reader encounters it, which is what the extraction guards assert on. */
    val plainText: String get() = lines.joinToString("\n") { line -> line.runs.joinToString("") { it.text } }
}

/**
 * The single measurement authority.
 *
 * Preview and export both lay out from one [CvResolvedLayout], so there is exactly one
 * implementation in production — the Compose-backed one. That is what makes CV-NFR-041 hold:
 * identical source and settings cannot produce different pagination in the two outputs, because
 * they are not two layouts.
 */
fun interface CvTextMeasurer {
    fun measure(runs: List<InlineRun>, style: CvTextStyle, maxWidthPt: Double): CvMeasuredText
}

sealed interface CvPageElement {
    /** Offset from the top of the page's content box. */
    val yPt: Double

    /** The Markdown that produced this element, where one line produced it. */
    val source: SourceRange?

    data class TextBlock(
        val xPt: Double,
        override val yPt: Double,
        val text: CvMeasuredText,
        val style: CvTextStyle,
        override val source: SourceRange? = null
    ) : CvPageElement

    data class Rule(
        val xPt: Double,
        override val yPt: Double,
        val widthPt: Double,
        val thicknessPt: Double,
        val color: CvColorRole = CvColorRole.Divider,
        override val source: SourceRange? = null
    ) : CvPageElement
}

/**
 * Content the printable box cannot hold — CV-FR-046.
 *
 * A flow item taller than the content box is the one case [com.skaldoria.cv.core.PagePacker]
 * cannot solve by starting another page: moving it changes which page truncates it, not whether
 * one does. Preview clips such an item at the sheet edge and the PDF draws it below the page,
 * so without this report the text simply disappears from both outputs — the silent omission the
 * requirement forbids. Reporting is the honest outcome; the engine never scales or crops to hide
 * the problem.
 *
 * @param requiredPt the item's measured height, and [availablePt] the content box it must fit.
 */
data class CvLayoutOverflow(
    val pageNumber: Int,
    val label: String,
    val source: SourceRange?,
    val requiredPt: Double,
    val availablePt: Double
) {
    /** How much taller than one page the item is. Always positive for a reported overflow. */
    val excessPt: Double get() = requiredPt - availablePt

    /**
     * Rendered into the same panel as the parser's findings, because to a user "this will not fit
     * on the page" is the same kind of problem as "this heading has no section".
     */
    fun toDiagnostic(): CvDiagnostic = CvDiagnostic(
        code = "CV_CONTENT_OVERFLOW",
        severity = DiagnosticSeverity.Error,
        message = "$label is ${excessPt.roundedPoints()} pt taller than page $pageNumber can hold.",
        action = "Split it into shorter paragraphs or list items, widen the margins, or reduce the font size.",
        source = source ?: SourceRange(1)
    )
}

private fun Double.roundedPoints(): String = kotlin.math.round(this).toInt().toString()

/**
 * @param elements in reading order — CV-FR-061 is satisfied by preserving this order when writing
 *   the content stream, not by sorting geometry afterwards.
 */
data class CvLayoutPage(
    val pageNumber: Int,
    val elements: List<CvPageElement>,
    val footer: CvPageElement.TextBlock?
)

data class CvResolvedLayout(
    val paper: CvPaperSize,
    val pages: List<CvLayoutPage>,
    val title: String,
    val author: String?,
    /** Empty when every item fits. See [CvLayoutOverflow]. */
    val overflows: List<CvLayoutOverflow> = emptyList()
) {
    val pageCount: Int get() = pages.size

    /** Reading-order text across the whole document, used by the layout regression guards. */
    fun extractText(): String = pages.joinToString("\n") { page ->
        page.elements.filterIsInstance<CvPageElement.TextBlock>()
            .joinToString("\n") { it.text.plainText }
    }

    /**
     * The one-based page showing the Markdown at [line], or null when nothing on any page came
     * from it. Blank lines and content the preview omits legitimately have no page.
     *
     * Outline navigation (CV-FR-024) uses this to move the preview to the section the user picked,
     * which is only possible because elements carry the source range they were built from.
     */
    fun pageContaining(line: Int): Int? = pages.firstOrNull { page ->
        page.elements.any { element ->
            element.source?.let { line >= it.startLine && line <= it.endLine } == true
        }
    }?.pageNumber
}
