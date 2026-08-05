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

    // -----------------------------------------------------------------
    // MMD-10 — subgraphs, and keywords that must never become nodes
    // -----------------------------------------------------------------

    /**
     * Keyword lines were being registered as nodes, because `readNode` claims an id the moment
     * it matches. A diagram using subgraphs rendered phantom `subgraph`, `end`, `classDef` and
     * `class` boxes that appear nowhere in the source.
     */
    @Test
    fun `subgraph and styling keywords never become nodes`() {
        val diagram = MermaidParser.parse(
            """
            flowchart LR
                Client[Browser] --> API[Gateway]
                subgraph Backend [Backend Services]
                    API --> Svc[Service]
                end
                classDef hot fill:#f9f
                class API hot
                style Svc stroke:#333
                linkStyle 0 stroke:#f00
                click API "https://example.com"
            """.trimIndent()
        )

        val ids = diagram.nodes.map { it.id }
        listOf("subgraph", "end", "classDef", "class", "style", "linkStyle", "click").forEach {
            assertTrue(it !in ids, "'$it' is a keyword, not a node — got $ids")
        }
        assertEquals(setOf("Client", "API", "Svc"), ids.toSet())
    }

    @Test
    fun `subgraph members are grouped with the declared title`() {
        val diagram = MermaidParser.parse(
            """
            flowchart LR
                Client[Browser] --> API[Gateway]
                subgraph Backend [Backend Services]
                    API --> Svc[Service]
                    Svc --> DB[(Store)]
                end
            """.trimIndent()
        )

        val group = diagram.groups.single()
        assertEquals("Backend", group.id)
        assertEquals("Backend Services", group.title)
        assertTrue("Svc" in group.nodeIds && "DB" in group.nodeIds, "members: ${group.nodeIds}")
        assertTrue("Client" !in group.nodeIds, "a node declared outside must not join the group")
    }

    @Test
    fun `a subgraph without a bracketed title uses its id`() {
        val diagram = MermaidParser.parse(
            """
            flowchart TD
                subgraph Infra
                    A[Node] --> B[Other]
                end
            """.trimIndent()
        )

        assertEquals("Infra", diagram.groups.single().title)
    }

    @Test
    fun `multiple subgraphs are captured separately`() {
        val diagram = MermaidParser.parse(
            """
            flowchart LR
                subgraph One [First]
                    A[A] --> B[B]
                end
                subgraph Two [Second]
                    C[C] --> D[D]
                end
            """.trimIndent()
        )

        assertEquals(2, diagram.groups.size)
        assertEquals(listOf("First", "Second"), diagram.groups.map { it.title }.sorted())
        assertEquals(4, diagram.nodes.size)
    }

    /** A missing `end` should still render the group rather than discard it. */
    @Test
    fun `an unterminated subgraph is still captured`() {
        val diagram = MermaidParser.parse(
            """
            flowchart LR
                subgraph Backend
                    A[A] --> B[B]
            """.trimIndent()
        )

        assertEquals(1, diagram.groups.size)
        assertEquals(2, diagram.groups.single().nodeIds.size)
    }

    @Test
    fun `an empty subgraph produces no frame`() {
        val diagram = MermaidParser.parse(
            """
            flowchart LR
                subgraph Empty
                end
                A[A] --> B[B]
            """.trimIndent()
        )
        assertTrue(diagram.groups.isEmpty(), "an empty group would draw an empty box")
    }

    /**
     * Bidirectional arrows (`<-->`, `<==>`, `<-.->`) were unsupported: the arrow scanner
     * failed on the leading `<`, so the edge chain broke *before* reading the second node.
     * In `B1[Buyer] <--> S1[Seller]` the target `S1` was never registered — the "no S1
     * defined" symptom — and every downstream node on the line was lost too.
     */
    @Test
    fun `bidirectional arrows register both endpoints and preserve labels`() {
        val diagram = MermaidParser.parse(
            """
            flowchart LR
                subgraph Before[Before - bilateral]
                    B1[Buyer] <--> S1[Seller]
                end
                subgraph After[After - two cleared trades]
                    B2[Buyer] <--> CCP{{Eurex Clearing AG}} <--> S2[Seller]
                end
                S1 ==>|novation| CCP
            """.trimIndent()
        )

        assertEquals(
            setOf("B1", "S1", "B2", "CCP", "S2"),
            diagram.nodes.map { it.id }.toSet(),
            "every endpoint of a bidirectional link must be registered"
        )
        assertEquals("Seller", diagram.nodes.first { it.id == "S1" }.label, "label after `<-->` must survive")
        assertEquals("Eurex Clearing AG", diagram.nodes.first { it.id == "CCP" }.label)
        assertEquals("Seller", diagram.nodes.first { it.id == "S2" }.label)

        assertTrue(diagram.edges.any { it.fromId == "B1" && it.toId == "S1" }, "B1 <--> S1")
        assertTrue(diagram.edges.any { it.fromId == "B2" && it.toId == "CCP" }, "B2 <--> CCP")
        assertTrue(diagram.edges.any { it.fromId == "CCP" && it.toId == "S2" }, "CCP <--> S2")
        assertTrue(
            diagram.edges.any { it.fromId == "S1" && it.toId == "CCP" && it.label == "novation" },
            "cross-subgraph novation edge S1 ==>|novation| CCP"
        )

        assertTrue("S1" in diagram.groups.first { it.id == "Before" }.nodeIds, "S1 belongs to Before")
        assertTrue(
            setOf("B2", "CCP", "S2").all { it in diagram.groups.first { g -> g.id == "After" }.nodeIds },
            "the After panel must contain all three of its members"
        )
    }

    /** Thick (`<==>`) and dashed (`<-.->`) bidirectional variants must parse too. */
    @Test
    fun `bidirectional thick and dashed arrows are supported`() {
        val diagram = MermaidParser.parse(
            """
            flowchart LR
                A[A] <==> B[B]
                C[C] <-.-> D[D]
            """.trimIndent()
        )

        assertEquals(setOf("A", "B", "C", "D"), diagram.nodes.map { it.id }.toSet())
        assertTrue(diagram.edges.any { it.fromId == "A" && it.toId == "B" }, "A <==> B")
        val dashed = diagram.edges.first { it.fromId == "C" && it.toId == "D" }
        assertTrue(dashed.isDashed, "`<-.->` is a dashed bidirectional link")
    }

    /**
     * The novation deck slide. Linking the subgraph *ids* (`Before ==> After`) used to spawn
     * phantom `Before`/`After` boxes, so the arrow is anchored to an inner node (`S1 ==> B2`).
     * This asserts the panel members are grouped, the novation edge is kept, and no phantom
     * node named after a subgraph leaks into the graph.
     */
    @Test
    fun `novation slide keeps panels and routes novation between inner nodes`() {
        val diagram = MermaidParser.parse(
            """
            flowchart TB
                subgraph Before[Before - bilateral]
                    B1[Buyer] --- S1[Seller]
                end
                subgraph After[After - Eurex Clearing is the CCP]
                    B2[Buyer] --> CCP{{Eurex Clearing AG}} --> S2[Seller]
                end
                S1 ==>|novation| B2
            """.trimIndent()
        )

        assertEquals(
            setOf("B1", "S1", "B2", "CCP", "S2"),
            diagram.nodes.map { it.id }.toSet(),
            "exactly the five real nodes — no phantom `Before`/`After` boxes"
        )
        assertTrue("Before" !in diagram.nodes.map { it.id }, "subgraph id must not become a node")
        assertTrue("After" !in diagram.nodes.map { it.id }, "subgraph id must not become a node")

        assertTrue(diagram.edges.any { it.fromId == "B1" && it.toId == "S1" }, "bilateral B1 --- S1")
        assertTrue(diagram.edges.any { it.fromId == "B2" && it.toId == "CCP" }, "B2 --> CCP")
        assertTrue(diagram.edges.any { it.fromId == "CCP" && it.toId == "S2" }, "CCP --> S2")
        assertTrue(
            diagram.edges.any { it.fromId == "S1" && it.toId == "B2" && it.label == "novation" },
            "novation routed from the seller side into the After panel"
        )

        assertTrue("S1" in diagram.groups.first { it.id == "Before" }.nodeIds, "S1 in Before panel")
        assertTrue(
            setOf("B2", "CCP", "S2").all { it in diagram.groups.first { g -> g.id == "After" }.nodeIds },
            "the After panel holds all three cleared-trade nodes"
        )
    }

    /** MMD-9: `[(cylinder)]` must win over `[rect]`, or the label keeps its parens. */
    @Test
    fun `cylinder nodes parse with a clean label and datastore shape`() {
        val diagram = MermaidParser.parse(
            """
            flowchart LR
                Svc[Service] --> DB[(Order Store)]
            """.trimIndent()
        )

        val db = diagram.nodes.first { it.id == "DB" }
        assertEquals("Order Store", db.label, "parens must not survive into the label")
        assertEquals(NodeShape.DATABASE, db.shape)
    }
}
