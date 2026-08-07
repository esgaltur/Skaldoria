package com.skaldoria.ui.components

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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.diagram.FlowDirection
import com.skaldoria.core.diagram.SequenceDiagram
import com.skaldoria.core.diagram.SequenceDiagramParser
import com.skaldoria.theme.PresentationTheme
import com.skaldoria.ui.components.MermaidParser.ARROW_TOKEN
import com.skaldoria.ui.components.MermaidParser.NODE_BRACKETS
import com.skaldoria.ui.components.MermaidParser.NODE_TOKEN
import com.skaldoria.ui.components.MermaidParser.parseEdgeChain
import com.skaldoria.ui.components.MermaidParser.shapeOf

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

// DED-10: `isBiDirectional` was declared here and never set by the parser nor read by any
// renderer — a flag describing support that does not exist. Removed under the rule stated for
// `NodeShape` directly above: a property exists here only when the parser can actually produce
// it. Mermaid's `<-->` is not parsed today; if it ever is, the flag comes back with the code
// that emits and draws it.
data class DiagramEdge(
    val fromId: String,
    val toId: String,
    val label: String? = null,
    val isDashed: Boolean = false
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
    /** DIA-08: all four Mermaid directions, not just the two an axis flag could express. */
    val direction: FlowDirection = FlowDirection.DEFAULT,
    val nodes: List<DiagramNode>,
    val edges: List<DiagramEdge>,
    val groups: List<DiagramGroup> = emptyList()
) {
    /** Kept so every existing renderer call site reads the same; now derived, not stored. */
    val isHorizontal: Boolean get() = direction.isHorizontal
}

/**
 * Parses Mermaid flowchart and sequence definitions into structured renderable models.
 */
object MermaidParser {

    /** First non-blank capture among [groups], or null. Keeps label extraction readable. */
    private fun firstNonBlank(match: MatchResult, vararg groups: Int): String? =
        groups.asSequence()
            .map { match.groupValues.getOrElse(it) { "" } }
            .firstOrNull { it.isNotBlank() }

    /** Matches a Mermaid `<br>` line break in any of its written forms: `<br>`, `<br/>`, `<br />`. */
    private val BR_TAG = Regex("""<\s*br\s*/?\s*>""", RegexOption.IGNORE_CASE)

    /**
     * Normalises a captured label into what should actually be drawn.
     *
     * Two things authors write are markup, not text, and must not survive into the node:
     *  - a surrounding pair of quotes (`["…"]`), Mermaid's way of allowing special characters
     *    in a label — the quotes delimit, they are not part of the text;
     *  - `<br/>` (and `<br>` / `<br />`), an explicit line break — rendered literally it read
     *    as the tag itself instead of splitting the label across lines.
     *
     * Centralised here so every capture site — node labels and edge labels alike — cleans a
     * label the same way, rather than each re-deriving it.
     */
    private fun cleanLabel(raw: String): String =
        BR_TAG.replace(raw.trim().removeSurrounding("\""), "\n").trim()

    /**
     * The bracket forms a node label can wear, each paired with the shape it implies.
     *
     * Order is significant and load-bearing: a two-character delimiter must come *before* the
     * one-character delimiter it starts with, because regex alternation is ordered. If `[rect]`
     * were tried before `[(cylinder)]`, `DB[(Database)]` would match the square-bracket branch,
     * capture `(Database`, and leave the parens in the label (MMD-9); the same reasoning fixed
     * `((circle))` vs `(round)` (MMD-6) and `{{hexagon}}` vs `{diamond}` (MMD-7).
     *
     * Keeping the pairs as data — rather than one hand-written mega-alternation — lets
     * [NODE_TOKEN] be assembled from them and [shapeOf] read the shape back by position, so the
     * open/close/shape triples can never drift out of step. Each pair keeps its *own* closing
     * bracket, which is what lets a label hold inner parens (`EA["EA (MT527)"]`) without the
     * lazy body stopping at the first stray `)`.
     *
     * `{{hexagon}}` has no dedicated shape; diamond is the closest visually-distinct match.
     */
    private data class NodeBracket(val open: String, val close: String, val shape: NodeShape)

