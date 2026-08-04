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
            // Check for edge patterns: A --> B, A -->|Label| B, A -.-> B, A ==> B, A --- B
            val edgeRegex = Regex("""([A-Za-z0-9_]+)\s*(?:\[(.*?)\]|\((.*?)\)|\{(.*?)\}|\(\((.*?)\)\))?\s*(-+>|==>|-\.->|---|-->)(?:\|(.*?)\|)?\s*([A-Za-z0-9_]+)\s*(?:\[(.*?)\]|\((.*?)\)|\{(.*?)\}|\(\((.*?)\)\))?""")
            val match = edgeRegex.find(line)

            if (match != null) {
                val fromId = match.groupValues[1]
                val fromLabel = (match.groupValues[2].ifBlank { match.groupValues[3] }.ifBlank { match.groupValues[4] }.ifBlank { match.groupValues[5] }).ifBlank { fromId }
                val arrow = match.groupValues[6]
                val edgeLabel = match.groupValues[7].ifBlank { null }
                val toId = match.groupValues[8]
                val toLabel = (match.groupValues[9].ifBlank { match.groupValues[10] }.ifBlank { match.groupValues[11] }.ifBlank { match.groupValues[12] }).ifBlank { toId }

                val fromShape = when {
                    match.groupValues[4].isNotBlank() -> NodeShape.DIAMOND
                    match.groupValues[5].isNotBlank() -> NodeShape.CIRCLE
                    match.groupValues[3].isNotBlank() -> NodeShape.ROUNDED_RECT
                    else -> NodeShape.ROUNDED_RECT
                }
                val toShape = when {
                    match.groupValues[11].isNotBlank() -> NodeShape.DIAMOND
                    match.groupValues[12].isNotBlank() -> NodeShape.CIRCLE
                    match.groupValues[10].isNotBlank() -> NodeShape.ROUNDED_RECT
                    else -> NodeShape.ROUNDED_RECT
                }

                if (!nodesMap.containsKey(fromId)) {
                    nodesMap[fromId] = DiagramNode(fromId, fromLabel, fromShape)
                }
                if (!nodesMap.containsKey(toId)) {
                    nodesMap[toId] = DiagramNode(toId, toLabel, toShape)
                }

                edges.add(
                    DiagramEdge(
                        fromId = fromId,
                        toId = toId,
                        label = edgeLabel,
                        isDashed = arrow.contains(".")
                    )
                )
            } else {
                // Standalone node: A[Label] or B(Text)
                val nodeRegex = Regex("""([A-Za-z0-9_]+)\s*(?:\[(.*?)\]|\((.*?)\)|\{(.*?)\}|\(\((.*?)\)\))""")
                val nodeMatch = nodeRegex.find(line)
                if (nodeMatch != null) {
                    val id = nodeMatch.groupValues[1]
                    val label = (nodeMatch.groupValues[2].ifBlank { nodeMatch.groupValues[3] }.ifBlank { nodeMatch.groupValues[4] }.ifBlank { nodeMatch.groupValues[5] }).ifBlank { id }
                    val shape = when {
                        nodeMatch.groupValues[4].isNotBlank() -> NodeShape.DIAMOND
                        nodeMatch.groupValues[5].isNotBlank() -> NodeShape.CIRCLE
                        nodeMatch.groupValues[3].isNotBlank() -> NodeShape.ROUNDED_RECT
                        else -> NodeShape.ROUNDED_RECT
                    }
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

@Composable
private fun FlowchartDiagramRenderer(
    diagram: ParsedDiagram,
    theme: PresentationTheme
) {
    val nodes = diagram.nodes
    val isHorizontal = diagram.isHorizontal

    if (isHorizontal) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            nodes.forEachIndexed { index, node ->
                NodeCard(node = node, theme = theme)

                if (index < nodes.size - 1) {
                    val matchingEdge = diagram.edges.find {
                        it.fromId == node.id || it.toId == nodes[index + 1].id
                    }
                    ArrowConnector(
                        label = matchingEdge?.label,
                        isHorizontal = true,
                        isDashed = matchingEdge?.isDashed ?: false,
                        theme = theme
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            nodes.forEachIndexed { index, node ->
                NodeCard(node = node, theme = theme)

                if (index < nodes.size - 1) {
                    val matchingEdge = diagram.edges.find {
                        it.fromId == node.id || it.toId == nodes[index + 1].id
                    }
                    ArrowConnector(
                        label = matchingEdge?.label,
                        isHorizontal = false,
                        isDashed = matchingEdge?.isDashed ?: false,
                        theme = theme
                    )
                }
            }
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
