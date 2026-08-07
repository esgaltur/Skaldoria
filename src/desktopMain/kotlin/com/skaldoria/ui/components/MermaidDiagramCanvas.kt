package com.skaldoria.ui.components

import com.skaldoria.core.diagram.DiagramEdge
import com.skaldoria.core.diagram.DiagramGroup
import com.skaldoria.core.diagram.DiagramNode
import com.skaldoria.core.diagram.NodeShape
import com.skaldoria.core.diagram.ParsedDiagram
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
import com.skaldoria.core.diagram.DiagramStyling
import com.skaldoria.core.diagram.EdgeStyle
import com.skaldoria.core.diagram.NodeStyle
import com.skaldoria.core.diagram.StyleDeclarationParser
import com.skaldoria.core.diagram.ClassDiagramParser
import com.skaldoria.core.diagram.DiagramKind
import com.skaldoria.core.diagram.toFlowchart
import com.skaldoria.core.diagram.ErDiagramParser
import com.skaldoria.core.diagram.GanttChart
import com.skaldoria.core.diagram.GanttChartParser
import com.skaldoria.core.diagram.FlowDirection
import com.skaldoria.core.diagram.StateDiagramParser
import com.skaldoria.core.diagram.SequenceDiagram
import com.skaldoria.core.diagram.SequenceDiagramParser
import com.skaldoria.theme.AdaptiveContrastEnforcer
import com.skaldoria.theme.PresentationTheme
import com.skaldoria.ui.components.MermaidParser.ARROW_TOKEN
import com.skaldoria.ui.components.MermaidParser.NODE_BRACKETS
import com.skaldoria.ui.components.MermaidParser.NODE_TOKEN
import com.skaldoria.ui.components.MermaidParser.parseEdgeChain
import com.skaldoria.ui.components.MermaidParser.shapeOf

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

    /** `classDef name fill:#f9f,stroke:#333` — the name list may be comma-separated. */
    private val CLASS_DEF = Regex("""^\s*classDef\s+([\w,\s]+?)\s+(.+)$""", RegexOption.IGNORE_CASE)

    /** `class nodeA,nodeB styleName` — applies a `classDef` to nodes. */
    private val CLASS_APPLY = Regex("""^\s*class\s+([\w,\s]+?)\s+(\w+)\s*$""", RegexOption.IGNORE_CASE)

    /** `style nodeA fill:#f9f` — an inline style for a single node. */
    private val STYLE_NODE = Regex("""^\s*style\s+(\w+)\s+(.+)$""", RegexOption.IGNORE_CASE)

    /** `linkStyle 0,2 stroke:#f00`, or `linkStyle default stroke:#f00`. */
    private val LINK_STYLE = Regex("""^\s*linkStyle\s+([\w,\s]+?)\s+(.+)$""", RegexOption.IGNORE_CASE)

    /**
     * Statements that contribute no nodes or edges, and matching them here is what stops the
     * node scanner registering `classDef`, `class` or `style` as node ids.
     *
     * DIA-07: they are no longer *discarded*. The styling ones are collected first; only
     * `click` and `direction` still fall through to being genuinely ignored.
     */
    private val IGNORED_DIRECTIVE =
        Regex("""^\s*(classDef|class|style|linkStyle|click|direction)\b""", RegexOption.IGNORE_CASE)

    /** Accumulates a subgraph's members while its block is open. */
    private class MutableGroup(val id: String, val title: String) {
        val nodeIds = mutableListOf<String>()
    }

    /**
     * DIA-07: gathers styling statements, then resolves them once the graph is complete.
     *
     * Two-phase because Mermaid imposes no ordering — `class a big` may appear before the
     * `classDef big …` it refers to, and `linkStyle default` has to be applied to a count of
     * edges that is not known until parsing ends. Resolving eagerly would silently drop the
     * forward references, which is the sort of partial support that is worse than none.
     */
    private class StyleCollector {
        private val classDefs = mutableMapOf<String, NodeStyle>()
        private val classAssignments = mutableListOf<Pair<String, String>>()
        private val inlineNodeStyles = mutableMapOf<String, NodeStyle>()
        private val edgeStyles = mutableMapOf<Int, EdgeStyle>()
        private var defaultEdgeStyle: EdgeStyle? = null

        /** @return true when [line] was a styling statement and needs no further parsing. */
        fun tryConsume(line: String): Boolean {
            CLASS_DEF.find(line)?.let { match ->
                val style = StyleDeclarationParser.parseNodeStyle(match.groupValues[2])
                for (name in splitNames(match.groupValues[1])) classDefs[name] = style
                return true
            }
            // Checked after CLASS_DEF: `classDef` also begins with `class`.
            CLASS_APPLY.find(line)?.let { match ->
                val className = match.groupValues[2]
                for (node in splitNames(match.groupValues[1])) classAssignments += node to className
                return true
            }
            STYLE_NODE.find(line)?.let { match ->
                val style = StyleDeclarationParser.parseNodeStyle(match.groupValues[2])
                val id = match.groupValues[1]
                inlineNodeStyles[id] = inlineNodeStyles[id]?.mergedWith(style) ?: style
                return true
            }
            LINK_STYLE.find(line)?.let { match ->
                val style = StyleDeclarationParser.parseEdgeStyle(match.groupValues[2])
                val targets = splitNames(match.groupValues[1])
                if (targets.any { it.equals("default", ignoreCase = true) }) {
                    defaultEdgeStyle = style
                } else {
                    for (index in targets.mapNotNull { it.toIntOrNull() }) edgeStyles[index] = style
                }
                return true
            }
            return false
        }

        fun resolve(edgeCount: Int): DiagramStyling {
            val perNode = mutableMapOf<String, NodeStyle>()

            // `class` first, inline `style` layered on top: the more specific statement wins.
            for ((nodeId, className) in classAssignments) {
                val classStyle = classDefs[className] ?: continue
                perNode[nodeId] = perNode[nodeId]?.mergedWith(classStyle) ?: classStyle
            }
            for ((nodeId, inline) in inlineNodeStyles) {
                perNode[nodeId] = perNode[nodeId]?.mergedWith(inline) ?: inline
            }

            val perEdge = mutableMapOf<Int, EdgeStyle>()
            defaultEdgeStyle?.let { fallback ->
                for (index in 0 until edgeCount) perEdge[index] = fallback
            }
            perEdge.putAll(edgeStyles)

            return DiagramStyling(classes = classDefs.toMap(), nodeStyles = perNode, edgeStyles = perEdge)
        }

        private fun splitNames(raw: String): List<String> =
            raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
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
            // DIA-07: collected *before* the blanket skip, so a styling statement contributes
            // its colours and still never reaches the node scanner.
            if (styleCollector.tryConsume(line)) return
            if (IGNORED_DIRECTIVE.containsMatchIn(line)) return
            assignToOpenGroup(scanNodesAndEdges(line))
        }

        private val styleCollector = StyleCollector()

        /** Finalises the diagram: close any unterminated subgraphs, then fall back if empty. */
        fun build(bodyLines: List<String>, direction: FlowDirection): ParsedDiagram {
            drainOpenSubgraphs()
            if (nodes.isEmpty()) fillFallback(bodyLines)
            return ParsedDiagram(
                type = "flowchart",
                direction = direction,
                nodes = nodes.values.toList(),
                edges = edges,
                groups = groups,
                // Resolved last: `class` statements may precede or follow the `classDef` they
                // name, and Mermaid does not require an order.
                styling = styleCollector.resolve(edgeCount = edges.size)
            )
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

        // DIA-01/02/03/04: the language is decided by the header keyword, deterministically.
        when (DiagramKind.of(code)) {
            DiagramKind.SEQUENCE -> return parseSequenceDiagram(lines)
            DiagramKind.STATE -> return StateDiagramParser.parse(code).toFlowchart()
            DiagramKind.CLASS -> return ClassDiagramParser.parse(code).toFlowchart()
            DiagramKind.ER -> return ErDiagramParser.parse(code).toFlowchart()
            // Gantt is a timeline, not a graph; it has its own renderer and never reaches here.
            DiagramKind.GANTT -> return ParsedDiagram("gantt", FlowDirection.DEFAULT, emptyList(), emptyList())
            DiagramKind.FLOWCHART -> Unit
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

    // DIA-04: a Gantt is a timeline, not a graph, so it never goes through the flowchart model.
    val gantt = remember(code) {
        if (DiagramKind.of(code) == DiagramKind.GANTT) GanttChartParser.parse(code) else null
    }

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
            diagram.type == "class" -> "CLASS DIAGRAM"
            diagram.type == "state" -> "STATE DIAGRAM"
            diagram.type == "er" -> "ENTITY RELATIONSHIP"
            diagram.type == "gantt" -> "GANTT CHART"
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
                gantt = gantt,
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
    gantt: GanttChart?,
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

            // DIA-04: `GanttChartView` returns false when the schedule cannot be resolved
            // deterministically — a non-ISO `dateFormat`, or nothing anchored to a calendar.
            // Falling through to the source then is the honest outcome; a chart drawn from a
            // guessed date format would be confidently wrong.
            gantt != null && !gantt.isEmpty ->
                if (!GanttChartView(gantt, theme, Modifier.fillMaxSize())) {
                    Text(
                        text = code,
                        color = theme.textMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }

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
    theme: PresentationTheme,
    /**
     * DIA-07: the node's declared style, or null to use the theme.
     *
     * Every field is applied independently rather than as a bundle, because Mermaid lets an
     * author set only `fill` — and a diagram that sets one property must not lose the other
     * two to defaults the palette never chose.
     */
    style: NodeStyle? = null
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
                // A declared fill is flat: the gradient is Skaldoria's own styling, and
                // applying it over an author's colour changes the colour they asked for.
                style?.fill?.let { Brush.linearGradient(listOf(it, it)) }
                    ?: Brush.linearGradient(listOf(theme.surfaceVariant, theme.surface))
            )
            .border(
                (style?.strokeWidthPx?.dp ?: 1.5.dp),
                style?.stroke ?: theme.primary.copy(alpha = 0.8f),
                shape
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = node.label,
                color = style?.textColor ?: theme.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                // DIA-02/03: a class or entity box lists its members, so three lines truncates
                // most real ones. Still bounded — an unbounded box would break the layout.
                maxLines = NODE_LABEL_MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )
            if (node.showId && node.id != node.label && node.id.isNotBlank()) {
                Text(
                    text = node.id,
                    // DIA-07: `textMuted` is chosen to sit on the theme's surface. On a node
                    // with a declared fill it is near-invisible — the id vanished on a yellow
                    // node, which the styling tests could not see and the render did. Muted
                    // against the actual fill instead, via the same enforcer that keeps body
                    // text legible.
                    color = style?.fill?.let { fill ->
                        AdaptiveContrastEnforcer.ensureContrast(
                            foreground = style.textColor ?: theme.textMuted,
                            background = fill,
                            minContrastRatio = NODE_ID_MIN_CONTRAST
                        )
                    } ?: theme.textMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Contrast floor for a node's id sublabel against a declared fill.
 *
 * Lower than body text on purpose — it is deliberately secondary information — but not so low
 * that it disappears, which is what `textMuted` did on a bright custom fill. WCAG 1.4.11's 3:1
 * for non-text-critical graphics is the right reference point.
 */
private const val NODE_ID_MIN_CONTRAST = 3.0f

/** Bound on a node label's lines. Enough for a real class box, short of unbounded. */
private const val NODE_LABEL_MAX_LINES = 12