    private val NODE_BRACKETS = listOf(
        NodeBracket("""\[\(""", """\)\]""", NodeShape.DATABASE),      // [( cylinder )]
        NodeBracket("""\[""", """\]""", NodeShape.ROUNDED_RECT),      // [ rect ]
        NodeBracket("""\(\(""", """\)\)""", NodeShape.CIRCLE),        // (( circle ))
        NodeBracket("""\(""", """\)""", NodeShape.ROUNDED_RECT),      // ( round )
        NodeBracket("""\{\{""", """\}\}""", NodeShape.DIAMOND),       // {{ hexagon }}
        NodeBracket("""\{""", """\}""", NodeShape.DIAMOND)            // { diamond }
    )

    /** The 1-based capture group carrying the label for [NODE_BRACKETS]`[index]`. */
    private fun labelGroupOf(index: Int) = index + 2

    /**
     * A node reference: an id (group 1) and an optional bracketed label, one capture group per
     * entry in [NODE_BRACKETS] (groups 2..). Built from the table so it can never disagree with
     * [shapeOf]; see [NodeBracket] for why the order matters.
     */
    private val NODE_TOKEN = Regex(
        """\s*(\w+)\s*(?:""" +
            NODE_BRACKETS.joinToString("|") { "${it.open}(.*?)${it.close}" } +
            ")?"
    )

    /** Node shape implied by whichever [NODE_BRACKETS] label group actually matched. */
    private fun shapeOf(match: MatchResult): NodeShape {
        NODE_BRACKETS.forEachIndexed { index, bracket ->
            if (match.groupValues.getOrElse(labelGroupOf(index)) { "" }.isNotBlank()) return bracket.shape
        }
        return NodeShape.ROUNDED_RECT
    }

    /** The label captured by whichever [NODE_BRACKETS] form matched, cleaned, or null. */
    private fun labelOf(match: MatchResult): String? =
        NODE_BRACKETS.indices
            .firstNotNullOfOrNull { match.groupValues.getOrElse(labelGroupOf(it)) { "" }.ifBlank { null } }
            ?.let(::cleanLabel)

    /** An arrow with a trailing `|label|`, e.g. `A -->|yes| B`. Dashed/thick variants included.
     *  A leading `<` (`<-->`, `<==>`, `<-.->`) marks a *bidirectional* link; it is accepted so
     *  the scanner does not stall on the `<` and silently drop the second node of the pair.
     *
     *  The `<?` prefix is factored out of the arrowhead forms, and `={2,}>`/`-{2,}>` fold the
     *  two/three-symbol variants (`==>`/`===>`, `-->`/`--->`) into one branch each — same
     *  language, lower regex complexity. `-{3,}` is the plain (headless) link `---`. */
    private val ARROW_TOKEN = Regex("""\s*(<?(?:-\.->|={2,}>|-{2,}>)|-{3,})(?:\|(.*?)\|)?""")

    /**
     * Mermaid's *mid-link* label form, where the text sits between the dashes rather than in
     * a `|…|`: `A -- yes --> B`, `A == x ==> B`, `A -. x .-> B`. This was unsupported, so
     * every such edge was silently dropped — which orphaned the target node and wrecked the
     * layout. Tried only after [ARROW_TOKEN] fails, so plain `-->`/`-->|…|` still win.
     *
     * Groups: 1=opener (`--`/`==`/`-.`), 2=label, 3=closer (`-->`/`==>`/`.->`/`---`).
     */
    private val ARROW_MIDLABEL_TOKEN =
        Regex("""\s*(<?--|<?==|<?-\.)\s*(.+?)\s*(-\.->|===>|==>|-{2,}>|\.->)""")

