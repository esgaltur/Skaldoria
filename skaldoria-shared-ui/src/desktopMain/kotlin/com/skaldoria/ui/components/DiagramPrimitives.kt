package com.skaldoria.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.skaldoria.core.theme.DesignTokens
import com.skaldoria.theme.PresentationTheme

/**
 * Shared primitives for diagram rendering: arrowheads, edge paths, and label boxes.
 *
 * This centralizes the divergent implementations that previously existed in
 * FlowchartGraphView and SequenceDiagramView, ensuring consistent arrow style,
 * dash patterns, and label appearance across all diagrams.
 */

/**
 * Draws an arrowhead at the given tip point, pointing in a cardinal direction.
 *
 * @param tip the arrow's tip position
 * @param direction 1f for right/down, -1f for left/up
 * @param isHorizontal if true, arrow points horizontally; if false, vertically
 */
fun DrawScope.drawArrowHead(
    tip: Offset,
    direction: Float,
    isHorizontal: Boolean,
    color: Color,
    length: Float = DesignTokens.DrawPrimitive.arrowheadLength,
    spread: Float = DesignTokens.DrawPrimitive.arrowheadSpread
) {
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        if (isHorizontal) {
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

/**
 * Draws a horizontal or vertical edge path with optional dashing.
 *
 * @param from start point
 * @param to end point
 * @param isDashed if true, applies a dashed line effect
 * @param dashPattern the dash array (e.g., [8f, 6f]); ignored if isDashed is false
 */
fun DrawScope.drawEdgePath(
    from: Offset,
    to: Offset,
    isDashed: Boolean,
    dashPattern: FloatArray = DesignTokens.DrawPrimitive.dashFlowchartEdge,
    color: Color,
    strokeWidth: Float = DesignTokens.DrawPrimitive.strokeWidth
) {
    val isHorizontal = kotlin.math.abs(to.y - from.y) < 0.01f
    val path = Path().apply {
        moveTo(from.x, from.y)
        if (isHorizontal) {
            val midX = (from.x + to.x) / 2f
            lineTo(midX, from.y)
            lineTo(midX, to.y)
        } else {
            val midY = (from.y + to.y) / 2f
            lineTo(from.x, midY)
            lineTo(to.x, midY)
        }
        lineTo(to.x, to.y)
    }
    val effect = if (isDashed) PathEffect.dashPathEffect(dashPattern) else null
    drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, pathEffect = effect))
}

/**
 * Draws a label box (rounded rectangle with text) centered at the given position.
 *
 * @param center the center position of the label box
 * @param text the label text
 * @param measurer TextMeasurer for measuring the text
 * @param theme the presentation theme (provides colors)
 */
fun DrawScope.drawLabelChip(
    center: Offset,
    text: String,
    measurer: TextMeasurer,
    theme: PresentationTheme
) {
    if (text.isBlank()) return

    val layoutResult = measurer.measure(
        text = text,
        style = TextStyle(
            color = theme.primary,
            fontSize = DesignTokens.DiagramText.edgeLabelFontSize,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        ),
        maxLines = 1
    )

    val boxWidth = layoutResult.size.width + DesignTokens.DrawPrimitive.edgeLabelBoxPadding
    val boxHeight = layoutResult.size.height + DesignTokens.DrawPrimitive.edgeLabelBoxVerticalPadding

    drawRect(
        color = theme.surfaceVariant,
        topLeft = Offset(center.x - boxWidth / 2f, center.y - boxHeight / 2f),
        size = androidx.compose.ui.geometry.Size(boxWidth, boxHeight)
    )
    translate(
        left = center.x - layoutResult.size.width / 2f,
        top = center.y - layoutResult.size.height / 2f
    ) { drawText(layoutResult) }
}

/**
 * Draws a sequence diagram message arrow (horizontal line with an arrowhead).
 *
 * @param startX x coordinate of the arrow's start
 * @param endX x coordinate of the arrow's end
 * @param y y coordinate (horizontal flow)
 * @param isDashed if true, applies dashing
 * @param color the line color
 */
fun DrawScope.drawSequenceMessageArrow(
    startX: Float,
    endX: Float,
    y: Float,
    isDashed: Boolean,
    color: Color
) {
    val direction = if (endX >= startX) 1f else -1f
    val tipX = endX - direction * (DesignTokens.Diagram.sequenceActivationWidth / 2f)

    drawLine(
        color = color,
        start = Offset(startX + direction * (DesignTokens.Diagram.sequenceActivationWidth / 2f), y),
        end = Offset(tipX, y),
        strokeWidth = 1.8f,
        cap = StrokeCap.Round,
        pathEffect = if (isDashed) PathEffect.dashPathEffect(DesignTokens.DrawPrimitive.dashSequenceMessage) else null
    )
    drawArrowHead(Offset(tipX, y), direction, isHorizontal = true, color, length = 9f, spread = 5.5f)
}
