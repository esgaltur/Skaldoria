package com.skaldoria.core.search

import com.skaldoria.markdown.models.Slide
import com.skaldoria.markdown.models.SlideElement

/**
 * Full-text search across a deck, for the command palette.
 *
 * COR-13: extracted from the `CommandPalette` composable, where the matching logic sat inline
 * and — critically — ended in `else -> false` over the **sealed** [SlideElement] hierarchy.
 * Four variants therefore fell into that branch and were silently unsearchable: polls,
 * diagrams, formulas and images. A speaker could not jump to the slide holding a poll by
 * typing one of its options.
 *
 * **The `when` below deliberately has no `else`.** Kotlin makes an exhaustive `when` over a
 * sealed hierarchy a *compile* error when a variant is missing, which is the Visitor pattern's
 * guarantee for free — but only while nobody writes `else`. Adding a variant to
 * [SlideElement] must break this file, not silently make that content unfindable. The same
 * `else` habit is what let EXP-3 ship, where tables, images and polls vanished from PNG
 * export.
 */
object SlideSearch {

    /** Slides in [slides] matching [query]; a blank query filters nothing. */
    fun filter(slides: List<Slide>, query: String): List<Slide> {
        if (query.isBlank()) return slides
        return slides.filter { matches(it, query) }
    }

    /** True when any searchable text on [slide] contains [query], ignoring case. */
    fun matches(slide: Slide, query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true

        return slide.title.contains(needle, ignoreCase = true) ||
            slide.subtitle?.contains(needle, ignoreCase = true) == true ||
            slide.notes.any { it.contains(needle, ignoreCase = true) } ||
            slide.elements.any { elementMatches(it, needle) }
    }

    private fun elementMatches(element: SlideElement, needle: String): Boolean = when (element) {
        is SlideElement.Text -> element.content.hit(needle)
        is SlideElement.BulletList -> element.items.anyHit(needle)
        is SlideElement.CodeBlock -> element.code.hit(needle) || element.language.hit(needle)
        is SlideElement.Table ->
            element.headers.anyHit(needle) || element.rows.any { it.anyHit(needle) }
        is SlideElement.Quote -> element.quote.hit(needle) || element.author.hit(needle)
        // The four that `else -> false` used to swallow.
        is SlideElement.Image ->
            element.altText.hit(needle) || element.url.hit(needle) || element.caption.hit(needle)
        is SlideElement.Metric -> element.value.hit(needle) || element.label.hit(needle)
        is SlideElement.MermaidDiagram -> element.code.hit(needle) || element.diagramType.hit(needle)
        is SlideElement.MathFormula -> element.formula.hit(needle)
        is SlideElement.Poll -> element.question.hit(needle) || element.options.anyHit(needle)
    }

    private fun String?.hit(needle: String) = this?.contains(needle, ignoreCase = true) == true

    private fun List<String>.anyHit(needle: String) = any { it.contains(needle, ignoreCase = true) }
}
