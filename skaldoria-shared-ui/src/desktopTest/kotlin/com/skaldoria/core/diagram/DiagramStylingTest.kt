package com.skaldoria.core.diagram

import androidx.compose.ui.graphics.Color
import com.skaldoria.ui.components.MermaidParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DIA-07 — `classDef`, `class`, `style` and `linkStyle` reach the renderer.
 *
 * These statements were matched by `IGNORED_DIRECTIVE` purely so the node scanner would not
 * register `classDef` as a node id, and then dropped. A colour-coded flowchart — which is most
 * of the interesting ones — rendered monochrome with nothing to say it had discarded anything.
 *
 * The resolution order and the forward-reference cases are the parts worth pinning: Mermaid
 * imposes no ordering, so `class a big` may legally appear before the `classDef big …` it
 * names, and an eager resolver silently drops exactly those.
 */
class DiagramStylingTest {

    private fun stylingOf(body: String) = MermaidParser.parse("graph TD\n$body").styling

    // ---- the declaration grammar --------------------------------------------

    @Test
    fun `hex colours are parsed in every length CSS allows`() {
        assertEquals(StyleDeclarationParser.parseColor("#f0f"), Color(0xFFFF00FF))
        assertEquals(StyleDeclarationParser.parseColor("#FF00FF"), Color(0xFFFF00FF))
        assertEquals(StyleDeclarationParser.parseColor("#FF00FF80"), Color(0x80FF00FF))
    }

    @Test
    fun `named colours that authors actually type are understood`() {
        assertEquals(StyleDeclarationParser.parseColor("red"), Color(0xFFFF0000))
        assertEquals(StyleDeclarationParser.parseColor("grey"), Color(0xFF808080))
        assertEquals(StyleDeclarationParser.parseColor("GRAY"), Color(0xFF808080))
    }

    @Test
    fun `an unknown colour falls back rather than failing the diagram`() {
        // Falling through to the theme is a reasonable outcome; refusing to render is not.
        assertNull(StyleDeclarationParser.parseColor("chartreuse"))
        assertNull(StyleDeclarationParser.parseColor("none"))
        assertNull(StyleDeclarationParser.parseColor("#12345"))
        assertNull(StyleDeclarationParser.parseColor(""))
    }

    @Test
    fun `unset properties stay null instead of taking a default`() {
        // A diagram that sets only `fill` must not lose stroke and text to colours the
        // palette never chose — that is why every field is nullable.
        val style = StyleDeclarationParser.parseNodeStyle("fill:#f9f")

        assertEquals(Color(0xFFFF99FF), style.fill)
        assertNull(style.stroke)
        assertNull(style.textColor)
        assertNull(style.strokeWidthPx)
    }

    @Test
    fun `a full declaration is read in all its parts`() {
        val style = StyleDeclarationParser.parseNodeStyle("fill:#f9f,stroke:#333,color:#fff,stroke-width:4px")

        assertEquals(Color(0xFFFF99FF), style.fill)
        assertEquals(Color(0xFF333333), style.stroke)
        assertEquals(Color(0xFFFFFFFF), style.textColor)
        assertEquals(4f, style.strokeWidthPx)
    }

    @Test
    fun `an unrecognised property is skipped, not fatal`() {
        val style = StyleDeclarationParser.parseNodeStyle("fill:#f9f,rx:8,unknown-thing:whatever")
        assertEquals(Color(0xFFFF99FF), style.fill, "a valid property was lost to an invalid neighbour")
    }

    // ---- the statements, in a diagram ---------------------------------------

    @Test
    fun `classDef plus class colours the named nodes`() {
        val styling = stylingOf(
            """
            A[Start] --> B[Finish]
            classDef highlight fill:#f9f,stroke:#333
            class A highlight
            """.trimIndent()
        )

        assertEquals(Color(0xFFFF99FF), styling.forNode("A")?.fill)
        assertNull(styling.forNode("B"), "an unstyled node must carry no style at all")
    }

