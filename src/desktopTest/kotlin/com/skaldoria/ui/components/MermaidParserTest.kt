package com.skaldoria.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** MMD-5 / MMD-6 — flowchart parsing. */
class MermaidParserTest {

    /** MMD-5: `find` returned only the first pair, silently dropping the tail of a chain. */
    @Test
    fun `chained edges on one line are all captured`() {
        val diagram = MermaidParser.parse(
            """
            flowchart LR
                A[Start] --> B(Process) --> C{Decision}
            """.trimIndent()
        )

        assertEquals(setOf("A", "B", "C"), diagram.nodes.map { it.id }.toSet())
        assertTrue(diagram.edges.any { it.fromId == "A" && it.toId == "B" }, "A->B")
        assertTrue(diagram.edges.any { it.fromId == "B" && it.toId == "C" }, "B->C — was dropped by MMD-5")
    }

    /** The app's own DIAGRAM insert template uses exactly this shape. */
    @Test
    fun `the built-in diagram template parses completely`() {
        val diagram = MermaidParser.parse(
            """
            flowchart LR
                A[Start] --> B(Process) --> C{Decision}
                C -->|Yes| D[Success]
                C -->|No| E[Retry]
            """.trimIndent()
        )

        assertEquals(5, diagram.nodes.size, "all five nodes should be present")
        assertTrue(diagram.edges.any { it.fromId == "C" && it.toId == "D" && it.label == "Yes" })
        assertTrue(diagram.edges.any { it.fromId == "C" && it.toId == "E" && it.label == "No" })
    }

    /** MMD-6: `(round)` preceded `((circle))` in the alternation, so CIRCLE was unreachable. */
    @Test
    fun `double-paren nodes parse as circles with a clean label`() {
        val diagram = MermaidParser.parse(
            """
            flowchart LR
                A((Round Start)) --> B[Next]
            """.trimIndent()
        )

        val circle = diagram.nodes.first { it.id == "A" }
        assertEquals(NodeShape.CIRCLE, circle.shape, "MMD-6: circle shape must be reachable")
        assertEquals("Round Start", circle.label, "MMD-6: label must not keep a stray paren")
    }

    @Test
    fun `bracket shapes map to the expected node shapes`() {
        val diagram = MermaidParser.parse(
            """
            flowchart TD
                A[Rect] --> B{Diamond}
                B --> C(Rounded)
            """.trimIndent()
        )

        assertEquals(NodeShape.DIAMOND, diagram.nodes.first { it.id == "B" }.shape)
        assertEquals(NodeShape.ROUNDED_RECT, diagram.nodes.first { it.id == "C" }.shape)
    }

    @Test
    fun `orientation is taken from the header`() {
        assertTrue(MermaidParser.parse("flowchart LR\n A --> B").isHorizontal)
        assertTrue(!MermaidParser.parse("flowchart TD\n A --> B").isHorizontal)
    }

    @Test
    fun `dashed arrows are marked dashed`() {
        val diagram = MermaidParser.parse("flowchart LR\n A -.-> B")
        assertTrue(diagram.edges.single().isDashed)
    }

    @Test
    fun `sequence diagrams are detected as their own type`() {
        val diagram = MermaidParser.parse(
            """
            sequenceDiagram
                Alice->>Bob: Hello
                Bob-->>Alice: Hi back
            """.trimIndent()
        )

        assertEquals("sequence", diagram.type)
        assertEquals(2, diagram.edges.size)
        assertTrue(diagram.edges[1].isDashed, "`-->>` is a dashed reply")
    }

    /**
     * Mermaid allows the edge label to sit *between* the dashes (`A -- text --> B`) as an
     * alternative to the pipe form (`A -->|text| B`). The parser dropped these edges
     * entirely, which orphaned every target node and broke the whole graph layout.
     */
    @Test
    fun `mid-arrow edge labels are captured`() {
        val diagram = MermaidParser.parse(
            """
            flowchart TD
                B{Decision} -- No --> L[Left]
                B -- Yes --> C[Right]
            """.trimIndent()
        )

        assertTrue(
            diagram.edges.any { it.fromId == "B" && it.toId == "L" && it.label == "No" },
            "B -- No --> L edge with label 'No'"
        )
        assertTrue(
            diagram.edges.any { it.fromId == "B" && it.toId == "C" && it.label == "Yes" },
            "B -- Yes --> C edge with label 'Yes'"
        )
    }

    /** The user's real deck: a TD chart mixing normal `-->` and `-- label -->` arrows. */
    @Test
    fun `real-world TD decision chart keeps every edge and node`() {
        val diagram = MermaidParser.parse(
            """
            flowchart TD
                A[New Select Invest front leg] --> B{RESL member and settles tomorrow?}
                B -- No --> L[Normal path]
                B -- Yes --> C[Standard validations]
                C --> D[Nominal and settlement-amount checks]
                D --> E[Cash Balance check SKIPPED]
                E --> F{Enough maturing cash in the bucket?}
                F -- No --> R[Reject code 3018]
                F -- Yes --> G[Accept draw the balance down reply to F7]
            """.trimIndent()
        )

        assertEquals(
            setOf("A", "B", "L", "C", "D", "E", "F", "R", "G"),
            diagram.nodes.map { it.id }.toSet(),
            "every node must be present"
        )
        // The edge into G was the one the user reported missing.
        assertTrue(diagram.edges.any { it.fromId == "F" && it.toId == "G" && it.label == "Yes" }, "F -- Yes --> G")
        assertTrue(diagram.edges.any { it.fromId == "F" && it.toId == "R" && it.label == "No" }, "F -- No --> R")
        assertTrue(diagram.edges.any { it.fromId == "A" && it.toId == "B" }, "A --> B")
        assertTrue(diagram.edges.any { it.fromId == "E" && it.toId == "F" }, "E --> F")
        assertEquals(8, diagram.edges.size, "all eight edges present")
    }

    /** `{{...}}` is Mermaid's hexagon node; the single-brace branch mangled the label. */
    @Test
    fun `double-brace hexagon nodes parse with a clean label`() {
        val diagram = MermaidParser.parse(
            """
            flowchart LR
                A[Start] -->|go| N{{Existing netting run}}
            """.trimIndent()
        )

        val hex = diagram.nodes.first { it.id == "N" }
        assertEquals("Existing netting run", hex.label, "label must not keep stray braces")
        assertTrue(diagram.edges.any { it.fromId == "A" && it.toId == "N" && it.label == "go" }, "A->N labelled 'go'")
    }
}
