package com.skaldoria.canvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.skaldoria.canvas.model.*
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.theme.PresentationTheme

/**
 * Renders all graph connection edges, arrows, label pills, and active drag connection previews.
 */
@Composable
fun CanvasEdgeRenderer(
    state: CanvasState,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val nodeMap = state.nodes.associateBy { it.id }

    Canvas(modifier = modifier.fillMaxSize()) {
        val defaultEdgeColor = if (theme.isDark) {
            Color.White.copy(alpha = 0.55f)
        } else {
            Color(0xFF475569)
        }

        // 1. Draw existing edges
        state.edges.forEach { edge ->
            val fromNode = nodeMap[edge.fromNodeId] ?: return@forEach
            val toNode = nodeMap[edge.toNodeId] ?: return@forEach

            val (startCanvas, endCanvas) = CanvasGeometry.resolvePorts(
                fromNode, toNode, edge.fromPort, edge.toPort
            )

            val startScreen = state.viewport.canvasToScreen(startCanvas)
            val endScreen = state.viewport.canvasToScreen(endCanvas)

            val isSelected = state.selectedEdgeId == edge.id
            val edgeColor = when {
                isSelected -> theme.primary
                edge.color != null -> edge.color.accent(theme.isDark)
                else -> defaultEdgeColor
            }

            val strokeWidth = ((if (isSelected) 3.5f else 2.2f) * state.viewport.zoom).coerceIn(1.5f, 6f)

            val pathEffect = when (edge.style) {
                EdgeStyle.Solid -> null
                EdgeStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(14f * state.viewport.zoom, 8f * state.viewport.zoom), 0f)
                EdgeStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(4f * state.viewport.zoom, 6f * state.viewport.zoom), 0f)
            }

            // Draw Bezier spline
            val bezier = CanvasGeometry.bezierBetween(startScreen, endScreen)
            val bezierPath = CanvasGeometry.buildBezierPath(bezier)
            drawPath(
                path = bezierPath,
                color = edgeColor,
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = pathEffect,
                    cap = StrokeCap.Round
                )
            )

            // Draw Arrowhead
            val arrowheadPoints = CanvasGeometry.computeArrowhead(
                start = bezier.control2,
                target = endScreen,
                arrowLength = 14f * state.viewport.zoom.coerceIn(0.6f, 2f),
                arrowAngleDeg = 26f
            )

            val arrowPath = Path().apply {
                moveTo(arrowheadPoints[0].x, arrowheadPoints[0].y)
                lineTo(arrowheadPoints[1].x, arrowheadPoints[1].y)
                lineTo(arrowheadPoints[2].x, arrowheadPoints[2].y)
                close()
            }
            drawPath(path = arrowPath, color = edgeColor)

            // Draw Edge Label badge if present
            if (edge.label.isNotBlank()) {
                drawEdgeLabel(
                    label = edge.label,
                    start = startScreen,
                    end = endScreen,
                    theme = theme,
                    textMeasurer = textMeasurer,
                    zoom = state.viewport.zoom
                )
            }
        }

        // 2. Draw active connecting line preview
        val connectingSourceId = state.connectingSourceNodeId
        val connectingTargetPos = state.connectingTargetPosition
        if (connectingSourceId != null && connectingTargetPos != null) {
            val sourceNode = nodeMap[connectingSourceId]
            if (sourceNode != null) {
                val sourcePortCanvas = sourceNode.portPosition(state.connectingSourcePort)
                val startScreen = state.viewport.canvasToScreen(sourcePortCanvas)
                val endScreen = connectingTargetPos

                val previewPath = CanvasGeometry.buildBezierPath(startScreen, endScreen)
                drawPath(
                    path = previewPath,
                    color = theme.primary.copy(alpha = 0.85f),
                    style = Stroke(
                        width = 2.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f),
                        cap = StrokeCap.Round
                    )
                )

                drawCircle(
                    color = theme.primary,
                    radius = 6f,
                    center = endScreen
                )
            }
        }
    }
}

private fun DrawScope.drawEdgeLabel(
    label: String,
    start: Offset,
    end: Offset,
    theme: PresentationTheme,
    textMeasurer: TextMeasurer,
    zoom: Float
) {
    val mid = CanvasGeometry.calculateMidpoint(start, end)
    val textStyle = TextStyle(
        color = theme.textPrimary,
        fontSize = (11f * zoom).coerceIn(9f, 15f).sp
    )
    val textLayout = textMeasurer.measure(label, textStyle)

    val paddingH = 8f * zoom
    val paddingV = 4f * zoom
    val pillW = textLayout.size.width + paddingH * 2
    val pillH = textLayout.size.height + paddingV * 2

    val pillTopLeft = Offset(mid.x - pillW / 2f, mid.y - pillH / 2f)

    // Pill background
    drawRoundRect(
        color = theme.surface,
        topLeft = pillTopLeft,
        size = androidx.compose.ui.geometry.Size(pillW, pillH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * zoom, 6f * zoom)
    )
    drawRoundRect(
        color = theme.cardBorder,
        topLeft = pillTopLeft,
        size = androidx.compose.ui.geometry.Size(pillW, pillH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * zoom, 6f * zoom),
        style = Stroke(width = 1f * zoom)
    )

    // Pill Text
    drawText(
        textLayoutResult = textLayout,
        topLeft = Offset(pillTopLeft.x + paddingH, pillTopLeft.y + paddingV)
    )
}
