package com.skaldoria.canvas.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.*

/**
 * Geometric calculations for spatial canvas nodes, edge routing, and viewport culling.
 */
object CanvasGeometry {

    /** Complete screen-space geometry for one cubic Bézier edge. */
    data class CubicBezier(
        val start: Offset,
        val control1: Offset,
        val control2: Offset,
        val end: Offset
    ) {
        fun pointAt(t: Float): Offset {
            val clampedT = t.coerceIn(0f, 1f)
            val u = 1f - clampedT
            val uu = u * u
            val tt = clampedT * clampedT
            return Offset(
                x = uu * u * start.x + 3f * uu * clampedT * control1.x +
                    3f * u * tt * control2.x + tt * clampedT * end.x,
                y = uu * u * start.y + 3f * uu * clampedT * control1.y +
                    3f * u * tt * control2.y + tt * clampedT * end.y
            )
        }
    }

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
    fun bezierBetween(start: Offset, end: Offset): CubicBezier {
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

        return CubicBezier(start, cp1, cp2, end)
    }

    fun buildBezierPath(start: Offset, end: Offset): Path =
        buildBezierPath(bezierBetween(start, end))

    fun buildBezierPath(curve: CubicBezier): Path = Path().apply {
        moveTo(curve.start.x, curve.start.y)
        cubicTo(
            curve.control1.x,
            curve.control1.y,
            curve.control2.x,
            curve.control2.y,
            curve.end.x,
            curve.end.y
        )
    }

    /**
     * Calculates the midpoint of a cubic Bezier curve between [start] and [end] for placing edge label pills.
     */
    fun calculateMidpoint(start: Offset, end: Offset): Offset =
        bezierBetween(start, end).pointAt(0.5f)

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
     * Determines whether a point is close to the cubic Bezier curve connecting [start] and [end].
     */
    fun isPointNearBezier(point: Offset, start: Offset, end: Offset, threshold: Float = 14f): Boolean {
        val curve = bezierBetween(start, end)
        val steps = 20
        var prev = start
        for (step in 1..steps) {
            val current = curve.pointAt(step / steps.toFloat())

            if (isPointNearSegment(point, prev, current, threshold)) {
                return true
            }
            prev = current
        }
        return false
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
