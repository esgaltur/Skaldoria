package com.skaldoria.core.diagram

import androidx.compose.ui.unit.IntSize
import com.skaldoria.ui.components.MermaidParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MMD-10 — the two invariants that make a `subgraph` frame *honest*.
 *
 * A frame is the bounding box of its members, so it can lie in two ways, and both were
 * observed on screen before the layout reserved a cross-axis band per group:
 *  - it enclosed a node that was not a member (a group spanning several layers swallowed an
 *    unrelated node sitting between them);
 *  - it overlapped a neighbouring group's frame.
 *
 * These assert the geometry directly, so they fail without anyone having to look at a PNG.
 */
class SubgraphLayoutTest {

    /** Every node is given the same plausible size, so geometry is the only variable. */
    private fun sceneFor(mermaid: String, nodeWidth: Int = 120, nodeHeight: Int = 60): FlowchartScene {
        val diagram: ParsedDiagram = MermaidParser.parse(mermaid)
        val layout = FlowchartLayoutEngine.layout(
            nodeIds = diagram.nodes.map { it.id },
            edges = diagram.edges.map { it.fromId to it.toId }
        )
        return FlowchartScene.arrange(
            layout = layout,
            nodeIds = diagram.nodes.map { it.id },
            nodeSize = diagram.nodes.associate { it.id to IntSize(nodeWidth, nodeHeight) },
            edges = diagram.edges.map { DiagramEdge(it.fromId, it.toId, it.label, it.isDashed) },
            horizontal = diagram.isHorizontal,
            availableBounds = IntSize(1280, 720),
            labelWidths = emptyMap(),
            groupOf = diagram.groups.flatMap { g -> g.nodeIds.map { it to g.id } }.toMap(),
            groups = diagram.groups.map { Triple(it.id, it.title, it.nodeIds) }
        )
    }

    private fun assertFramesContainOnlyTheirMembers(scene: FlowchartScene, memberIds: Map<String, List<String>>) {
        for (group in scene.groups) {
            val members = memberIds.getValue(group.id).toSet()
            for ((nodeId, placement) in scene.nodes) {
                if (nodeId in members) continue
                assertTrue(
                    !group.rect.overlaps(placement.rect),
                    "frame '${group.title}' ${group.rect} encloses non-member '$nodeId' ${placement.rect}"
                )
            }
        }
    }

    private fun assertFramesDoNotOverlap(scene: FlowchartScene) {
        val frames = scene.groups
        for (i in frames.indices) {
            for (j in i + 1 until frames.size) {
                assertTrue(
                    !frames[i].rect.overlaps(frames[j].rect),
                    "frames '${frames[i].title}' and '${frames[j].title}' overlap: " +
                        "${frames[i].rect} vs ${frames[j].rect}"
                )
            }
        }
    }

    /**
     * A group spanning several layers with an unrelated node between its members. The frame
     * used to swallow `Free`.
     */
    @Test
    fun `a frame spanning layers does not enclose an unrelated node`() {
        val scene = sceneFor(
            """
            flowchart LR
                In[Ingress] --> A1[Auth]
                A1 --> Free[Unowned]
                Free --> A2[Audit]
                subgraph Sec [Security]
                    A1
                    A2
                end
            """.trimIndent()
        )

        assertEquals(1, scene.groups.size)
        assertFramesContainOnlyTheirMembers(scene, mapOf("Sec" to listOf("A1", "A2")))
    }

    /** Two groups across the same layers. Their frames used to overlap. */
    @Test
    fun `sibling group frames do not overlap`() {
        val scene = sceneFor(
            """
            flowchart LR
                Start[Start] --> X1[X one]
                Start --> Y1[Y one]
                X1 --> X2[X two]
                Y1 --> Y2[Y two]
                subgraph GX [Group X]
                    X1
                    X2
                end
                subgraph GY [Group Y]
                    Y1
                    Y2
                end
            """.trimIndent()
        )

        assertEquals(2, scene.groups.size)
        assertFramesDoNotOverlap(scene)
        assertFramesContainOnlyTheirMembers(
            scene,
            mapOf("GX" to listOf("X1", "X2"), "GY" to listOf("Y1", "Y2"))
        )
    }

    @Test
    fun `vertical flow keeps outside nodes outside the frame`() {
        val scene = sceneFor(
            """
            flowchart TD
                Req[Request] --> V[Validate]
                subgraph Proc [Processing]
                    V --> W[Work]
                    W --> S[Store]
                end
                S --> Res[Respond]
            """.trimIndent()
        )

        assertEquals(1, scene.groups.size)
        assertFramesContainOnlyTheirMembers(scene, mapOf("Proc" to listOf("V", "W", "S")))
    }

