package com.skaldoria.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.diagram.FlowchartLayoutEngine
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

    SubcomposeLayout(modifier) { constraints ->
        val nodes = diagram.nodes
        if (nodes.isEmpty()) return@SubcomposeLayout layout(0, 0) {}

        // 1. Measure every node at its natural size.
        val nodePlaceables = subcompose("nodes") {
            nodes.forEach { node ->
                Box(Modifier.layoutId(node.id)) { NodeCard(node = node, theme = theme) }
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
            labelWidths = labelWidths
        )

        // 3. Draw edges from the scene.
        val edgePlaceable = subcompose("edges") {
            Canvas(Modifier) {
                drawFlowchartEdges(scene, theme, measurer)
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

/**
 * Draws all edges from a flowchart scene.
 *
 * Extracted to reduce cognitive complexity of the main composable.
 */
private fun DrawScope.drawFlowchartEdges(
    scene: FlowchartScene,
    theme: PresentationTheme,
    measurer: TextMeasurer
) {
    for (edge in scene.edges) {
        drawFlowchartEdge(edge, theme, measurer)
    }
}

/**
 * Draws a single edge with its path, arrowhead, and label.
 */
private fun DrawScope.drawFlowchartEdge(
    edge: FlowchartScene.EdgePlacement,
    theme: PresentationTheme,
    measurer: TextMeasurer
) {
    drawEdgePath(edge, theme)
    drawEdgeArrowHead(edge, theme)
    drawEdgeLabel(edge, theme, measurer)
}

/**
 * Draws the path for an edge with optional dashing.
 */
private fun DrawScope.drawEdgePath(edge: FlowchartScene.EdgePlacement, theme: PresentationTheme) {
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
    drawPath(path, theme.primary, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f, cap = StrokeCap.Round, pathEffect = effect))
}

/**
 * Draws the arrowhead at the end of an edge.
 */
private fun DrawScope.drawEdgeArrowHead(edge: FlowchartScene.EdgePlacement, theme: PresentationTheme) {
    val isForward = if (edge.path.isHorizontal) {
        edge.path.endPoint.x >= edge.path.startPoint.x
    } else {
        edge.path.endPoint.y >= edge.path.startPoint.y
    }
    drawArrowHead(edge.path.endPoint, if (isForward) 1f else -1f, edge.path.isHorizontal, theme.primary)
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
    drawText(
        textLayoutResult = layoutResult,
        topLeft = Offset(
            labelCenter.x - layoutResult.size.width / 2f,
            labelCenter.y - layoutResult.size.height / 2f
        ),
        color = theme.primary
    )
}

