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
    val height: Int,
    /** `subgraph` frames, already resolved to rectangles. */
    val groups: List<GroupPlacement> = emptyList()
) {
    data class NodePlacement(val id: String, val rect: Rect)

    /**
     * A resolved `subgraph` frame.
     *
     * Computed here rather than in the renderer so the two invariants that make a frame
     * *honest* can be asserted without rendering anything:
     *  - it must not enclose a node that is not a member;
     *  - it must not overlap another group's frame.
     */
    data class GroupPlacement(
        val id: String,
        val title: String,
        val rect: Rect,
        val titleAnchor: Offset
    )
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

    /**
     * This scene reflected along its primary axis — DIA-08's `RL` and `BT`.
     *
     * Edges are mirrored rather than re-derived, so an arrow still runs from its source to its
     * target and simply points the other way. Reversing the *endpoints* instead would draw the
     * arrowhead at the wrong end, which is the mistake that makes a reversed flowchart look
     * right until you read it.
     */
    fun mirrored(horizontal: Boolean): FlowchartScene {
        fun flip(point: Offset): Offset =
            if (horizontal) Offset(width - point.x, point.y) else Offset(point.x, height - point.y)

        fun flip(rect: Rect): Rect = if (horizontal) {
            Rect(width - rect.right, rect.top, width - rect.left, rect.bottom)
        } else {
            Rect(rect.left, height - rect.bottom, rect.right, height - rect.top)
        }

        return copy(
            nodes = nodes.mapValues { (_, node) -> node.copy(rect = flip(node.rect)) },
            edges = edges.map { edge ->
                edge.copy(
                    path = edge.path.copy(
                        startPoint = flip(edge.path.startPoint),
                        endPoint = flip(edge.path.endPoint)
                    ),
                    labelBox = edge.labelBox?.let { it.copy(center = flip(it.center)) }
                )
            },
            groups = groups.map { group ->
                val rect = flip(group.rect)
                // The title stays at the frame's top-left after mirroring, where a reader looks
                // for it — mirroring the anchor would park it outside the frame on the far side.
                group.copy(rect = rect, titleAnchor = Offset(rect.left + 10f, rect.top + 5f))
            }
        )
    }

    companion object {
        fun arrange(
            layout: FlowchartLayoutEngine.Layout,
            nodeIds: List<String>,
            nodeSize: Map<String, IntSize>,
            edges: List<DiagramEdge>,
            horizontal: Boolean,
            availableBounds: IntSize,
            labelWidths: Map<String, Float>,
            /** node id -> subgraph id. Absent means the node belongs to no subgraph. */
            groupOf: Map<String, String> = emptyMap(),
            /** Subgraphs to frame, in declaration order: id, title, member ids. */
            groups: List<Triple<String, String, List<String>>> = emptyList(),
            /**
             * DIA-08: `RL` and `BT` run the *same* axis backwards, so they are laid out
             * normally and mirrored. A second layout engine per sense would be four code paths
             * where the geometry is one.
             */
            reversed: Boolean = false
        ): FlowchartScene {
            if (nodeIds.isEmpty()) return FlowchartScene(emptyMap(), emptyList(), 0, 0)

            val bounds = positionNodes(layout, nodeSize, horizontal, labelWidths, nodeIds, groupOf)
            val (shifted, contentWidth, contentHeight) = normalizeToOrigin(bounds)
            val nodePlacements = shifted.mapValues { (id, rect) -> NodePlacement(id, rect) }
            // Groups first: an edge label must be able to avoid a frame border, which means
            // the frames have to exist before labels are placed.
            val groupPlacements = arrangeGroups(groups, shifted)
            val edgePlacements =
                arrangeEdgesWithLabels(edges, shifted, horizontal, labelWidths, groupPlacements)

            val scene = FlowchartScene(
                nodePlacements, edgePlacements, contentWidth, contentHeight, groupPlacements
            )
            return if (reversed) scene.mirrored(horizontal) else scene
        }

        /** Breathing room between a frame and the nodes it contains. */
        const val GROUP_PADDING = 18f

        /** Height of the strip above a frame that carries its title. */
        const val GROUP_TITLE_BAND = 20f

        /** Resolves each subgraph to the rectangle enclosing its members. */
        private fun arrangeGroups(
            groups: List<Triple<String, String, List<String>>>,
            bounds: Map<String, Rect>
        ): List<GroupPlacement> = groups.mapNotNull { (id, title, memberIds) ->
            val rects = memberIds.mapNotNull { bounds[it] }
            if (rects.isEmpty()) return@mapNotNull null

            val rect = Rect(
                left = rects.minOf { it.left } - GROUP_PADDING,
                top = rects.minOf { it.top } - GROUP_PADDING - GROUP_TITLE_BAND,
                right = rects.maxOf { it.right } + GROUP_PADDING,
                bottom = rects.maxOf { it.bottom } + GROUP_PADDING
            )
            GroupPlacement(id, title, rect, Offset(rect.left + 10f, rect.top + 5f))
        }

        /**
         * Reserved either side of a subgraph frame: padding plus the title band.
         * Kept in step with the renderer so a frame never touches a neighbouring band.
         */
        private const val GROUP_BAND_MARGIN = 58f

        /**
         * Positions every node, giving each subgraph its own cross-axis band.
         *
         * Bands exist because a subgraph frame is drawn as the bounding box of its members.
         * Without them the box lies in two ways, both of which were observed: it overlapped a
         * neighbouring group's frame, and — when a group spanned several layers — it enclosed
         * an unrelated node that merely happened to sit between its members.
         *
         * Reserving a band per group makes the bounding box honest by construction: only that
         * group's members ever occupy its cross-axis range, whatever layer they land in.
         * Diagrams with no subgraphs have a single band and lay out exactly as before.
         */
        private fun positionNodes(
            layout: FlowchartLayoutEngine.Layout,
            nodeSize: Map<String, IntSize>,
            horizontal: Boolean,
            labelWidths: Map<String, Float>,
            nodeIds: List<String>,
            groupOf: Map<String, String>
        ): Map<String, Rect> {
            val siblingGap = with(DesignTokens.Diagram) { flowchartSiblingGap.value }
            val minLaneGap = with(DesignTokens.Diagram) { flowchartMinLaneGap.value }
            val laneGap = kotlin.math.max(minLaneGap, (labelWidths.values.maxOrNull() ?: 0f) + 28f)

            // Band order follows first appearance, so bands read in authoring order.
            val bandOrder = LinkedHashSet<String>()
            nodeIds.forEach { bandOrder.add(groupOf[it] ?: "") }

            // A band's cross extent is its worst layer, so it stays a constant strip.
            val bandExtent = bandOrder.associateWith { band ->
                (0 until layout.layerCount).maxOfOrNull { layerIndex ->
                    val members = layout.layer(layerIndex).filter { (groupOf[it.id] ?: "") == band }
                    if (members.isEmpty()) 0f
                    else members.sumOf { m ->
                        val size = nodeSize[m.id] ?: IntSize.Zero
                        (if (horizontal) size.height else size.width).toDouble()
                    }.toFloat() + siblingGap * (members.size - 1)
                } ?: 0f
            }

            val bandMargin = if (bandOrder.size > 1) GROUP_BAND_MARGIN else 0f
            val totalCross = bandExtent.values.sum() + bandMargin * (bandOrder.size - 1).coerceAtLeast(0)

            val bandStart = mutableMapOf<String, Float>()
            var cursor = -totalCross / 2f
            for (band in bandOrder) {
                bandStart[band] = cursor
                cursor += (bandExtent[band] ?: 0f) + bandMargin
            }

            val bounds = mutableMapOf<String, Rect>()
            var flowCursor = 0f

            for (layerIndex in 0 until layout.layerCount) {
                val members = layout.layer(layerIndex)
                val laneExtent = positionLayer(
                    members, nodeSize, horizontal, siblingGap, flowCursor, bounds,
                    groupOf, bandStart, bandExtent
                )
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
            bounds: MutableMap<String, Rect>,
            groupOf: Map<String, String>,
            bandStart: Map<String, Float>,
            bandExtent: Map<String, Float>
        ): Float {
            val placeables = members.mapNotNull { nodeSize[it.id] }
            if (placeables.isEmpty()) return 0f

            val laneExtent = placeables.maxOf { if (horizontal) it.width else it.height }.toFloat()

            // Each band is laid out independently inside its reserved strip, and centred in it
            // so a layer with fewer members of that band still lines up with the others.
            for ((band, bandMembers) in members.groupBy { groupOf[it.id] ?: "" }) {
                val used = bandMembers.sumOf { m ->
                    val size = nodeSize[m.id] ?: IntSize.Zero
                    (if (horizontal) size.height else size.width).toDouble()
                }.toFloat() + siblingGap * (bandMembers.size - 1)

                val strip = bandExtent[band] ?: used
                var crossCursor = (bandStart[band] ?: -used / 2f) + (strip - used) / 2f

                for (placement in bandMembers) {
                    val placeable = nodeSize[placement.id] ?: continue
                    bounds[placement.id] =
                        positionNodeInLayer(placeable, horizontal, laneExtent, flowCursor, crossCursor)
                    crossCursor += (if (horizontal) placeable.height else placeable.width).toFloat() + siblingGap
                }
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
            labelWidths: Map<String, Float>,
            groups: List<GroupPlacement> = emptyList()
        ): List<EdgePlacement> {
            // Frame borders are obstacles too. An edge crossing into a subgraph would otherwise
            // drop its label straight onto the dashed border, where it is hard to read.
            val borderObstacles = groups.flatMap { group ->
                val r = group.rect
                listOf(
                    Rect(r.left - 6f, r.top, r.left + 6f, r.bottom),
                    Rect(r.right - 6f, r.top, r.right + 6f, r.bottom),
                    Rect(r.left, r.top - 6f, r.right, r.top + 6f),
                    Rect(r.left, r.bottom - 6f, r.right, r.bottom + 6f)
                )
            }
            val placedLabels = mutableListOf<Rect>().apply { addAll(borderObstacles) }
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