    @Test
    fun `nested subgraphs flatten into non-overlapping sibling frames`() {
        val scene = sceneFor(
            """
            flowchart LR
                subgraph Outer [Outer Layer]
                    A[A] --> B[B]
                    subgraph Inner [Inner Layer]
                        C[C] --> D[D]
                    end
                    B --> C
                end
            """.trimIndent()
        )

        assertEquals(2, scene.groups.size)
        assertFramesDoNotOverlap(scene)
        assertFramesContainOnlyTheirMembers(
            scene,
            mapOf("Outer" to listOf("A", "B"), "Inner" to listOf("C", "D"))
        )
    }

    /** Every member must actually be inside its own frame — the frame is their bounding box. */
    @Test
    fun `every member sits inside its own frame`() {
        val scene = sceneFor(
            """
            flowchart LR
                subgraph Backend [Backend Services]
                    API[Gateway] --> Svc[Service]
                    Svc --> DB[(Store)]
                end
                Client[Browser] --> API
            """.trimIndent()
        )

        val frame = scene.groups.single()
        listOf("API", "Svc", "DB").forEach { id ->
            val rect = scene.nodes.getValue(id).rect
            assertTrue(
                frame.rect.left <= rect.left && frame.rect.right >= rect.right &&
                    frame.rect.top <= rect.top && frame.rect.bottom >= rect.bottom,
                "member '$id' $rect is not inside its frame ${frame.rect}"
            )
        }
        assertFramesContainOnlyTheirMembers(scene, mapOf("Backend" to listOf("API", "Svc", "DB")))
    }

    /** A diagram with no subgraphs must lay out exactly as it did before bands existed. */
    @Test
    fun `a diagram without subgraphs is unaffected`() {
        val scene = sceneFor(
            """
            flowchart LR
                A[Alpha] --> B[Beta]
                B --> C[Gamma]
            """.trimIndent()
        )

        assertTrue(scene.groups.isEmpty())
        // One band means one row: every node shares the same vertical centre.
        val centres = scene.nodes.values.map { it.rect.center.y }.distinct()
        assertEquals(1, centres.size, "ungrouped nodes should stay on a single row: $centres")
    }

    // -----------------------------------------------------------------
    // Cases not covered by the first round — checked rather than assumed
    // -----------------------------------------------------------------

    /** Many groups stack into many bands; none of them may collide. */
    @Test
    fun `five groups all stay separate`() {
        val src = buildString {
            appendLine("flowchart LR")
            appendLine("    Root[Root]")
            repeat(5) { i ->
                appendLine("    Root --> N${i}a[Node ${i}a]")
                appendLine("    N${i}a --> N${i}b[Node ${i}b]")
                appendLine("    subgraph G$i [Group $i]")
                appendLine("        N${i}a")
                appendLine("        N${i}b")
                appendLine("    end")
            }
        }
        val scene = sceneFor(src)

        assertEquals(5, scene.groups.size)
        assertFramesDoNotOverlap(scene)
        assertFramesContainOnlyTheirMembers(
            scene,
            (0 until 5).associate { "G$it" to listOf("N${it}a", "N${it}b") }
        )
    }

    /** A member with no edges still has to sit inside its frame and exclude others. */
    @Test
    fun `an isolated member is framed correctly`() {
        val scene = sceneFor(
            """
            flowchart LR
                A[Alpha] --> B[Beta]
                subgraph Side [Side Notes]
                    Lonely[Standalone]
                end
            """.trimIndent()
        )

        assertEquals(1, scene.groups.size)
        assertFramesContainOnlyTheirMembers(scene, mapOf("Side" to listOf("Lonely")))
    }

    /** A cycle inside a group must not hang the layout or break the frame. */
    @Test
    fun `a cyclic group still lays out`() {
        val scene = sceneFor(
            """
            flowchart LR
                In[In] --> A[A]
                subgraph Loop [Retry Loop]
                    A --> B[B]
                    B --> C[C]
                    C --> A
                end
            """.trimIndent()
        )

        assertEquals(1, scene.groups.size)
        assertFramesContainOnlyTheirMembers(scene, mapOf("Loop" to listOf("A", "B", "C")))
    }

    /** An edge crossing into a group must not drag its label onto the frame border. */
    @Test
    fun `a labelled edge entering a group keeps its label outside the frame`() {
        val scene = sceneFor(
            """
            flowchart LR
                Client[Client] -->|authenticate| API[Gateway]
                subgraph Backend [Backend]
                    API --> Svc[Service]
                end
            """.trimIndent()
        )

        val frame = scene.groups.single()
        val entering = scene.edges.first { it.fromId == "Client" && it.toId == "API" }
        val box = entering.labelBox
        if (box != null) {
            val onBorder = kotlin.math.abs(box.center.x - frame.rect.left) < box.width / 2f &&
                box.center.y in frame.rect.top..frame.rect.bottom
            assertTrue(!onBorder, "edge label sits on the frame border at ${box.center}")
        }
    }
}
