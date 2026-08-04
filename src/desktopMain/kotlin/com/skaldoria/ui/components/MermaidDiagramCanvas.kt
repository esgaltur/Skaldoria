package com.skaldoria.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.diagram.SequenceDiagramParser
import com.skaldoria.theme.PresentationTheme

/**
 * Parsed diagram model for native Compose visualization of Mermaid flowcharts,
 * sequence diagrams, and architecture graphs.
 */
data class DiagramNode(
    val id: String,
    val label: String,
    val shape: NodeShape = NodeShape.ROUNDED_RECT
)

/**
 * Node shapes the parser can actually produce.
 *
 * DED-4: `RECTANGLE` and `DATABASE` were never emitted by any code path, and `CIRCLE` was
 * unreachable until MMD-6 fixed the alternation order. Removed rather than left as dead
 * branches implying support that does not exist — reinstate alongside the parser support
 * (`[[…]]`, `[(…)]`) if those forms are ever added. `DiagramNode.styleClass` went the same
 * way: nothing ever set it, since `classDef`/`class` are not parsed.
 */
enum class NodeShape {
    ROUNDED_RECT,
    CIRCLE,
    DIAMOND
}

data class DiagramEdge(
    val fromId: String,
    val toId: String,
    val label: String? = null,
    val isDashed: Boolean = false,
    val isBiDirectional: Boolean = false
)

data class ParsedDiagram(
    val type: String, // "flowchart", "sequence", "graph"
    val isHorizontal: Boolean = true,
    val nodes: List<DiagramNode>,
    val edges: List<DiagramEdge>
)

/**
 * Parses Mermaid flowchart and sequence definitions into structured renderable models.
 */
object MermaidParser {

    /** First non-blank capture among [groups], or null. Keeps label extraction readable. */
    private fun firstNonBlank(match: MatchResult, vararg groups: Int): String? =
        groups.asSequence()
            .map { match.groupValues.getOrElse(it) { "" } }
            .firstOrNull { it.isNotBlank() }

    /** Node shape implied by which bracket form matched. */
    private fun shapeOf(match: MatchResult, circleGroup: Int, roundGroup: Int, diamondGroup: Int): NodeShape = when {
        match.groupValues.getOrElse(diamondGroup) { "" }.isNotBlank() -> NodeShape.DIAMOND
        match.groupValues.getOrElse(circleGroup) { "" }.isNotBlank() -> NodeShape.CIRCLE
        match.groupValues.getOrElse(roundGroup) { "" }.isNotBlank() -> NodeShape.ROUNDED_RECT
        else -> NodeShape.ROUNDED_RECT
    }

    /**
     * A node reference: an id plus an optional bracketed label.
     *
     * MMD-6: `((circle))` is tried BEFORE `(round)`. Regex alternation is ordered, so with
     * the single-paren branch first, `A((Round))` matched it and captured `(Round` — which
     * made [NodeShape.CIRCLE] unreachable and left a stray paren in every circle label.
     */
    private val NODE_TOKEN = Regex("""\s*([A-Za-z0-9_]+)\s*(?:\[(.*?)\]|\(\((.*?)\)\)|\((.*?)\)|\{(.*?)\})?""")

    /** An arrow, with an optional `|label|`. Dashed and thick variants included. */
    private val ARROW_TOKEN = Regex("""\s*(-\.->|===>|==>|-{2,}>|-{3,}|-->)(?:\|(.*?)\|)?""")

    /**
     * Walks one flowchart line as `node (arrow node)*`, registering every node and edge.
     * Returns false when the line does not begin with a node reference.
     */
    private fun parseEdgeChain(
        line: String,
        nodesMap: MutableMap<String, DiagramNode>,
        edges: MutableList<DiagramEdge>
    ): Boolean {
        var position = 0

        fun readNode(): String? {
            val match = NODE_TOKEN.matchAt(line, position) ?: return null
            val id = match.groupValues[1]
            if (id.isBlank()) return null
            val label = firstNonBlank(match, 2, 3, 4, 5) ?: id
            val shape = shapeOf(match, circleGroup = 3, roundGroup = 4, diamondGroup = 5)
            // First declaration wins, so a later bare reference cannot erase a label.
            nodesMap.getOrPut(id) { DiagramNode(id, label, shape) }
            position = match.range.last + 1
            return id
        }

        var previousId = readNode() ?: return false
        var linked = false

        while (position < line.length) {
            val arrow = ARROW_TOKEN.matchAt(line, position) ?: break
            position = arrow.range.last + 1

            val nextId = readNode() ?: break
            edges.add(
                DiagramEdge(
                    fromId = previousId,
                    toId = nextId,
                    label = arrow.groupValues[2].ifBlank { null },
                    isDashed = arrow.groupValues[1].contains(".")
                )
            )
            previousId = nextId
            linked = true
        }

        return linked
    }

    fun parse(code: String): ParsedDiagram {
        val lines = code.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("%%") }
        if (lines.isEmpty()) {
            return ParsedDiagram("flowchart", true, emptyList(), emptyList())
        }

        val firstLine = lines.first().lowercase()
        val isHorizontal = firstLine.contains("lr") || firstLine.contains("right")
        val isSequence = firstLine.startsWith("sequence") || firstLine.contains("sequencediagram")

        val nodesMap = mutableMapOf<String, DiagramNode>()
        val edges = mutableListOf<DiagramEdge>()

