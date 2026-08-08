package com.skaldoria.canvas.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.*

/**
 * Geometric calculations for spatial canvas nodes, edge routing, and viewport culling.
 */
object CanvasGeometry {

    /**
     * Resolves the best source and destination ports for an edge connecting [from] to [to].
     */
    fun resolvePorts(from: CanvasNode, to: CanvasNode, fromPort: EdgePort, toPort: EdgePort): Pair<Offset, Offset> {
        val start = if (fromPort == EdgePort.Auto) {
            autoSourcePort(from, to)
        } else {
            from.portPosition(fromPort)
        }

        val end = if (toPort == EdgePort.Auto) {
            autoTargetPort(from, to)
        } else {
            to.portPosition(toPort)
        }

        return Pair(start, end)
    }

    private fun autoSourcePort(from: CanvasNode, to: CanvasNode): Offset {
        val dx = to.center.x - from.center.x
        val dy = to.center.y - from.center.y
        return if (abs(dx) >= abs(dy)) {
            if (dx > 0) from.portPosition(EdgePort.Right) else from.portPosition(EdgePort.Left)
        } else {
            if (dy > 0) from.portPosition(EdgePort.Bottom) else from.portPosition(EdgePort.Top)
        }
    }

    private fun autoTargetPort(from: CanvasNode, to: CanvasNode): Offset {
        val dx = to.center.x - from.center.x
        val dy = to.center.y - from.center.y
        return if (abs(dx) >= abs(dy)) {
            if (dx > 0) to.portPosition(EdgePort.Left) else to.portPosition(EdgePort.Right)
        } else {
            if (dy > 0) to.portPosition(EdgePort.Top) else to.portPosition(EdgePort.Bottom)
        }
    }

    /**
     * Builds a smooth cubic Bezier curve path connecting [start] to [end].
     */
    fun buildBezierPath(start: Offset, end: Offset): Path {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dist = hypot(dx, dy)
        val curvature = min(dist * 0.5f, 150f).coerceAtLeast(30f)

        val cp1 = if (abs(dx) > abs(dy)) {
            Offset(start.x + (if (dx >= 0) curvature else -curvature), start.y)
        } else {
            Offset(start.x, start.y + (if (dy >= 0) curvature else -curvature))
        }

        val cp2 = if (abs(dx) > abs(dy)) {
            Offset(end.x - (if (dx >= 0) curvature else -curvature), end.y)
        } else {
            Offset(end.x, end.y - (if (dy >= 0) curvature else -curvature))
        }

        return Path().apply {
            moveTo(start.x, start.y)
            cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, end.x, end.y)
        }
    }

    /**
     * Calculates the midpoint of a cubic Bezier curve between [start] and [end] for placing edge label pills.
     */
    fun calculateMidpoint(start: Offset, end: Offset): Offset {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dist = hypot(dx, dy)
        val curvature = min(dist * 0.5f, 150f).coerceAtLeast(30f)

        val cp1 = if (abs(dx) > abs(dy)) {
            Offset(start.x + (if (dx >= 0) curvature else -curvature), start.y)
        } else {
            Offset(start.x, start.y + (if (dy >= 0) curvature else -curvature))
        }

        val cp2 = if (abs(dx) > abs(dy)) {
            Offset(end.x - (if (dx >= 0) curvature else -curvature), end.y)
        } else {
            Offset(end.x, end.y - (if (dy >= 0) curvature else -curvature))
        }

        // Evaluate cubic bezier at t = 0.5
        val t = 0.5f
        val u = 1f - t
        val tt = t * t
        val uu = u * u
        val uuu = uu * u
        val ttt = tt * t

        val x = uuu * start.x + 3 * uu * t * cp1.x + 3 * u * tt * cp2.x + ttt * end.x
        val y = uuu * start.y + 3 * uu * t * cp1.y + 3 * u * tt * cp2.y + ttt * end.y
        return Offset(x, y)
    }

    /**
     * Computes the 3 vertices of an arrowhead polygon at [target] pointed in the incoming tangent direction.
     */
    fun computeArrowhead(start: Offset, target: Offset, arrowLength: Float = 12f, arrowAngleDeg: Float = 28f): List<Offset> {
        val angle = atan2(target.y - start.y, target.x - start.x)
        val rad = Math.toRadians(arrowAngleDeg.toDouble()).toFloat()

        val leftWing = Offset(
            target.x - arrowLength * cos(angle - rad),
            target.y - arrowLength * sin(angle - rad)
        )
        val rightWing = Offset(
            target.x - arrowLength * cos(angle + rad),
            target.y - arrowLength * sin(angle + rad)
        )

        return listOf(target, leftWing, rightWing)
    }

    /**
     * Determines whether a point is close to the line segment between [start] and [end].
     */
    fun isPointNearSegment(point: Offset, start: Offset, end: Offset, threshold: Float = 16f): Boolean {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSq = dx * dx + dy * dy
        if (lengthSq == 0f) return hypot(point.x - start.x, point.y - start.y) <= threshold

        val t = ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSq
        val clampedT = t.coerceIn(0f, 1f)
        val projX = start.x + clampedT * dx
        val projY = start.y + clampedT * dy
        return hypot(point.x - projX, point.y - projY) <= threshold
    }

    /**
     * Determines if a node's bounding rectangle intersects with the visible viewport rectangle (with padding).
     */
    fun isNodeVisible(node: CanvasNode, viewportRect: Rect, margin: Float = 100f): Boolean {
        val paddedViewport = Rect(
            viewportRect.left - margin,
            viewportRect.top - margin,
            viewportRect.right + margin,
            viewportRect.bottom + margin
        )
        return node.bounds.overlaps(paddedViewport)
    }
}