    /**
     * `subgraph Backend`, `subgraph id [Title]`, or `subgraph "Quoted Title"`.
     *
     * Groups: 1=id, 2=`[Title]`, 3=`"Title"`.
     */
    private val SUBGRAPH_START =
        Regex("""^\s*subgraph\s+(\w*)\s*(?:\[(.*?)\]|"(.*?)")?\s*$""", RegexOption.IGNORE_CASE)

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
                label = m.groupValues[2].ifBlank { null }?.let(::cleanLabel),
                isDashed = m.groupValues[1].contains("."),
                end = m.range.last + 1
            )
        }
        ARROW_MIDLABEL_TOKEN.matchAt(line, position)?.let { m ->
            val opener = m.groupValues[1]
            val closer = m.groupValues[3]
            return ArrowMatch(
                label = m.groupValues[2].ifBlank { null }?.let(::cleanLabel),
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
            val label = labelOf(match) ?: id
            val shape = shapeOf(match)
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

    /** Strips everything but word chars, spaces and dashes for the last-resort node fallback. */
    private val FALLBACK_STRIP = Regex("""[^\w -]""")

    /**
     * Accumulates a flowchart's nodes, edges and subgraphs as body lines are fed in.
     *
     * Pulling the mutable state and the per-line decisions into one object keeps [parse] a
     * short orchestrator and gives each rule (open a subgraph, close one, scan nodes, assign
     * membership) a single named home, instead of one long loop juggling five collections.
     */
    private class FlowchartBuilder {
        private val nodes = mutableMapOf<String, DiagramNode>()
        private val edges = mutableListOf<DiagramEdge>()
        private val groups = mutableListOf<DiagramGroup>()
        private val openGroups = ArrayDeque<MutableGroup>()
        private val assigned = mutableSetOf<String>()

        /**
         * Feeds one body line through a fixed precedence. Keyword lines are consumed *before*
         * node scanning because [parseEdgeChain] registers an id the moment it matches, so
         * `subgraph …`, `end` and `classDef …` would otherwise each become a phantom node.
         */
        fun consume(line: String) {
            if (tryOpenSubgraph(line)) return
            if (tryCloseSubgraph(line)) return
            if (IGNORED_DIRECTIVE.containsMatchIn(line)) return
            assignToOpenGroup(scanNodesAndEdges(line))
        }

        /** Finalises the diagram: close any unterminated subgraphs, then fall back if empty. */
        fun build(bodyLines: List<String>, direction: FlowDirection): ParsedDiagram {
            drainOpenSubgraphs()
            if (nodes.isEmpty()) fillFallback(bodyLines)
            return ParsedDiagram("flowchart", direction, nodes.values.toList(), edges, groups)
        }

        private fun tryOpenSubgraph(line: String): Boolean {
            val match = SUBGRAPH_START.find(line) ?: return false
            val rawId = match.groupValues[1].trim()
            val bracketTitle = firstNonBlank(match, 2, 3)
            openGroups.addLast(
                MutableGroup(
                    id = rawId.ifBlank { "group_${groups.size + openGroups.size}" },
                    title = (bracketTitle ?: rawId).trim().trim('"')
                )
            )
            return true
        }

        private fun tryCloseSubgraph(line: String): Boolean {
            if (!BLOCK_END.matches(line.trim())) return false
            closeGroup(openGroups.removeLastOrNull())
            return true
        }

        /** Scans a line as an edge chain, or failing that a standalone node; returns mentions. */
        private fun scanNodesAndEdges(line: String): List<String> {
            val mentioned = mutableListOf<String>()
            if (!parseEdgeChain(line, nodes, edges, mentioned)) {
                val nodeMatch = NODE_TOKEN.find(line)
                if (nodeMatch != null && nodeMatch.groupValues[1].isNotBlank()) {
                    val id = nodeMatch.groupValues[1]
                    mentioned.add(id)
                    nodes[id] = DiagramNode(id, labelOf(nodeMatch) ?: id, shapeOf(nodeMatch))
                }
            }
            return mentioned
        }

        /**
         * Membership follows *mention*, not creation — a subgraph body usually just lists ids
         * declared earlier. The first open group to mention a node owns it, matching Mermaid.
         */
        private fun assignToOpenGroup(mentioned: List<String>) {
            val current = openGroups.lastOrNull() ?: return
            mentioned.forEach { id ->
                if (id in nodes && assigned.add(id)) current.nodeIds.add(id)
            }
        }

        /** Unterminated subgraphs still render, rather than being discarded on a typo. */
        private fun drainOpenSubgraphs() {
            while (openGroups.isNotEmpty()) closeGroup(openGroups.removeLast())
        }

        /** Records a finished subgraph, dropping empty ones so no blank frame is drawn. */
        private fun closeGroup(finished: MutableGroup?) {
            if (finished != null && finished.nodeIds.isNotEmpty()) {
                groups.add(DiagramGroup(finished.id, finished.title, finished.nodeIds.toList()))
            }
        }

        /** Last resort when nothing parsed: turn each non-blank body line into a plain node. */
        private fun fillFallback(bodyLines: List<String>) {
            for ((idx, line) in bodyLines.withIndex()) {
                val clean = line.replace(FALLBACK_STRIP, " ").trim()
                if (clean.isNotBlank()) nodes["node_$idx"] = DiagramNode("node_$idx", clean)
            }
        }
    }

    fun parse(code: String): ParsedDiagram {
        val lines = code.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("%%") }
        if (lines.isEmpty()) {
            return ParsedDiagram("flowchart", FlowDirection.DEFAULT, emptyList(), emptyList())
        }

        val firstLine = lines.first().lowercase()
        if (firstLine.startsWith("sequence") || firstLine.contains("sequencediagram")) {
            return parseSequenceDiagram(lines)
        }
        // DIA-08: parsed as a word, by FlowDirection. The previous test asked whether the
        // header *contained* "lr", which could not see RL or BT at all.
        val direction = FlowDirection.parse(lines.first())

        val bodyLines = lines.drop(1)
        val builder = FlowchartBuilder()
        bodyLines.forEach(builder::consume)
        return builder.build(bodyLines, direction)
    }

    /** A sequence-diagram message line: `A ->> B : text`, dashed/plain arrows included. */
    private val SEQ_MESSAGE = Regex("""(\w+)\s*(->>|-->>|->|-->)\s*(\w+)\s*:\s*(.+)""")

    private fun parseSequenceDiagram(lines: List<String>): ParsedDiagram {
        val actors = mutableSetOf<String>()
        val edges = mutableListOf<DiagramEdge>()

        for (line in lines.drop(1)) {
            val match = SEQ_MESSAGE.find(line)
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
            direction = FlowDirection.LR,
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

    // ADR-002 step 3: the frame lives in DiagramCard, shared with MathFormulaRenderer.
    DiagramCard(
        title = diagramTypeLabel,
        icon = Icons.Default.AccountTree,
        iconDescription = "Mermaid Diagram",
        theme = theme,
        modifier = modifier,
        trailing = {
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
    ) {
        if (showRawCode) {
            RawCodeView(code = code)
        } else {
            DiagramContent(
                diagram = diagram,
                sequence = sequence,
                code = code,
                theme = theme
            )
        }
    }
}

/** Raw Mermaid source, monospaced on a dark background. */
@Composable
private fun RawCodeView(code: String) {
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
}

/**
 * The rendered diagram: a sequence view, a flowchart, or the raw code fallback
 * when nothing parsed. Each branch is mutually exclusive, keeping this flat.
 */
@Composable
private fun DiagramContent(
    diagram: ParsedDiagram,
    sequence: SequenceDiagram?,
    code: String,
    theme: PresentationTheme
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            sequence != null && !sequence.isEmpty ->
                SequenceDiagramView(sequence, theme, Modifier.fillMaxSize())

            diagram.nodes.isEmpty() ->
                Text(
                    text = code,
                    color = theme.textMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )

            else ->
                // MMD-1: laid out from the graph, not from parse order.
                // FitMode.Contain: FlowchartGraphView reports its own intrinsic
                // scene size, which can exceed the canvas — contain-fit scales it
                // down deterministically instead of letting it clip.
                FitToCanvas(modifier = Modifier.fillMaxSize(), fitMode = FitMode.Contain) {
                    FlowchartGraphView(diagram, theme)
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
