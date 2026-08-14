package com.skaldoria.cv.core.layout

import com.skaldoria.markdown.parser.InlineRun

/**
 * A deterministic stand-in for a real text stack: every glyph is [advanceRatio] × the font size
 * wide, and lines break on whole words.
 *
 * The engine's job is pagination and positioning, not glyph metrics, so testing it against a fake
 * whose numbers can be worked out by hand is the point — a test that asserted real Skia metrics
 * would be pinning the font vendor, not the layout.
 */
class FixedPitchMeasurer(private val advanceRatio: Double = 0.5) : CvTextMeasurer {

    override fun measure(
        runs: List<InlineRun>,
        style: CvTextStyle,
        maxWidthPt: Double
    ): CvMeasuredText {
        val charWidth = style.sizePt * advanceRatio + style.letterSpacingPt
        val perLine = maxOf(1, (maxWidthPt / charWidth).toInt())

        // Flatten to characters tagged with their run, so wrapping can cut mid-run the way a real
        // shaper does, then regroup each visual line back into runs.
        val tagged = runs.flatMap { run -> run.text.map { it to run } }
        if (tagged.isEmpty()) return CvMeasuredText(emptyList(), 0.0)

        val lines = ArrayList<CvTextLine>()
        var index = 0
        while (index < tagged.size) {
            var take = minOf(perLine, tagged.size - index)

            // Prefer a word boundary when the line is full and does not already end at one.
            if (index + take < tagged.size && tagged[index + take].first != ' ') {
                val lastSpace = (take - 1 downTo 1).firstOrNull { tagged[index + it].first == ' ' }
                if (lastSpace != null) take = lastSpace + 1
            }

            val slice = tagged.subList(index, index + take)
            val positioned = ArrayList<CvPositionedRun>()
            var x = 0.0
            var start = 0
            while (start < slice.size) {
                val run = slice[start].second
                var end = start
                while (end < slice.size && slice[end].second === run) end++
                val text = slice.subList(start, end).map { it.first }.joinToString("")
                val width = text.length * charWidth
                positioned += CvPositionedRun(
                    text = text,
                    xPt = x,
                    widthPt = width,
                    bold = run.bold,
                    italic = run.italic,
                    code = run.code,
                    strikethrough = run.strikethrough,
                    link = run.link
                )
                x += width
                start = end
            }

            lines += CvTextLine(
                runs = positioned,
                topPt = lines.size * style.lineHeightPt,
                baselinePt = lines.size * style.lineHeightPt + style.sizePt,
                heightPt = style.lineHeightPt
            )
            index += take
        }

        return CvMeasuredText(lines, lines.size * style.lineHeightPt)
    }
}
