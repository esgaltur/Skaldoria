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
import androidx.compose.ui.text.style.TextOverflow
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
 * DED-4 removed shapes that no code path could emit rather than leave dead branches implying
 * support that did not exist. `DATABASE` is back because MMD-9 added `[(cylinder)]` parsing —
 * the rule being that a shape exists here only when the parser can actually produce it.
 */
enum class NodeShape {
    ROUNDED_RECT,
    CIRCLE,
    DIAMOND,
    /** `A[(Label)]` — a datastore. Reinstated once the parser could actually emit it. */
    DATABASE
}

data class DiagramEdge(
    val fromId: String,
    val toId: String,
    val label: String? = null,
    val isDashed: Boolean = false,
    val isBiDirectional: Boolean = false
)

/**
 * A `subgraph … end` cluster: a titled frame drawn around the nodes declared inside it.
 *
 * Nested subgraphs are flattened — a node belongs to the innermost group that declared it —
 * because the layered layout has no notion of nested containers. The frame still reads
 * correctly; only the nesting relationship is lost.
 */
data class DiagramGroup(
    val id: String,
    val title: String,
    val nodeIds: List<String>
)

data class ParsedDiagram(
    val type: String, // "flowchart", "sequence", "graph"
    val isHorizontal: Boolean = true,
    val nodes: List<DiagramNode>,
    val edges: List<DiagramEdge>,
    val groups: List<DiagramGroup> = emptyList()
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
    private fun shapeOf(
        match: MatchResult,
        circleGroup: Int,
        roundGroup: Int,
        diamondGroup: Int,
        hexagonGroup: Int = -1,
        cylinderGroup: Int = -1
    ): NodeShape = when {
        cylinderGroup >= 0 && match.groupValues.getOrElse(cylinderGroup) { "" }.isNotBlank() -> NodeShape.DATABASE
        match.groupValues.getOrElse(diamondGroup) { "" }.isNotBlank() -> NodeShape.DIAMOND
        // `{{hexagon}}` has no dedicated shape yet; the diamond rendering is the closest
        // visually-distinct match, and it keeps the label clean instead of mangling it.
        hexagonGroup >= 0 && match.groupValues.getOrElse(hexagonGroup) { "" }.isNotBlank() -> NodeShape.DIAMOND
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
     *
     * MMD-7: `{{hexagon}}` is tried BEFORE `{diamond}` for the same reason — otherwise
     * `N{{Netting}}` matched the single-brace branch, captured `{Netting`, and left a stray
     * `}` that broke the rest of the line.
     *
     * MMD-9: `[(cylinder)]` is tried BEFORE `[rect]` for the same reason — otherwise
     * `DB[(Database)]` matched the square-bracket branch and captured `(Database`, leaving the
     * parens in the visible label.
     *
     * Groups: 1=id, 2=`[(cylinder)]`, 3=`[rect]`, 4=`((circle))`, 5=`(round)`,
     * 6=`{{hexagon}}`, 7=`{diamond}`.
     */
    private val NODE_TOKEN =
        Regex("""\s*([A-Za-z0-9_]+)\s*(?:\[\((.*?)\)\]|\[(.*?)\]|\(\((.*?)\)\)|\((.*?)\)|\{\{(.*?)\}\}|\{(.*?)\})?""")

    /** An arrow with a trailing `|label|`, e.g. `A -->|yes| B`. Dashed/thick variants included. */
    private val ARROW_TOKEN = Regex("""\s*(-\.->|===>|==>|-{2,}>|-{3,}|-->)(?:\|(.*?)\|)?""")

    /**
     * Mermaid's *mid-link* label form, where the text sits between the dashes rather than in
     * a `|…|`: `A -- yes --> B`, `A == x ==> B`, `A -. x .-> B`. This was unsupported, so
     * every such edge was silently dropped — which orphaned the target node and wrecked the
     * layout. Tried only after [ARROW_TOKEN] fails, so plain `-->`/`-->|…|` still win.
     *
     * Groups: 1=opener (`--`/`==`/`-.`), 2=label, 3=closer (`-->`/`==>`/`.->`/`---`).
     */
    private val ARROW_MIDLABEL_TOKEN =
        Regex("""\s*(--|==|-\.)\s*(.+?)\s*(-\.->|===>|==>|-{2,}>|\.->)""")

    /**
     * `subgraph Backend`, `subgraph id [Title]`, or `subgraph "Quoted Title"`.
     *
     * Groups: 1=id, 2=`[Title]`, 3=`"Title"`.
     */
    private val SUBGRAPH_START =
        Regex("""^\s*subgraph\s+([A-Za-z0-9_]*)\s*(?:\[(.*?)\]|"(.*?)")?\s*$""", RegexOption.IGNORE_CASE)

    /** Closes a `subgraph` block. */
    private val BLOCK_END = Regex("""^end$""", RegexOption.IGNORE_CASE)

    /**
     * Styling and interaction statements. They contribute no nodes or edges, and matching them
     * here is what stops the node scanner registering `classDef`, `class` or `style` as ids.
     */
    private val IGNORED_DIRECTIVE =
        Regex("""^\s*(classDef|class|style|linkStyle|click|direction)\b""", RegexOption.IGNORE_CASE)

    /** Accumulates a subgraph's members while its block is open. */
    private class MutableGroup(val id: String, val title: String) {
        val nodeIds = mutableListOf<String>()
    }

    /** Resolved arrow: the label (or null) plus whether it is dashed, and where it ends. */
    private data class ArrowMatch(val label: String?, val isDashed: Boolean, val end: Int)

    /** Matches either arrow form at [position], preferring the standard `|label|` form. */
    private fun matchArrow(line: String, position: Int): ArrowMatch? {
        ARROW_TOKEN.matchAt(line, position)?.let { m ->
            return ArrowMatch(
                label = m.groupValues[2].ifBlank { null },
                isDashed = m.groupValues[1].contains("."),
                end = m.range.last + 1
            )
        }
        ARROW_MIDLABEL_TOKEN.matchAt(line, position)?.let { m ->
            val opener = m.groupValues[1]
            val closer = m.groupValues[3]
            return ArrowMatch(
                label = m.groupValues[2].ifBlank { null },
                isDashed = opener.contains(".") || closer.contains("."),
                end = m.range.last + 1
            )
        }
        return null
    }

    /**
     * Walks one flowchart line as `node (arrow node)*`, registering every node and edge.
     * Returns false when the line does not begin with a node reference.
     */
    private fun parseEdgeChain(
        line: String,
        nodesMap: MutableMap<String, DiagramNode>,
        edges: MutableList<DiagramEdge>,
        /** Every id this line referred to — including ones that already existed. */
        mentioned: MutableList<String> = mutableListOf()
    ): Boolean {
        var position = 0

        fun readNode(): String? {
            val match = NODE_TOKEN.matchAt(line, position) ?: return null
            val id = match.groupValues[1]
            if (id.isBlank()) return null
            mentioned.add(id)
            val label = firstNonBlank(match, 2, 3, 4, 5, 6, 7) ?: id
            val shape = shapeOf(
                match,
                circleGroup = 4, roundGroup = 5, diamondGroup = 7,
                hexagonGroup = 6, cylinderGroup = 2
            )
            // First declaration wins, so a later bare reference cannot erase a label.
            nodesMap.getOrPut(id) { DiagramNode(id, label, shape) }
            position = match.range.last + 1
            return id
        }

        var previousId = readNode() ?: return false
        var linked = false

        while (position < line.length) {
            val arrow = matchArrow(line, position) ?: break
            position = arrow.end

            val nextId = readNode() ?: break
            edges.add(
                DiagramEdge(
                    fromId = previousId,
                    toId = nextId,
                    label = arrow.label,
                    isDashed = arrow.isDashed
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
        val groups = mutableListOf<DiagramGroup>()
        val openGroups = ArrayDeque<MutableGroup>()
        val assignedToGroup = mutableSetOf<String>()

        for (line in lines.drop(1)) {
            // MMD-10: keyword lines must be consumed *before* node scanning. `readNode`
            // registers an id as soon as it matches, so `subgraph Backend`, `end`,
            // `classDef …` and `class …` were each creating a phantom node — a diagram using
            // subgraphs rendered four boxes that do not exist in the source.
            val subgraphMatch = SUBGRAPH_START.find(line)
            if (subgraphMatch != null) {
                val rawId = subgraphMatch.groupValues[1].trim()
                val bracketTitle = firstNonBlank(subgraphMatch, 2, 3)
                openGroups.addLast(
                    MutableGroup(
                        id = rawId.ifBlank { "group_${groups.size + openGroups.size}" },
                        title = (bracketTitle ?: rawId).trim().trim('"')
                    )
                )
                continue
            }

            if (BLOCK_END.matches(line.trim())) {
                openGroups.removeLastOrNull()?.let { finished ->
                    // Empty subgraphs would draw an empty frame, so they are dropped.
                    if (finished.nodeIds.isNotEmpty()) {
                        groups.add(DiagramGroup(finished.id, finished.title, finished.nodeIds.toList()))
                    }
                }
                continue
            }

            // Styling and interaction directives carry no geometry; skipping them is what
            // stops `classDef`/`class`/`style`/`click` becoming nodes.
            if (IGNORED_DIRECTIVE.containsMatchIn(line)) continue

            val mentioned = mutableListOf<String>()

            if (!parseEdgeChain(line, nodesMap, edges, mentioned)) {
                // Standalone node: A[Label] or B(Text) — same alternation order as NODE_TOKEN.
                val nodeMatch = NODE_TOKEN.find(line)
                if (nodeMatch != null && nodeMatch.groupValues[1].isNotBlank()) {
                    val id = nodeMatch.groupValues[1]
                    mentioned.add(id)
                    val label = firstNonBlank(nodeMatch, 2, 3, 4, 5, 6, 7) ?: id
                    val shape = shapeOf(
                        nodeMatch,
                        circleGroup = 4, roundGroup = 5, diamondGroup = 7,
                        hexagonGroup = 6, cylinderGroup = 2
                    )
                    nodesMap[id] = DiagramNode(id, label, shape)
                }
            }

            // Membership follows *mention*, not creation. A subgraph body most often just
            // lists ids that were declared earlier (`A1` on its own line, or `A1 --> A2`);
            // capturing only newly-created nodes meant those groups came out empty and were
            // dropped, so the subgraph disappeared entirely.
            //
            // First group to mention a node wins, matching Mermaid: a node belongs to the
            // subgraph it is declared in, and a later reference does not move it.
            openGroups.lastOrNull()?.let { current ->
                mentioned.forEach { id ->
                    if (id in nodesMap && assignedToGroup.add(id)) current.nodeIds.add(id)
                }
            }
        }

        // Unterminated subgraphs still render, rather than being discarded on a typo.
        while (openGroups.isNotEmpty()) {
            openGroups.removeLast().let { finished ->
                if (finished.nodeIds.isNotEmpty()) {
                    groups.add(DiagramGroup(finished.id, finished.title, finished.nodeIds.toList()))
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
            edges = edges,
            groups = groups
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

    val diagramTypeLabel = remember(sequence, diagram.type) {
        when {
            sequence != null && !sequence.isEmpty -> "SEQUENCE DIAGRAM"
            diagram.type == "flowchart" -> "ARCHITECTURE FLOWCHART"
            else -> "MERMAID DIAGRAM"
        }
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
                        text = diagramTypeLabel,
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
                        // FitToCanvas is safe here — FlowchartGraphView measures its nodes
                        // at intrinsic size and reports its own natural bounds, so there is
                        // no weight/fill child to collapse under unbounded height.
                        FitToCanvas(modifier = Modifier.fillMaxSize()) {
                            FlowchartGraphView(diagram, theme)
                        }
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
        // A datastore reads as a cylinder: heavily rounded on the flow axis, flat elsewhere.
        NodeShape.DATABASE -> RoundedCornerShape(topStartPercent = 40, topEndPercent = 40, bottomStartPercent = 40, bottomEndPercent = 40)
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
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (node.id != node.label && node.id.isNotBlank()) {
                Text(
                    text = node.id,
                    color = theme.textMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
