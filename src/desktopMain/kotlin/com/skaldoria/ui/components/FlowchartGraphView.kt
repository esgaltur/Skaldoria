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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.diagram.FlowchartLayoutEngine
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
    val siblingGap = with(density) { 26.dp.toPx() }

    // The gap between layers has to be wide enough for the widest edge label. Edges are
    // drawn behind the node cards, so a label that outgrows the gap is simply covered by
    // the next node — it looked truncated with no indication anything was wrong.
    val laneGap = remember(diagram, measurer, density) {
        val widest = diagram.edges
            .mapNotNull { it.label?.takeIf { label -> label.isNotBlank() } }
            .maxOfOrNull { measurer.measure(it, EDGE_LABEL_STYLE).size.width }
            ?: 0
        maxOf(with(density) { 68.dp.toPx() }, widest + with(density) { 28.dp.toPx() })
    }

    SubcomposeLayout(modifier) { constraints ->
        val nodes = diagram.nodes
        if (nodes.isEmpty()) return@SubcomposeLayout layout(0, 0) {}

        // 1. Measure every node at its natural size — the layout needs real bounds.
        val nodePlaceables = subcompose("nodes") {
            nodes.forEach { node ->
                Box(Modifier.layoutId(node.id)) { NodeCard(node = node, theme = theme) }
            }
        }.map { it.measure(Constraints()) }

        val sizeById = nodes.mapIndexed { index, node -> node.id to nodePlaceables[index] }.toMap()

        // 2. Position from the layer model. `isHorizontal` decides which axis carries flow.
        val horizontal = diagram.isHorizontal
        val bounds = mutableMapOf<String, Rect>()
        var flowCursor = 0f

        for (layerIndex in 0 until layout.layerCount) {
            val members = layout.layer(layerIndex)
            val placeables = members.mapNotNull { sizeById[it.id] }
            if (placeables.isEmpty()) continue

            val laneExtent = placeables.maxOf { if (horizontal) it.width else it.height }.toFloat()
            val crossTotal = placeables.sumOf { if (horizontal) it.height else it.width }
                .toFloat() + siblingGap * (placeables.size - 1)

            var crossCursor = -crossTotal / 2f
            for (placement in members) {
                val placeable = sizeById[placement.id] ?: continue
                val crossExtent = (if (horizontal) placeable.height else placeable.width).toFloat()
                val flowExtent = (if (horizontal) placeable.width else placeable.height).toFloat()

                // Centre each node within its lane so mixed-width nodes stay aligned.
                val flowPos = flowCursor + (laneExtent - flowExtent) / 2f
                bounds[placement.id] = if (horizontal) {
                    Rect(flowPos, crossCursor, flowPos + flowExtent, crossCursor + crossExtent)
                } else {
                    Rect(crossCursor, flowPos, crossCursor + crossExtent, flowPos + flowExtent)
                }
                crossCursor += crossExtent + siblingGap
            }
            flowCursor += laneExtent + laneGap
        }

        // 3. Normalise to a positive-origin box.
        val minX = bounds.values.minOf { it.left }
        val minY = bounds.values.minOf { it.top }
        val shifted = bounds.mapValues { (_, rect) -> rect.translate(-minX, -minY) }
        val contentWidth = shifted.values.maxOf { it.right }.roundToInt().coerceAtLeast(1)
        val contentHeight = shifted.values.maxOf { it.bottom }.roundToInt().coerceAtLeast(1)

        // 4. Edges are subcomposed last, once node rectangles are known, and placed first
        //    so they render behind the cards.
        val edgePlaceable = subcompose("edges") {
            Canvas(Modifier) {
                for (edge in diagram.edges) {
                    val from = shifted[edge.fromId] ?: continue
                    val to = shifted[edge.toId] ?: continue
                    drawEdge(from, to, edge.label, edge.isDashed, horizontal, theme, measurer)
                }
            }
        }.first().measure(Constraints.fixed(contentWidth, contentHeight))

        // 5. Report the graph's intrinsic size and place everything unscaled.
        //
        // Fitting is the caller's job (MermaidDiagramCanvas wraps this in FitToCanvas).
        // Scaling the edge canvas and the node cards as separate placeables desynced them —
        // one transform over the whole subtree is the only way they stay aligned.
        layout(contentWidth, contentHeight) {
            edgePlaceable.place(0, 0)
            nodes.forEachIndexed { index, node ->
                val rect = shifted[node.id] ?: return@forEachIndexed
                nodePlaceables[index].place(rect.left.roundToInt(), rect.top.roundToInt())
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
 * Draws one edge between two node rectangles, entering and leaving on the faces that face
 * each other so the arrowhead lands on the border rather than under the card.
 */
private fun DrawScope.drawEdge(
    from: Rect,
    to: Rect,
    label: String?,
    isDashed: Boolean,
    horizontal: Boolean,
    theme: PresentationTheme,
    measurer: TextMeasurer
) {
    val forward = if (horizontal) to.center.x >= from.center.x else to.center.y >= from.center.y

    val start: Offset
    val end: Offset
    if (horizontal) {
        start = Offset(if (forward) from.right else from.left, from.center.y)
        end = Offset(if (forward) to.left else to.right, to.center.y)
    } else {
        start = Offset(from.center.x, if (forward) from.bottom else from.top)
        end = Offset(to.center.x, if (forward) to.top else to.bottom)
    }

    val effect = if (isDashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
    val color = theme.primary

    // An orthogonal elbow reads far more clearly than a diagonal once a layer branches.
    val path = Path().apply {
        moveTo(start.x, start.y)
        if (horizontal) {
            val midX = (start.x + end.x) / 2f
            lineTo(midX, start.y)
            lineTo(midX, end.y)
        } else {
            val midY = (start.y + end.y) / 2f
            lineTo(start.x, midY)
            lineTo(end.x, midY)
        }
        lineTo(end.x, end.y)
    }
    drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f, cap = StrokeCap.Round, pathEffect = effect))

    drawArrowHead(end, horizontal, forward, color)

    if (!label.isNullOrBlank()) {
        val layoutResult = measurer.measure(
            text = label,
            style = EDGE_LABEL_STYLE.copy(color = theme.primary),
            maxLines = 1
        )
        // Sit on the segment that runs into the target, inside the lane gap. The raw
        // midpoint of start..end lands on the elbow — which for a fanned-out branch is
        // right on top of the target node, and stacks every sibling label in one spot.
        // Centred across the FULL lane gap (start..end spans it), at the *target's* row so
        // sibling branches separate vertically instead of stacking on the elbow corner.
        // The gap is sized to the widest label, so centring here is what keeps it uncovered.
        val mid = if (horizontal) {
            Offset((start.x + end.x) / 2f, end.y - layoutResult.size.height / 2f - 4f)
        } else {
            Offset(end.x + layoutResult.size.width / 2f + 6f, (start.y + end.y) / 2f)
        }
        val boxWidth = layoutResult.size.width + 8f
        val boxHeight = layoutResult.size.height + 4f
        drawRect(
            color = theme.surfaceVariant,
            topLeft = Offset(mid.x - boxWidth / 2f, mid.y - boxHeight / 2f),
            size = androidx.compose.ui.geometry.Size(boxWidth, boxHeight)
        )
        translate(
            left = mid.x - layoutResult.size.width / 2f,
            top = mid.y - layoutResult.size.height / 2f
        ) { drawText(layoutResult) }
    }
}

private fun DrawScope.drawArrowHead(tip: Offset, horizontal: Boolean, forward: Boolean, color: Color) {
    val length = 10f
    val spread = 5.5f
    val direction = if (forward) 1f else -1f
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        if (horizontal) {
            lineTo(tip.x - direction * length, tip.y - spread)
            lineTo(tip.x - direction * length, tip.y + spread)
        } else {
            lineTo(tip.x - spread, tip.y - direction * length)
            lineTo(tip.x + spread, tip.y - direction * length)
        }
        close()
    }
    drawPath(path, color)
}

private fun Rect.translate(dx: Float, dy: Float): Rect =
    Rect(left + dx, top + dy, right + dx, bottom + dy)
