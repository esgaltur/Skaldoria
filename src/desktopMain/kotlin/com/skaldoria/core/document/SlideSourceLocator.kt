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
     * Character offset at which zero-based [line] begins, or the end of [text] if it has fewer
     * lines than that.
     *
     * PRF-5: a single forward scan with no allocation. This runs on every caret movement, and
     * the first version materialised `text.lines()` *and* an `ArrayList<Int>` of every line
     * start — a boxed `Integer` per line of the deck, per keystroke, to answer a question about
     * one line.
     *
     * Line ends are recognised as `\r\n`, `\n` and a bare `\r`, matching Kotlin's `lines()`,
     * because a deck authored on Windows carries `\r\n`. Summing `length + 1` per line — the
     * obvious shortcut — would drift by one character per line and put the caret in the wrong
     * place well before the bottom of a long deck.
     */
    internal fun offsetOfLine(text: String, line: Int): Int {
        if (line <= 0) return 0
        var seen = 0
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    seen++
                }
                '\n' -> seen++
            }
            i++
            if (seen == line) return i
        }
        return text.length
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
        if (range.isEmpty() || markdown.isEmpty()) return 0

        // PRF-5: walks only this slide's own lines. The first version called `markdown.lines()`,
        // allocating the entire document as a list of strings to look at a handful of them.
        val start = offsetOfLine(markdown, range.first)
        var lineStart = start
        var line = range.first

        while (line <= range.last && lineStart < markdown.length) {
            val lineEnd = endOfLine(markdown, lineStart)
            if (hasNonBlank(markdown, lineStart, lineEnd)) return lineStart
            lineStart = startOfNextLine(markdown, lineEnd)
            line++
        }
        return start
    }

    /** Index of the terminator ending the line that begins at [from], or the text length. */
    private fun endOfLine(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i] != '\n' && text[i] != '\r') i++
        return i
    }

    /** Start of the line after the one ending at [terminator]. */
    private fun startOfNextLine(text: String, terminator: Int): Int {
        if (terminator >= text.length) return text.length
        val isCrLf = text[terminator] == '\r' &&
            terminator + 1 < text.length &&
            text[terminator + 1] == '\n'
        return terminator + if (isCrLf) 2 else 1
    }

    private fun hasNonBlank(text: String, from: Int, to: Int): Boolean {
        for (i in from until to) if (!text[i].isWhitespace()) return true
        return false
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

    /**
     * Zero-based line containing [offset], clamped into [markdown].
     *
     * PRF-5: counts line ends up to [offset] and stops there, rather than building an index of
     * the whole document and binary-searching it. Same reasoning as [offsetOfLine].
     */
    internal fun lineAtOffset(markdown: String, offset: Int): Int {
        val clamped = offset.coerceIn(0, markdown.length)
        var line = 0
        var i = 0
        while (i < clamped) {
            when (markdown[i]) {
                '\r' -> {
                    if (i + 1 < markdown.length && markdown[i + 1] == '\n') i++
                    if (i + 1 <= clamped) line++
                }
                '\n' -> line++
            }
            i++
        }
        return line
    }
}
