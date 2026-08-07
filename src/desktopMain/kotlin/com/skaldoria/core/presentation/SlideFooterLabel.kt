package com.skaldoria.core.presentation

import com.skaldoria.markdown.models.Slide
import com.skaldoria.markdown.models.SlideElement

/**
 * The text in the slide footer's left-hand pill.
 *
 * DIA-06 / R-2: the footer printed `slide.layoutType.displayName`, so every diagram slide read
 * "ARCHITECTURE / FLOW DIAGRAM" regardless of what it actually held. The diagram *canvas*
 * header already reports the parsed type; the footer contradicted it on the same slide.
 */
object SlideFooterLabel {

    /**
     * Human-readable names for the diagram types the renderer can actually draw.
     *
     * DED-4 in spirit: a type is named here only if a renderer exists for it, so the footer can
     * never claim a type that was actually shown as source.
     *
     * DIA-01/02/03/04 (2026-08-07) added the four that used to be absent. Until then this map
     * held three entries and the KDoc explained that state, class, ER and Gantt "display their
     * source" — true when written, and exactly the sort of comment that becomes a lie when the
     * feature lands. They now render, so they are named.
     */
    private val DIAGRAM_TYPE_NAMES = mapOf(
        "flowchart" to "Flowchart",
        "graph" to "Flowchart",
        "sequence" to "Sequence Diagram",
        "state" to "State Diagram",
        "class" to "Class Diagram",
        "er" to "Entity Relationship",
        "gantt" to "Gantt Chart"
    )

    /**
     * The footer label for [slide].
     *
     * [parsedDiagramTypeOf] resolves Mermaid source to its parsed type and is injected rather
     * than called directly: the Mermaid parser lives in the UI layer, and this stays a pure
     * `core/` unit with no Compose dependency. It is consulted only for slides that actually
     * carry a diagram, so no parsing happens for ordinary slides on every recomposition.
     */
    fun forSlide(slide: Slide, parsedDiagramTypeOf: (String) -> String?): String {
        val diagram = slide.elements.filterIsInstance<SlideElement.MermaidDiagram>().firstOrNull()
            ?: return slide.layoutType.displayName

        val parsed = parsedDiagramTypeOf(diagram.code)?.lowercase()
        return DIAGRAM_TYPE_NAMES[parsed] ?: slide.layoutType.displayName
    }
}
