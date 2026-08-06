package com.skaldoria.core.presentation

import com.skaldoria.core.models.Slide
import com.skaldoria.core.models.SlideElement
import com.skaldoria.core.models.SlideLayoutType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * DIA-06 / R-2: the slide footer reports what the slide actually is.
 *
 * The footer printed `slide.layoutType.displayName`, so every diagram slide read
 * "ARCHITECTURE / FLOW DIAGRAM" whether it held a flowchart or a sequence diagram. The
 * diagram *canvas* header already reports the parsed type; the footer did not.
 *
 * The parsed type is supplied by the caller rather than parsed here: the Mermaid parser lives
 * in the UI layer, and DED-4 keeps one parser rather than a second sniffing implementation.
 */
class SlideFooterLabelTest {

    private fun diagramSlide(code: String) = Slide(
        index = 0,
        title = "Architecture",
        layoutType = SlideLayoutType.DIAGRAM,
        elements = listOf(SlideElement.MermaidDiagram(code = code))
    )

    private fun bulletSlide() = Slide(
        index = 0,
        title = "Agenda",
        layoutType = SlideLayoutType.BULLET_LIST,
        elements = listOf(SlideElement.BulletList(listOf("one", "two")))
    )

    @Test
    fun `a non-diagram slide reports its layout type`() {
        assertEquals(
            SlideLayoutType.BULLET_LIST.displayName,
            SlideFooterLabel.forSlide(bulletSlide()) { null }
        )
    }

    @Test
    fun `a diagram slide reports the parsed diagram type`() {
        assertEquals(
            "Sequence Diagram",
            SlideFooterLabel.forSlide(diagramSlide("sequenceDiagram\nA->>B: hi")) { "sequence" },
            "R-2: a sequence diagram must not read as 'Architecture / Flow Diagram'"
        )
    }

    @Test
    fun `a flowchart reports as a flowchart`() {
        assertEquals(
            "Flowchart",
            SlideFooterLabel.forSlide(diagramSlide("flowchart LR\nA-->B")) { "flowchart" }
        )
    }

    @Test
    fun `an unrecognised diagram type falls back to the layout name`() {
        // An unsupported type (state, class, ER, Gantt) displays its source, so claiming a
        // diagram type the renderer did not draw would be a lie.
        assertEquals(
            SlideLayoutType.DIAGRAM.displayName,
            SlideFooterLabel.forSlide(diagramSlide("stateDiagram-v2")) { null }
        )
    }

    @Test
    fun `a diagram-layout slide with no diagram element falls back safely`() {
        val empty = Slide(
            index = 0,
            title = "Empty",
            layoutType = SlideLayoutType.DIAGRAM,
            elements = emptyList()
        )
        assertEquals(SlideLayoutType.DIAGRAM.displayName, SlideFooterLabel.forSlide(empty) { "flowchart" })
    }

    @Test
    fun `the resolver is not consulted for non-diagram slides`() {
        // Parsing Mermaid for a bullet slide would be wasted work on every recomposition.
        var consulted = false
        SlideFooterLabel.forSlide(bulletSlide()) { consulted = true; "flowchart" }
        assertEquals(false, consulted)
    }
}
