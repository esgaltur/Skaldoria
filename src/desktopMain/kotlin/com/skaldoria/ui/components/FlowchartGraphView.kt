package com.skaldoria.ui.components

import com.skaldoria.core.diagram.ParsedDiagram
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.skaldoria.core.diagram.FlowchartLayoutEngine
import androidx.compose.ui.graphics.Color
import com.skaldoria.core.diagram.DiagramStyling
import com.skaldoria.core.diagram.FlowchartScene
import com.skaldoria.theme.PresentationTheme
import kotlin.math.roundToInt

/**
 * Renders a flowchart from its actual graph structure.
 *
 * MMD-1: replaces the linear chain renderer, which emitted nodes in parse order and drew a
 * fixed 50dp connector between consecutive ones — so branches were drawn as a queue and the
 * arrows described a topology the diagram did not have.
 *
 * Nodes are measured first, then positioned from [FlowchartLayoutEngine]'s layer/order
 * assignment, then edges are drawn between the resulting rectangles. Edges go behind the
 * nodes so an arrow never covers a label.
 */
@Composable
fun FlowchartGraphView(
    diagram: ParsedDiagram,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    val layout = remember(diagram) {
        FlowchartLayoutEngine.layout(
            nodeIds = diagram.nodes.map { it.id },
            edges = diagram.edges.map { it.fromId to it.toId }
        )
    }
    val measurer = rememberTextMeasurer()

    val density = androidx.compose.ui.platform.LocalDensity.current

    // The gap between layers has to be wide enough for the widest edge label. Edges are
    // drawn behind the node cards, so a label that outgrows the gap is simply covered by
    // the next node — it looked truncated with no indication anything was wrong.
    val labelWidths = remember(diagram, measurer, density) {
        diagram.edges
            .mapNotNull { edge ->
                edge.label?.takeIf { it.isNotBlank() }?.let { it to measurer.measure(it, EDGE_LABEL_STYLE).size.width.toFloat() }
            }
            .toMap()
    }

    // node -> subgraph, so the layout can reserve a cross-axis band per group.
    val groupOf = remember(diagram) {
        diagram.groups.flatMap { g -> g.nodeIds.map { it to g.id } }.toMap()
    }

    SubcomposeLayout(modifier) { constraints ->
        val nodes = diagram.nodes
        if (nodes.isEmpty()) return@SubcomposeLayout layout(0, 0) {}

        // 1. Measure every node at its natural size.
        val nodePlaceables = subcompose("nodes") {
            nodes.forEach { node ->
                Box(Modifier.layoutId(node.id)) {
                    NodeCard(node = node, theme = theme, style = diagram.styling.forNode(node.id))
                }
            }
        }.map { it.measure(Constraints()) }

        val nodeSize = nodes.mapIndexed { index, node ->
            node.id to IntSize(nodePlaceables[index].width, nodePlaceables[index].height)
        }.toMap()

        // 2. Arrange the entire scene (geometry).
        val scene = FlowchartScene.arrange(
            layout = layout,
            nodeIds = diagram.nodes.map { it.id },
            nodeSize = nodeSize,
            edges = diagram.edges,
            horizontal = diagram.isHorizontal,
            availableBounds = IntSize(constraints.maxWidth, constraints.maxHeight),
            labelWidths = labelWidths,
            groupOf = groupOf,
            groups = diagram.groups.map { Triple(it.id, it.title, it.nodeIds) },
            reversed = diagram.direction.isReversed
        )

        // 3. Draw subgraph frames, then edges, into one canvas behind the node cards.
        val edgePlaceable = subcompose("edges") {
            Canvas(Modifier) {
                drawSubgraphFrames(scene, theme, measurer)
                drawFlowchartEdges(scene, theme, measurer, diagram.styling)
            }
        }.first().measure(Constraints.fixed(scene.width, scene.height))

        // 4. Place everything unscaled.
        layout(scene.width, scene.height) {
            edgePlaceable.place(0, 0)
            nodes.forEachIndexed { index, node ->
                val placement = scene.nodes[node.id] ?: return@forEachIndexed
                nodePlaceables[index].place(placement.rect.left.roundToInt(), placement.rect.top.roundToInt())
            }
        }
    }
}

/** Shared so the lane-gap measurement and the drawing can never disagree. */
private val EDGE_LABEL_STYLE = TextStyle(
    fontSize = 10.sp,
    fontWeight = FontWeight.SemiBold,
    fontFamily = FontFamily.Monospace
)

/** Title style for a `subgraph` frame — shares one definition with its measurement. */
private val GROUP_TITLE_STYLE = TextStyle(
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    fontFamily = FontFamily.Monospace
)

/** Breathing room between a subgraph's member nodes and its frame. */
private const val GROUP_PADDING = 18f
private const val GROUP_TITLE_BAND = 20f

/**
 * Draws a labelled frame around each `subgraph`'s member nodes.
 *
 * The layout engine has no notion of clusters, so the frame is derived *after* arrangement
 * from the bounding box of the members. That keeps subgraph support entirely additive — a
 * diagram without them lays out exactly as before — at the cost of not guaranteeing that a
 * group's members end up adjacent. In practice they do, because members are almost always
 * connected to each other and the layered layout already places connected nodes together.
 */
