package com.skaldoria.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.theme.PresentationTheme

/**
 * Parsed diagram model for native Compose visualization of Mermaid flowcharts,
 * sequence diagrams, and architecture graphs.
 */
data class DiagramNode(
    val id: String,
    val label: String,
    val shape: NodeShape = NodeShape.ROUNDED_RECT,
    val styleClass: String? = null
)

enum class NodeShape {
    RECTANGLE,
    ROUNDED_RECT,
    CIRCLE,
    DIAMOND,
    DATABASE
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
                    if (diagram.nodes.isEmpty()) {
                        Text(
                            text = code,
                            color = theme.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    } else if (diagram.type == "sequence") {
                        SequenceDiagramRenderer(diagram, theme)
                    } else {
                        FlowchartDiagramRenderer(diagram, theme)
                    }
                }
            }
        }
    }
}

/**
 * Finds the edge that actually joins [from] to [to].
 *
 * The previous predicate used `||`, which matched any edge merely *starting* at [from]
 * or *ending* at [to], so labels attached to the wrong connector (MMD-4).
 */
private fun ParsedDiagram.edgeBetween(from: DiagramNode, to: DiagramNode): DiagramEdge? =
    edges.find { it.fromId == from.id && it.toId == to.id }

/**
 * Renders the node chain, wrapping onto additional lines instead of running off the canvas.
 *
 * `FlowRow`/`FlowColumn` replace the plain `Row`/`Column`, which measured each child
 * against the *remaining* main-axis space — past roughly six nodes later children were
 * handed a maxWidth near zero, collapsed to a sliver, and were then clipped by the
 * surface's rounded-corner clip (OVF-2). Combined with `FitToCanvas` upstream, content
 * now wraps first and shrinks second.
 *
 * This is a containment fix, not the real thing: nodes are still emitted in parse order as
 * a linear chain, so branches and merges are drawn wrong, and a connector at the end of a
 * wrapped line points into empty space. MMD-1 replaces this with a layered graph layout —
 * expect to delete this function rather than extend it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowchartDiagramRenderer(
    diagram: ParsedDiagram,
    theme: PresentationTheme
) {
    val nodes = diagram.nodes
    if (diagram.isHorizontal) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.Center
        ) {
            FlowchartChain(diagram, nodes, isHorizontal = true, theme = theme)
        }
    } else {
        FlowColumn(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.Center
        ) {
            FlowchartChain(diagram, nodes, isHorizontal = false, theme = theme)
        }
    }
}

/**
 * The node/connector emission shared by both orientations, which previously existed as
 * two near-identical copies differing only in the `isHorizontal` flag.
 */
@Composable
private fun FlowchartChain(
    diagram: ParsedDiagram,
    nodes: List<DiagramNode>,
    isHorizontal: Boolean,
    theme: PresentationTheme
) {
    nodes.forEachIndexed { index, node ->
        NodeCard(node = node, theme = theme)

        val next = nodes.getOrNull(index + 1)
        if (next != null) {
            val edge = diagram.edgeBetween(node, next)
            ArrowConnector(
                label = edge?.label,
                isHorizontal = isHorizontal,
                isDashed = edge?.isDashed ?: false,
                theme = theme
            )
        }
    }
}

@Composable
private fun NodeCard(
    node: DiagramNode,
    theme: PresentationTheme
) {
    val shape = when (node.shape) {
        NodeShape.ROUNDED_RECT -> RoundedCornerShape(12.dp)
        NodeShape.RECTANGLE -> RoundedCornerShape(4.dp)
        NodeShape.CIRCLE -> CircleShape
        NodeShape.DIAMOND -> RoundedCornerShape(8.dp)
        NodeShape.DATABASE -> RoundedCornerShape(14.dp)
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

@Composable
private fun ArrowConnector(
    label: String?,
    isHorizontal: Boolean,
    isDashed: Boolean,
    theme: PresentationTheme
) {
    if (isHorizontal) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            if (label != null) {
                Surface(
                    color = theme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Text(
                        text = label,
                        color = theme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Canvas(modifier = Modifier.size(50.dp, 20.dp)) {
                val y = size.height / 2
                val startX = 0f
                val endX = size.width

                drawLine(
                    color = theme.primary,
                    start = Offset(startX, y),
                    end = Offset(endX, y),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )

                // Arrow head
                val arrowHead = Path().apply {
                    moveTo(endX, y)
                    lineTo(endX - 10f, y - 6f)
                    lineTo(endX - 10f, y + 6f)
                    close()
                }
                drawPath(arrowHead, color = theme.primary)
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            Canvas(modifier = Modifier.size(20.dp, 40.dp)) {
                val x = size.width / 2
                val startY = 0f
                val endY = size.height

                drawLine(
                    color = theme.primary,
                    start = Offset(x, startY),
                    end = Offset(x, endY),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )

                // Arrow head
                val arrowHead = Path().apply {
                    moveTo(x, endY)
                    lineTo(x - 6f, endY - 10f)
                    lineTo(x + 6f, endY - 10f)
                    close()
                }
                drawPath(arrowHead, color = theme.primary)
            }

            if (label != null) {
                Surface(
                    color = theme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = theme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SequenceDiagramRenderer(
    diagram: ParsedDiagram,
    theme: PresentationTheme
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Actors Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            diagram.nodes.forEach { actor ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = theme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.primary)
                ) {
                    Text(
                        text = actor.label,
                        color = theme.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Sequence Messages
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            diagram.edges.forEach { edge ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = theme.background.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${edge.fromId} ➔ ${edge.toId}",
                            color = theme.primary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = edge.label ?: "",
                            color = theme.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Actors Footer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            diagram.nodes.forEach { actor ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = theme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, theme.primary)
                ) {
                    Text(
                        text = actor.label,
                        color = theme.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
