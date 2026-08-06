package com.skaldoria.core.document

import com.skaldoria.core.models.Slide

/**
 * Where a slide lives inside the deck's markdown, as a character offset — and the reverse.
 *
 * EDT-3: both directions derive from [Slide.sourceLineRange], which the parser produces.
 * Nothing here re-derives slide boundaries; that is COR-1, and [SlideDocument] is the existing
 * precedent for consuming the range rather than re-splitting the text.
 *
 * Pure and Compose-free, so the mapping is testable without a renderer — the same Tier A shape
 * ADR-002 identified as the one this codebase already does well.
 */
object SlideSourceLocator {

    /**
     * Character offset of the first line of every line in [text].
     *
     * Computed by walking the string rather than summing `lines()` lengths, because `lines()`
     * splits on `\r\n`, `\n` *and* a bare `\r`, and a deck authored on Windows and opened here
     * carries `\r\n`. Summing `length + 1` per line would drift by one character per line and
     * put the caret in the wrong place by the bottom of a long deck.
     */
    internal fun lineStartOffsets(text: String): List<Int> {
        val starts = ArrayList<Int>(16)
        starts.add(0)
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    starts.add(i + 1)
                }
                '\n' -> starts.add(i + 1)
            }
            i++
        }
        return starts
    }

    /**
     * Offset at which editing [slide] should begin.
     *
     * The range's *first* line is where the parser started accumulating, which after a `---`
     * is usually the blank line before the heading. Leading blank lines inside the range are
     * skipped so the caret lands on the slide's first real line — a position inside the range
     * the parser gave us, not a boundary this function invented.
     *
     * Returns 0 for a slide with no recorded range, which is the synthetic slide the parser
     * emits for empty markdown.
     */
    fun offsetOfSlide(markdown: String, slide: Slide): Int {
        val range = slide.sourceLineRange
        if (range.isEmpty()) return 0

        val lines = markdown.lines()
        if (lines.isEmpty()) return 0

        val first = range.first.coerceIn(0, lines.lastIndex)
        val last = range.last.coerceIn(first, lines.lastIndex)
        val target = (first..last).firstOrNull { lines[it].isNotBlank() } ?: first

        return lineStartOffsets(markdown).getOrElse(target) { 0 }
    }

    /** [offsetOfSlide] for a slide index, or 0 when the index is not in [slides]. */
    fun offsetOfSlideIndex(markdown: String, slides: List<Slide>, index: Int): Int {
        val slide = slides.getOrNull(index) ?: return 0
        return offsetOfSlide(markdown, slide)
    }

    /**
     * Which slide the caret at [offset] sits in.
     *
     * An offset can legitimately belong to no slide — a `---` separator line is consumed by the
     * parser and recorded in nobody's range — so an offset that falls between two slides is
     * attributed to the one *before* it. That is what makes the round trip with [offsetOfSlide]
     * total: every offset maps to some slide, and every slide's own offset maps back to itself.
     *
     * Returns 0 when [slides] is empty.
     */
    fun slideIndexAtOffset(markdown: String, slides: List<Slide>, offset: Int): Int {
        if (slides.isEmpty()) return 0

        val line = lineAtOffset(markdown, offset)

        slides.indexOfFirst { line in it.sourceLineRange }
            .takeIf { it >= 0 }
            ?.let { return it }

        // Between slides, or past the last one: the nearest slide that starts at or before
        // this line. `indexOfLast` rather than a search, because a slide with an empty range
        // must never be picked up by a `<=` comparison against its meaningless `first`.
        val preceding = slides.indexOfLast { !it.sourceLineRange.isEmpty() && it.sourceLineRange.first <= line }
        return preceding.coerceAtLeast(0)
    }

    /** Zero-based line containing [offset], clamped into [markdown]. */
    internal fun lineAtOffset(markdown: String, offset: Int): Int {
        val starts = lineStartOffsets(markdown)
        val clamped = offset.coerceIn(0, markdown.length)

        // starts is ascending; binarySearch returns the insertion point negated when absent.
        val found = starts.binarySearch { it.compareTo(clamped) }
        return if (found >= 0) found else (-found - 2).coerceIn(0, starts.lastIndex)
    }
}