private fun DrawScope.drawSubgraphFrames(
    scene: FlowchartScene,
    theme: PresentationTheme,
    measurer: TextMeasurer
) {
    for (group in scene.groups) {
        val rect = group.rect
        drawRoundRect(
            color = theme.primary.copy(alpha = 0.05f),
            topLeft = Offset(rect.left, rect.top),
            size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
        )
        drawRoundRect(
            color = theme.primary.copy(alpha = 0.35f),
            topLeft = Offset(rect.left, rect.top),
            size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
            )
        )

        if (group.title.isNotBlank()) {
            val layoutResult = measurer.measure(
                text = group.title,
                style = GROUP_TITLE_STYLE.copy(color = theme.primary),
                maxLines = 1
            )
            translate(group.titleAnchor.x, group.titleAnchor.y) { drawText(layoutResult) }
        }
    }
}

/**
 * Draws all edges from a flowchart scene.
 *
 * Connectors and arrowheads for every edge are drawn first, then every label on top. Drawing
 * a label immediately after its own edge is not enough: a *later* edge's connector would then
 * be painted over an *earlier* edge's label, and the line struck straight through the text
 * (e.g. two edges leaving the same node share a horizontal run at the source's centre line).
 * Deferring all labels to a second pass guarantees no connector is ever drawn over a label.
 */
private fun DrawScope.drawFlowchartEdges(
    scene: FlowchartScene,
    theme: PresentationTheme,
    measurer: TextMeasurer,
    /** DIA-07: `linkStyle` colours, keyed by declaration order. */
    styling: DiagramStyling = DiagramStyling.EMPTY
) {
    for ((index, edge) in scene.edges.withIndex()) {
        val stroke = styling.forEdge(index)?.stroke ?: theme.primary
        drawEdgePath(edge, theme, stroke)
        drawEdgeArrowHead(edge, theme, stroke)
    }
    for (edge in scene.edges) {
        drawEdgeLabel(edge, theme, measurer)
    }
}

/**
 * Draws the path for an edge with optional dashing.
 */
private fun DrawScope.drawEdgePath(
    edge: FlowchartScene.EdgePlacement,
    theme: PresentationTheme,
    stroke: Color = theme.primary
) {
    val path = Path().apply {
        moveTo(edge.path.startPoint.x, edge.path.startPoint.y)
        if (edge.path.isHorizontal) {
            val midX = (edge.path.startPoint.x + edge.path.endPoint.x) / 2f
            lineTo(midX, edge.path.startPoint.y)
            lineTo(midX, edge.path.endPoint.y)
        } else {
            val midY = (edge.path.startPoint.y + edge.path.endPoint.y) / 2f
            lineTo(edge.path.startPoint.x, midY)
            lineTo(edge.path.endPoint.x, midY)
        }
        lineTo(edge.path.endPoint.x, edge.path.endPoint.y)
    }
    val effect = if (edge.isDashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
    drawPath(path, stroke, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f, cap = StrokeCap.Round, pathEffect = effect))
}

/**
 * Draws the arrowhead at the end of an edge.
 */
private fun DrawScope.drawEdgeArrowHead(
    edge: FlowchartScene.EdgePlacement,
    theme: PresentationTheme,
    stroke: Color = theme.primary
) {
    val isForward = if (edge.path.isHorizontal) {
        edge.path.endPoint.x >= edge.path.startPoint.x
    } else {
        edge.path.endPoint.y >= edge.path.startPoint.y
    }
    drawArrowHead(edge.path.endPoint, if (isForward) 1f else -1f, edge.path.isHorizontal, stroke)
}

/**
 * Draws the label for an edge if present.
 */
private fun DrawScope.drawEdgeLabel(edge: FlowchartScene.EdgePlacement, theme: PresentationTheme, measurer: TextMeasurer) {
    if (edge.labelBox == null) return

    val layoutResult = measurer.measure(
        text = edge.label ?: "",
        style = EDGE_LABEL_STYLE.copy(color = theme.primary),
        maxLines = 1
    )
    val labelCenter = edge.labelBox.center

    // Mask the edge line behind the text. Without this the connector runs straight through
    // the label and reads as a strike-through — the label and the line are on the same
    // canvas, drawn in that order, so the line is otherwise visible in the gaps of the glyphs.
    val boxWidth = layoutResult.size.width + LABEL_BACKGROUND_PADDING_X
    val boxHeight = layoutResult.size.height + LABEL_BACKGROUND_PADDING_Y
    drawRoundRect(
        color = theme.surface,
        topLeft = Offset(labelCenter.x - boxWidth / 2f, labelCenter.y - boxHeight / 2f),
        size = androidx.compose.ui.geometry.Size(boxWidth, boxHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
    )

    drawText(
        textLayoutResult = layoutResult,
        topLeft = Offset(
            labelCenter.x - layoutResult.size.width / 2f,
            labelCenter.y - layoutResult.size.height / 2f
        ),
        color = theme.primary
    )
}

/** Padding of the label's background mask around its text, so the connector is fully hidden. */
private const val LABEL_BACKGROUND_PADDING_X = 8f
private const val LABEL_BACKGROUND_PADDING_Y = 2f

