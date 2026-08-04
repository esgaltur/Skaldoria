package com.skaldoria.core.document

import com.skaldoria.core.models.Slide
import com.skaldoria.core.parser.MarkdownSlideParser

/**
 * A markdown deck together with the structural edits you can perform on it.
 *
 * Exists to fix COR-1. Slide reordering and deletion previously lived in
 * `PresentationState`, which re-derived slide boundaries with its own splitter — one that
 * recognised only a literal `---` and therefore disagreed with the parser about `##`
 * heading splits and `----` rules. The two index spaces drifted apart, so `deleteSlide`
 * either removed the wrong slide or silently did nothing.
 *
 * Here the boundaries come from [Slide.sourceLineRange], produced by the parser itself, so
 * there is exactly one definition of where a slide starts and ends.
 *
 * Immutable and free of Compose or app state: every operation returns the resulting
 * markdown, which makes the whole class unit testable.
 */
class SlideDocument private constructor(
    val markdown: String,
    val slides: List<Slide>
) {

    companion object {
        /** Separator emitted when reassembling slides. */
        private const val SEPARATOR = "\n\n---\n\n"

        fun of(markdown: String): SlideDocument =
            SlideDocument(markdown, MarkdownSlideParser.parse(markdown))
    }

    val size: Int get() = slides.size

    /** The exact source text of [index], or null when out of range. */
    fun sourceOf(index: Int): String? {
        val slide = slides.getOrNull(index) ?: return null
        return sliceOf(slide)
    }

    private fun sliceOf(slide: Slide): String {
        val range = slide.sourceLineRange
        if (range.isEmpty()) return ""
        val lines = markdown.lines()
        val from = range.first.coerceIn(0, lines.lastIndex)
        val to = range.last.coerceIn(from, lines.lastIndex)
        return lines.subList(from, to + 1).joinToString("\n").trim()
    }

    /** Source text of every slide, in order. */
    private fun chunks(): List<String> = slides.map { sliceOf(it) }

    private fun rebuild(chunks: List<String>): String =
        chunks.filter { it.isNotBlank() }.joinToString(SEPARATOR)

    /**
     * Removes [index]. Returns null when the index is invalid or when it would empty the
     * deck — a deck always keeps at least one slide.
     */
    fun delete(index: Int): String? {
        if (index !in slides.indices || slides.size <= 1) return null
        return rebuild(chunks().toMutableList().apply { removeAt(index) })
    }

    /** Moves [from] to position [to]. Returns null when either index is invalid or they match. */
    fun move(from: Int, to: Int): String? {
        if (from !in slides.indices || to !in slides.indices || from == to) return null
        val updated = chunks().toMutableList()
        updated.add(to, updated.removeAt(from))
        return rebuild(updated)
    }

    /** Inserts a copy of [index] directly after it. Returns null when the index is invalid. */
    fun duplicate(index: Int): String? {
        if (index !in slides.indices) return null
        val updated = chunks().toMutableList()
        updated.add(index + 1, updated[index])
        return rebuild(updated)
    }

    /**
     * Inserts [template] after [afterIndex]. An out-of-range index appends, so callers do
     * not have to special-case an empty deck.
     */
    fun insert(afterIndex: Int, template: String): String {
        val updated = chunks().toMutableList()
        val position = (afterIndex + 1).coerceIn(0, updated.size)
        updated.add(position, template.trim())
        return rebuild(updated)
    }

    /** Replaces the source of [index]. Returns null when the index is invalid. */
    fun replace(index: Int, replacement: String): String? {
        if (index !in slides.indices) return null
        val updated = chunks().toMutableList()
        updated[index] = replacement.trim()
        return rebuild(updated)
    }
}
