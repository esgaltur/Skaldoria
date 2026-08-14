package com.skaldoria.cv

import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.skaldoria.cv.core.layout.*
import com.skaldoria.markdown.parser.InlineRun
import kotlin.math.roundToInt

/**
 * The production [CvTextMeasurer]: real shaping, from the same text stack that draws the preview.
 *
 * **Measured at density 1.0, deliberately.** One point becomes one pixel becomes one `sp`, so the
 * resolved layout is in typographic points and is identical on every monitor. Measuring at the
 * screen's density would make a CV paginate differently on a HiDPI display than on a projector —
 * CV-NFR-041 says identical source and settings must produce identical pages, and the user's
 * hardware is not one of the settings. The preview scales the finished layout for display instead.
 *
 * The [TextLayoutResult] behind every measurement is kept, because the preview draws the very
 * objects the engine measured rather than laying the text out a second time. That is what removes
 * the last opportunity for preview and export to disagree.
 */
class ComposeCvTextMeasurer(
    private val bodyFont: FontFamily,
    private val headingFont: FontFamily
) : CvTextMeasurer {

    private val measurer = TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(),
        defaultDensity = Density(density = 1f, fontScale = 1f),
        defaultLayoutDirection = LayoutDirection.Ltr,
        cacheSize = 256
    )

    private data class Key(
        val runs: List<InlineRun>,
        val style: CvTextStyle,
        val maxWidth: Int
    )

    private val cache = HashMap<Key, Pair<CvMeasuredText, TextLayoutResult>>()

    override fun measure(
        runs: List<InlineRun>,
        style: CvTextStyle,
        maxWidthPt: Double
    ): CvMeasuredText = layoutOf(runs, style, maxWidthPt).first

    /** The Compose layout for a measurement the engine already made. */
    fun layoutFor(
        runs: List<InlineRun>,
        style: CvTextStyle,
        maxWidthPt: Double
    ): TextLayoutResult = layoutOf(runs, style, maxWidthPt).second

    private fun layoutOf(
        runs: List<InlineRun>,
        style: CvTextStyle,
        maxWidthPt: Double
    ): Pair<CvMeasuredText, TextLayoutResult> {
        val width = maxWidthPt.roundToInt().coerceAtLeast(1)
        return cache.getOrPut(Key(runs, style, width)) { build(runs, style, width) }
    }

    private fun build(
        runs: List<InlineRun>,
        style: CvTextStyle,
        maxWidth: Int
    ): Pair<CvMeasuredText, TextLayoutResult> {
        // Offsets are tracked while the string is assembled, so a run can be located again after
        // shaping has broken it across lines.
        val spans = ArrayList<Triple<Int, Int, InlineRun>>(runs.size)
        val annotated = buildAnnotatedString {
            for (run in runs) {
                val start = length
                append(run.text)
                addStyle(spanStyleFor(run), start, length)
                spans += Triple(start, length, run)
            }
        }

        val result = measurer.measure(
            text = annotated,
            style = textStyleFor(style),
            constraints = Constraints(maxWidth = maxWidth)
        )

        val lines = ArrayList<CvTextLine>(result.lineCount)
        for (index in 0 until result.lineCount) {
            val lineStart = result.getLineStart(index)
            val lineEnd = result.getLineEnd(index, visibleEnd = true)
            val positioned = ArrayList<CvPositionedRun>()

            for ((spanStart, spanEnd, run) in spans) {
                val from = maxOf(spanStart, lineStart)
                val to = minOf(spanEnd, lineEnd)
                if (from >= to) continue

                val x = result.getHorizontalPosition(from, usePrimaryDirection = true)
                val end = result.getHorizontalPosition(to, usePrimaryDirection = true)
                positioned += CvPositionedRun(
                    text = annotated.text.substring(from, to),
                    xPt = x.toDouble(),
                    widthPt = (end - x).toDouble(),
                    bold = run.bold,
                    italic = run.italic,
                    code = run.code,
                    strikethrough = run.strikethrough,
                    link = run.link
                )
            }

            lines += CvTextLine(
                runs = positioned,
                topPt = result.getLineTop(index).toDouble(),
                baselinePt = result.getLineBaseline(index).toDouble(),
                heightPt = (result.getLineBottom(index) - result.getLineTop(index)).toDouble()
            )
        }

        val measured = CvMeasuredText(
            lines = lines,
            heightPt = if (lines.isEmpty()) 0.0 else result.size.height.toDouble(),
            sourceRuns = runs,
            maxWidthPt = maxWidth.toDouble()
        )
        return measured to result
    }

    fun textStyleFor(style: CvTextStyle): TextStyle = TextStyle(
        fontFamily = when (style.fontRole) {
            CvFontRole.Body -> bodyFont
            CvFontRole.Heading -> headingFont
        },
        fontSize = style.sizePt.sp,
        lineHeight = style.lineHeightPt.sp,
        fontWeight = when (style.weight) {
            CvFontWeight.Normal -> FontWeight.Normal
            CvFontWeight.Medium -> FontWeight.Medium
            CvFontWeight.SemiBold -> FontWeight.SemiBold
            CvFontWeight.Bold -> FontWeight.Bold
        },
        letterSpacing = style.letterSpacingPt.sp
    )

    private fun spanStyleFor(run: InlineRun) = SpanStyle(
        fontWeight = if (run.bold) FontWeight.Bold else null,
        fontStyle = if (run.italic) FontStyle.Italic else null,
        fontFamily = if (run.code) FontFamily.Monospace else null,
        textDecoration = if (run.strikethrough) TextDecoration.LineThrough else null
    )
}