        if (isSequence) {
            return parseSequenceDiagram(lines)
        }

        // Flowchart parsing
        for (line in lines.drop(1)) {
            // MMD-5: a line is an alternating chain of nodes and arrows, so it is scanned
            // left to right rather than matched pairwise. `findAll` with a two-node pattern
            // cannot work here — matches do not overlap, so the first match consumes `B` in
            // `A --> B --> C` and the second edge is silently lost.
            if (!parseEdgeChain(line, nodesMap, edges)) {
                // Standalone node: A[Label] or B(Text) — same alternation order as above.
                val nodeRegex = Regex("""([A-Za-z0-9_]+)\s*(?:\[(.*?)\]|\(\((.*?)\)\)|\((.*?)\)|\{(.*?)\})""")
                val nodeMatch = nodeRegex.find(line)
                if (nodeMatch != null) {
                    val id = nodeMatch.groupValues[1]
                    val label = firstNonBlank(nodeMatch, 2, 3, 4, 5) ?: id
                    val shape = shapeOf(nodeMatch, circleGroup = 3, roundGroup = 4, diamondGroup = 5)
                    nodesMap[id] = DiagramNode(id, label, shape)
                }
            }
        }

        // Fallback: If no nodes parsed, generate nodes from lines
        if (nodesMap.isEmpty()) {
            for ((idx, l) in lines.drop(1).withIndex()) {
                val clean = l.replace(Regex("[^a-zA-Z0-9 _-]"), " ").trim()
                if (clean.isNotBlank()) {
                    nodesMap["node_$idx"] = DiagramNode("node_$idx", clean)
                }
            }
        }

        return ParsedDiagram(
            type = "flowchart",
            isHorizontal = isHorizontal,
            nodes = nodesMap.values.toList(),
            edges = edges
        )
    }

    private fun parseSequenceDiagram(lines: List<String>): ParsedDiagram {
        val actors = mutableSetOf<String>()
        val edges = mutableListOf<DiagramEdge>()

        for (line in lines.drop(1)) {
            val seqRegex = Regex("""([A-Za-z0-9_]+)\s*(->>|-->>|->|-->)\s*([A-Za-z0-9_]+)\s*:\s*(.+)""")
            val match = seqRegex.find(line)
            if (match != null) {
                val from = match.groupValues[1]
                val arrow = match.groupValues[2]
                val to = match.groupValues[3]
                val message = match.groupValues[4]
                actors.add(from)
                actors.add(to)
                edges.add(
                    DiagramEdge(
                        fromId = from,
                        toId = to,
                        label = message,
                        isDashed = arrow.contains("--")
                    )
                )
            }
        }

        val nodes = actors.map { DiagramNode(it, it, NodeShape.ROUNDED_RECT) }
        return ParsedDiagram(
            type = "sequence",
            isHorizontal = true,
            nodes = nodes,
            edges = edges
        )
    }
}

/**
 * Visual Renderer for Mermaid Diagrams in Compose.
 */
@Composable
fun MermaidDiagramCanvas(
    code: String,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    var showRawCode by remember { mutableStateOf(false) }
    val diagram = remember(code) { MermaidParser.parse(code) }

    // MMD-2/MMD-3: sequence diagrams get their own model and renderer. The flowchart
    // node/edge model cannot express ordering or nesting, so reusing it dropped
    // participants, half the arrow types, and every block construct.
    val sequence = remember(code) {
        if (diagram.type == "sequence") SequenceDiagramParser.parse(code) else null
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp)),
        color = theme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar with Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(theme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = "Mermaid Diagram",
                        tint = theme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "MERMAID ARCHITECTURE DIAGRAM",
                        color = theme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                IconButton(
                    onClick = { showRawCode = !showRawCode },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Toggle Raw Mermaid Code",
                        tint = if (showRawCode) theme.primary else theme.textMuted
                    )
                }
            }

            if (showRawCode) {
                // Raw Code View
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F131C))
                        .padding(20.dp)
                ) {
                    Text(
                        text = code,
                        color = Color(0xFFE2E8F0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }
            } else {
                // Interactive Visual Diagram Canvas
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (sequence != null && !sequence.isEmpty) {
                        SequenceDiagramView(sequence, theme, Modifier.fillMaxSize())
                    } else if (diagram.nodes.isEmpty()) {
                        Text(
                            text = code,
                            color = theme.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    } else {
                        // MMD-1: laid out from the graph, not from parse order.
                        FlowchartGraphView(diagram, theme)
                    }
                }
            }
        }
    }
}

@Composable
internal fun NodeCard(
    node: DiagramNode,
    theme: PresentationTheme
) {
    val shape = when (node.shape) {
        NodeShape.ROUNDED_RECT -> RoundedCornerShape(12.dp)
        NodeShape.CIRCLE -> CircleShape
        NodeShape.DIAMOND -> RoundedCornerShape(8.dp)
    }

    Box(
        modifier = Modifier
            .shadow(10.dp, shape, spotColor = theme.primary.copy(alpha = 0.2f))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        theme.surfaceVariant,
                        theme.surface
                    )
                )
            )
            .border(1.5.dp, theme.primary.copy(alpha = 0.8f), shape)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = node.label,
                color = theme.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (node.id != node.label && node.id.isNotBlank()) {
                Text(
                    text = node.id,
                    color = theme.textMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