    @Test
    fun `a class assignment may precede the classDef it names`() {
        // Mermaid imposes no ordering. An eager resolver drops exactly this case.
        val styling = stylingOf(
            """
            A[Start] --> B[Finish]
            class A highlight
            classDef highlight fill:#0f0
            """.trimIndent()
        )

        assertEquals(Color(0xFF00FF00), styling.forNode("A")?.fill, "the forward reference was dropped")
    }

    @Test
    fun `one classDef can name several classes and one class several nodes`() {
        val styling = stylingOf(
            """
            A --> B --> C
            classDef warn,danger fill:#f00
            class A,B warn
            """.trimIndent()
        )

        assertEquals(Color(0xFFFF0000), styling.forNode("A")?.fill)
        assertEquals(Color(0xFFFF0000), styling.forNode("B")?.fill)
        assertNull(styling.forNode("C"))
    }

    @Test
    fun `an inline style refines the class rather than replacing it`() {
        val styling = stylingOf(
            """
            A --> B
            classDef base fill:#eee,stroke:#111
            class A base
            style A fill:#0ff
            """.trimIndent()
        )

        val style = styling.forNode("A")
        assertEquals(Color(0xFF00FFFF), style?.fill, "the more specific inline fill should win")
        assertEquals(Color(0xFF111111), style?.stroke, "the class's stroke should survive")
    }

    @Test
    fun `linkStyle colours edges by declaration order`() {
        val styling = stylingOf(
            """
            A --> B
            B --> C
            linkStyle 1 stroke:#f00
            """.trimIndent()
        )

        assertNull(styling.forEdge(0)?.stroke)
        assertEquals(Color(0xFFFF0000), styling.forEdge(1)?.stroke)
    }

    @Test
    fun `linkStyle default colours every edge`() {
        val styling = stylingOf(
            """
            A --> B
            B --> C
            linkStyle default stroke:#00f
            """.trimIndent()
        )

        assertEquals(Color(0xFF0000FF), styling.forEdge(0)?.stroke)
        assertEquals(Color(0xFF0000FF), styling.forEdge(1)?.stroke)
    }

    @Test
    fun `an explicit linkStyle overrides the default`() {
        val styling = stylingOf(
            """
            A --> B
            B --> C
            linkStyle default stroke:#00f
            linkStyle 0 stroke:#f00
            """.trimIndent()
        )

        assertEquals(Color(0xFFFF0000), styling.forEdge(0)?.stroke)
        assertEquals(Color(0xFF0000FF), styling.forEdge(1)?.stroke)
    }

    // ---- the regression surface ---------------------------------------------

    @Test
    fun `styling statements still never become nodes`() {
        // The original reason these lines were matched at all. Widening them from "skip" to
        // "collect" must not reopen it.
        val diagram = MermaidParser.parse(
            """
            graph TD
            A[Start] --> B[Finish]
            classDef highlight fill:#f9f
            class A highlight
            style B fill:#0f0
            linkStyle 0 stroke:#f00
            """.trimIndent()
        )

        assertEquals(
            setOf("A", "B"), diagram.nodes.map { it.id }.toSet(),
            "a styling statement leaked into the graph as a node"
        )
        assertEquals(1, diagram.edges.size)
    }

    @Test
    fun `a diagram with no styling carries an empty instance`() {
        val styling = stylingOf("A --> B")

        assertTrue(styling.isEmpty)
        assertNull(styling.forNode("A"))
        assertNull(styling.forEdge(0))
    }

    @Test
    fun `a malformed styling statement is ignored and the diagram still parses`() {
        val diagram = MermaidParser.parse("graph TD\nA --> B\nclassDef\nclass\nlinkStyle")

        assertEquals(setOf("A", "B"), diagram.nodes.map { it.id }.toSet())
        assertEquals(1, diagram.edges.size)
    }
}
