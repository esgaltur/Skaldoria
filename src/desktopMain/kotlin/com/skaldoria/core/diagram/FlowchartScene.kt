package com.skaldoria.core.diagram

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.skaldoria.core.theme.DesignTokens
import com.skaldoria.ui.components.DiagramEdge
import kotlin.math.roundToInt

/**
 * A fully-resolved scene of a flowchart: all node rectangles, edges with path + label positions.
 *
 * This is the pure output of the arrange function — it contains only measurements and
 * coordinates, no Compose/drawing logic. The renderer becomes a simple walk through this
 * scene, drawing each primitive.
 */
data class FlowchartScene(
    val nodes: Map<String, NodePlacement>,
    val edges: List<EdgePlacement>,
    val width: Int,
    val height: Int
) {
    data class NodePlacement(val id: String, val rect: Rect)
    data class EdgePlacement(
        val fromId: String,
        val toId: String,
        val label: String?,
        val isDashed: Boolean,
        val path: Path,
        val labelBox: LabelBox?
    ) {
        data class Path(val startPoint: Offset, val endPoint: Offset, val isHorizontal: Boolean)
        data class LabelBox(val center: Offset, val width: Float, val height: Float)
    }

    companion object {
        fun arrange(
            layout: FlowchartLayoutEngine.Layout,
            nodeIds: List<String>,
            nodeSize: Map<String, IntSize>,
            edges: List<DiagramEdge>,
            horizontal: Boolean,
            availableBounds: IntSize,
            labelWidths: Map<String, Float>
        ): FlowchartScene {
            if (nodeIds.isEmpty()) return FlowchartScene(emptyMap(), emptyList(), 0, 0)

            val bounds = positionNodes(layout, nodeSize, horizontal, labelWidths)
            val (shifted, contentWidth, contentHeight) = normalizeToOrigin(bounds)
            val nodePlacements = shifted.mapValues { (id, rect) -> NodePlacement(id, rect) }
            val edgePlacements = arrangeEdgesWithLabels(edges, shifted, horizontal, labelWidths)

            return FlowchartScene(nodePlacements, edgePlacements, contentWidth, contentHeight)
        }

        private fun positionNodes(
            layout: FlowchartLayoutEngine.Layout,
            nodeSize: Map<String, IntSize>,
            horizontal: Boolean,
            labelWidths: Map<String, Float>
        ): Map<String, Rect> {
            val siblingGap = with(DesignTokens.Diagram) { flowchartSiblingGap.value }
            val minLaneGap = with(DesignTokens.Diagram) { flowchartMinLaneGap.value }
            val laneGap = kotlin.math.max(minLaneGap, (labelWidths.values.maxOrNull() ?: 0f) + 28f)

            val bounds = mutableMapOf<String, Rect>()
            var flowCursor = 0f

            for (layerIndex in 0 until layout.layerCount) {
                val members = layout.layer(layerIndex)
                val laneExtent = positionLayer(members, nodeSize, horizontal, siblingGap, flowCursor, bounds)
                flowCursor += laneExtent + laneGap
            }

            return bounds
        }

        private fun positionLayer(
            members: List<FlowchartLayoutEngine.Placement>,
            nodeSize: Map<String, IntSize>,
            horizontal: Boolean,
            siblingGap: Float,
            flowCursor: Float,
            bounds: MutableMap<String, Rect>
        ): Float {
            val placeables = members.mapNotNull { nodeSize[it.id] }
            if (placeables.isEmpty()) return 0f

            val laneExtent = placeables.maxOf { if (horizontal) it.width else it.height }.toFloat()
            val crossTotal = placeables.sumOf { if (horizontal) it.height else it.width }
                .toFloat() + siblingGap * (placeables.size - 1)

            var crossCursor = -crossTotal / 2f
            for (placement in members) {
                val placeable = nodeSize[placement.id] ?: continue
                val nodeRect = positionNodeInLayer(placeable, horizontal, laneExtent, flowCursor, crossCursor)
                bounds[placement.id] = nodeRect
                val crossExtent = (if (horizontal) placeable.height else placeable.width).toFloat()
                crossCursor += crossExtent + siblingGap
            }

            return laneExtent
        }

        private fun positionNodeInLayer(
            placeable: IntSize,
            horizontal: Boolean,
            laneExtent: Float,
            flowCursor: Float,
            crossCursor: Float
        ): Rect {
            val crossExtent = (if (horizontal) placeable.height else placeable.width).toFloat()
            val flowExtent = (if (horizontal) placeable.width else placeable.height).toFloat()
            val flowPos = flowCursor + (laneExtent - flowExtent) / 2f

            return if (horizontal) {
                Rect(flowPos, crossCursor, flowPos + flowExtent, crossCursor + crossExtent)
            } else {
                Rect(crossCursor, flowPos, crossCursor + crossExtent, flowPos + flowExtent)
            }
        }

        private fun normalizeToOrigin(bounds: Map<String, Rect>): Triple<Map<String, Rect>, Int, Int> {
            val minX = bounds.values.minOf { it.left }
            val minY = bounds.values.minOf { it.top }
            val shifted = bounds.mapValues { (_, rect) -> rect.translate(-minX, -minY) }
            val contentWidth = shifted.values.maxOf { it.right }.roundToInt().coerceAtLeast(1)
            val contentHeight = shifted.values.maxOf { it.bottom }.roundToInt().coerceAtLeast(1)
            return Triple(shifted, contentWidth, contentHeight)
        }

        private fun arrangeEdgesWithLabels(
            edges: List<DiagramEdge>,
            bounds: Map<String, Rect>,
            horizontal: Boolean,
            labelWidths: Map<String, Float>
        ): List<EdgePlacement> {
            val placedLabels = mutableListOf<Rect>()
            return edges.mapNotNull { edge ->
                val from = bounds[edge.fromId] ?: return@mapNotNull null
                val to = bounds[edge.toId] ?: return@mapNotNull null

                val isForward = if (horizontal) to.center.x >= from.center.x else to.center.y >= from.center.y
                val path = computeEdgePath(from, to, isForward, horizontal)
                val labelBox = computeLabelBox(edge, path, horizontal, labelWidths, placedLabels)

                EdgePlacement(edge.fromId, edge.toId, edge.label, edge.isDashed, path, labelBox)
            }
        }

        private fun computeEdgePath(
            from: Rect,
            to: Rect,
            isForward: Boolean,
            horizontal: Boolean
        ): EdgePlacement.Path {
            val start: Offset
            val end: Offset
            if (horizontal) {
                start = Offset(if (isForward) from.right else from.left, from.center.y)
                end = Offset(if (isForward) to.left else to.right, to.center.y)
            } else {
                start = Offset(from.center.x, if (isForward) from.bottom else from.top)
                end = Offset(to.center.x, if (isForward) to.top else to.bottom)
            }
            return EdgePlacement.Path(start, end, horizontal)
        }

        private fun computeLabelBox(
            edge: DiagramEdge,
            path: EdgePlacement.Path,
            horizontal: Boolean,
            labelWidths: Map<String, Float>,
            placedLabels: MutableList<Rect>
        ): EdgePlacement.LabelBox? {
            if (edge.label.isNullOrBlank()) return null

            val labelWidth = labelWidths[edge.label] ?: 50f
            val boxWidth = labelWidth + 8f
            val boxHeight = 20f
            val step = boxHeight + 2f

            var center = if (horizontal) {
                Offset((path.startPoint.x + path.endPoint.x) / 2f, (path.startPoint.y + path.endPoint.y) / 2f)
            } else {
                Offset(path.endPoint.x + labelWidth / 2f + 6f, (path.startPoint.y + path.endPoint.y) / 2f)
            }

            var attempt = 0
            while (attempt < 12) {
                val candidate = Rect(
                    left = center.x - boxWidth / 2f,
                    top = center.y - boxHeight / 2f,
                    right = center.x + boxWidth / 2f,
                    bottom = center.y + boxHeight / 2f
                )
                if (placedLabels.none { it.overlaps(candidate) }) {
                    placedLabels.add(candidate)
                    return EdgePlacement.LabelBox(center, boxWidth, boxHeight)
                }
                attempt++
                val magnitude = ((attempt + 1) / 2) * step
                val sign = if (attempt % 2 == 1) 1f else -1f
                center = Offset(center.x, center.y + sign * magnitude)
            }

            return EdgePlacement.LabelBox(center, boxWidth, boxHeight)
        }
    }
}
