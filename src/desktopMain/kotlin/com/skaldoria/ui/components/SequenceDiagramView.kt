package com.skaldoria.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.diagram.ArrowHead
import com.skaldoria.core.diagram.BlockKind
import com.skaldoria.core.diagram.NotePlacement
import com.skaldoria.core.diagram.SequenceDiagram
import com.skaldoria.core.diagram.SequenceStep
import com.skaldoria.theme.PresentationTheme
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a Mermaid sequence diagram as an actual sequence diagram.
 *
 * MMD-2: the previous renderer emitted an actor chip row, a full-width **table** of
 * `From ➔ To | message` rows, and a duplicate chip row as a footer. It had no lifelines,
 * no time axis and no arrows between participants — which is why sequence diagrams "did
 * not work at all". Everything is drawn on one Canvas so lifelines, arrows, activation
 * bars and block frames share a coordinate system.
 *
 * R-1: the canvas now reports its intrinsic size (not fillMaxSize) so that it can be
 * wrapped in [FitToCanvas] with [FitMode.Contain] to shrink when it overflows.
 */
@Composable
fun SequenceDiagramView(
    diagram: SequenceDiagram,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()

    FitToCanvas(modifier = modifier, fitMode = FitMode.Contain) {
        if (diagram.participants.isEmpty()) {
            Box(modifier = Modifier.size(400.dp, 300.dp))
        } else {
            val rows = remember(diagram) { flatten(diagram.steps) }
            val participants = diagram.participants
            val columnWidthPx = 150f
            val totalWidthPx = SIDE_PADDING * 2 + participants.size * columnWidthPx
            val bodyTop = TOP_PADDING + HEADER_HEIGHT
            val totalHeightPx = bodyTop + rows.size * ROW_HEIGHT + ROW_HEIGHT * 0.5f + HEADER_HEIGHT

            val widthDp = (totalWidthPx / 1.0f).dp
            val heightDp = (totalHeightPx / 1.0f).dp

            Box(
                modifier = Modifier
                    .size(widthDp, heightDp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, theme.cardBorder, RoundedCornerShape(12.dp))
                    .background(theme.surface)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawSequence(diagram, theme, measurer)
                }
            }
        }
    }
}

private const val HEADER_HEIGHT = 44f
private const val ROW_HEIGHT = 54f
private const val TOP_PADDING = 8f
private const val SIDE_PADDING = 16f
private const val SELF_CALL_WIDTH = 46f
private const val ACTIVATION_WIDTH = 10f

/** A step flattened for drawing, carrying the nesting depth it was found at. */
private data class Row(val step: SequenceStep, val depth: Int)

private fun flatten(steps: List<SequenceStep>, depth: Int = 0): List<Row> {
    val rows = mutableListOf<Row>()
    for (step in steps) {
        when (step) {
            is SequenceStep.Block -> {
                rows.add(Row(step, depth))
                rows.addAll(flatten(step.children, depth + 1))
                for (section in step.sections) {
                    rows.add(Row(SequenceStep.Note(NotePlacement.OVER, emptyList(), section.label), depth))
                    rows.addAll(flatten(section.children, depth + 1))
                }
            }
            else -> rows.add(Row(step, depth))
        }
    }
    return rows
}

private fun DrawScope.drawSequence(
    diagram: SequenceDiagram,
    theme: PresentationTheme,
    measurer: TextMeasurer
) {
    val participants = diagram.participants
    val columnWidth = (size.width - SIDE_PADDING * 2) / participants.size
    fun columnCenter(index: Int) = SIDE_PADDING + columnWidth * (index + 0.5f)

    val indexOf = participants.withIndex().associate { (i, p) -> p.id to i }
    val rows = flatten(diagram.steps)

    // Lifelines span the whole body so late participants still read as present from the start.
    val bodyTop = TOP_PADDING + HEADER_HEIGHT
    val bodyBottom = max(bodyTop + ROW_HEIGHT, bodyTop + rows.size * ROW_HEIGHT + ROW_HEIGHT * 0.5f)

    // --- lifelines -------------------------------------------------------
    val dashed = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
    participants.forEachIndexed { index, _ ->
        val x = columnCenter(index)
        drawLine(
            color = theme.textMuted.copy(alpha = 0.55f),
            start = Offset(x, bodyTop),
            end = Offset(x, bodyBottom),
            strokeWidth = 1.5f,
            pathEffect = dashed
        )
    }

    // --- activation bars -------------------------------------------------
    // Opened by `activate`/`+`, closed by `deactivate`/`-`; anything still open at the end
    // runs to the bottom rather than disappearing.
    val openedAt = mutableMapOf<String, Float>()
    rows.forEachIndexed { rowIndex, row ->
        val step = row.step
        if (step is SequenceStep.Activation) {
            val x = indexOf[step.participantId]?.let { columnCenter(it) } ?: return@forEachIndexed
            val y = bodyTop + rowIndex * ROW_HEIGHT
            if (step.active) {
                openedAt[step.participantId] = y
            } else {
                val from = openedAt.remove(step.participantId) ?: bodyTop
                drawRect(
                    color = theme.primary.copy(alpha = 0.30f),
                    topLeft = Offset(x - ACTIVATION_WIDTH / 2f, from),
                    size = Size(ACTIVATION_WIDTH, max(ROW_HEIGHT * 0.5f, y - from))
                )
            }
        }
    }
    for ((id, from) in openedAt) {
        val x = indexOf[id]?.let { columnCenter(it) } ?: continue
        drawRect(
            color = theme.primary.copy(alpha = 0.30f),
            topLeft = Offset(x - ACTIVATION_WIDTH / 2f, from),
            size = Size(ACTIVATION_WIDTH, max(ROW_HEIGHT * 0.5f, bodyBottom - from))
        )
    }

    // --- block frames ----------------------------------------------------
    rows.forEachIndexed { rowIndex, row ->
        val step = row.step
        if (step is SequenceStep.Block) {
            val top = bodyTop + rowIndex * ROW_HEIGHT - ROW_HEIGHT * 0.35f
            val span = countRows(step)
            val height = (span + 1) * ROW_HEIGHT
            drawRect(
                color = theme.primary.copy(alpha = 0.35f),
                topLeft = Offset(SIDE_PADDING * 0.6f, top),
                size = Size(size.width - SIDE_PADDING * 1.2f, height),
                style = Stroke(width = 1.2f)
            )
            val tab = "${step.kind.keyword.uppercase()}${if (step.label.isBlank()) "" else "  ${step.label}"}"
            drawLabel(measurer, tab, Offset(SIDE_PADDING * 0.6f + 6f, top + 4f), theme.primary, 10f, bold = true)
        }
    }

    // --- messages and notes ----------------------------------------------
    var messageNumber = 0
    rows.forEachIndexed { rowIndex, row ->
        val y = bodyTop + rowIndex * ROW_HEIGHT + ROW_HEIGHT * 0.5f
        when (val step = row.step) {
            is SequenceStep.Message -> {
                messageNumber++
                val fromIndex = indexOf[step.fromId] ?: return@forEachIndexed
                val toIndex = indexOf[step.toId] ?: return@forEachIndexed
                val label = if (diagram.autoNumber) "$messageNumber. ${step.text}" else step.text

                if (step.isSelfCall) {
                    drawSelfCall(columnCenter(fromIndex), y, step.arrow.isDashed, step.arrow.head, theme)
                    drawLabel(
                        measurer, label,
                        Offset(columnCenter(fromIndex) + SELF_CALL_WIDTH + 8f, y - 6f),
                        theme.textPrimary, 11f
                    )
                } else {
                    drawMessageArrow(
                        startX = columnCenter(fromIndex),
                        endX = columnCenter(toIndex),
                        y = y,
                        isDashed = step.arrow.isDashed,
                        head = step.arrow.head,
                        color = theme.primary
                    )
                    drawCenteredLabel(
                        measurer, label,
                        centerX = (columnCenter(fromIndex) + columnCenter(toIndex)) / 2f,
                        y = y - 17f,
                        color = theme.textPrimary,
                        maxWidth = kotlin.math.abs(columnCenter(toIndex) - columnCenter(fromIndex)) - 8f
                    )
                }
            }

            is SequenceStep.Note -> {
                val targets = step.participantIds.mapNotNull { indexOf[it] }
                val centerX = if (targets.isEmpty()) {
                    size.width / 2f
                } else {
                    (columnCenter(targets.min()) + columnCenter(targets.max())) / 2f
                }
                val noteWidth = min(size.width * 0.55f, max(140f, step.text.length * 6.5f))
                drawRect(
                    color = theme.surfaceVariant,
                    topLeft = Offset(centerX - noteWidth / 2f, y - 15f),
                    size = Size(noteWidth, 30f)
                )
                drawRect(
                    color = theme.cardBorder,
                    topLeft = Offset(centerX - noteWidth / 2f, y - 15f),
                    size = Size(noteWidth, 30f),
                    style = Stroke(width = 1f)
                )
                drawCenteredLabel(measurer, step.text, centerX, y - 7f, theme.textSecondary, noteWidth - 10f)
            }

            else -> Unit
        }
    }

    // --- participant headers (drawn last so they sit above the lifelines) --
    participants.forEachIndexed { index, participant ->
        val x = columnCenter(index)
        val boxWidth = min(columnWidth - 10f, 150f)
        drawRect(
            color = theme.surfaceVariant,
            topLeft = Offset(x - boxWidth / 2f, TOP_PADDING),
            size = Size(boxWidth, HEADER_HEIGHT - 10f)
        )
        drawRect(
            color = theme.primary,
            topLeft = Offset(x - boxWidth / 2f, TOP_PADDING),
            size = Size(boxWidth, HEADER_HEIGHT - 10f),
            style = Stroke(width = 1.4f)
        )
        drawCenteredLabel(
            measurer, participant.displayName, x, TOP_PADDING + 9f,
            theme.textPrimary, boxWidth - 8f, bold = true, sizeSp = 12f
        )
    }
}

/** Rows a block occupies, including its sections and their headers. */
private fun countRows(block: SequenceStep.Block): Int =
    flatten(block.children).size + block.sections.sumOf { 1 + flatten(it.children).size }

private fun DrawScope.drawMessageArrow(
    startX: Float,
    endX: Float,
    y: Float,
    isDashed: Boolean,
    head: ArrowHead,
    color: Color
) {
    val direction = if (endX >= startX) 1f else -1f
    val tipX = endX - direction * (ACTIVATION_WIDTH / 2f)

    drawLine(
        color = color,
        start = Offset(startX + direction * (ACTIVATION_WIDTH / 2f), y),
        end = Offset(tipX, y),
        strokeWidth = 1.8f,
        cap = StrokeCap.Round,
        pathEffect = if (isDashed) PathEffect.dashPathEffect(floatArrayOf(7f, 5f)) else null
    )
    drawArrowHead(tipX, y, direction, head, color)
}

private fun DrawScope.drawArrowHead(tipX: Float, y: Float, direction: Float, head: ArrowHead, color: Color) {
    val length = 9f
    when (head) {
        ArrowHead.FILLED -> {
            val path = Path().apply {
                moveTo(tipX, y)
                lineTo(tipX - direction * length, y - 5f)
                lineTo(tipX - direction * length, y + 5f)
                close()
            }
            drawPath(path, color)
        }
        ArrowHead.OPEN -> {
            drawLine(color, Offset(tipX, y), Offset(tipX - direction * length, y - 5f), strokeWidth = 1.8f)
            drawLine(color, Offset(tipX, y), Offset(tipX - direction * length, y + 5f), strokeWidth = 1.8f)
        }
        ArrowHead.CROSS -> {
            drawLine(color, Offset(tipX - 5f, y - 5f), Offset(tipX + 5f, y + 5f), strokeWidth = 2f)
            drawLine(color, Offset(tipX - 5f, y + 5f), Offset(tipX + 5f, y - 5f), strokeWidth = 2f)
        }
        ArrowHead.ASYNC -> {
            drawCircle(color, radius = 4.5f, center = Offset(tipX - direction * 4f, y), style = Stroke(width = 1.8f))
        }
    }
}

/** A message a participant sends to itself: out, down, and back. */
private fun DrawScope.drawSelfCall(x: Float, y: Float, isDashed: Boolean, head: ArrowHead, theme: PresentationTheme) {
    val effect = if (isDashed) PathEffect.dashPathEffect(floatArrayOf(7f, 5f)) else null
    val drop = 18f
    val color = theme.primary

    drawLine(color, Offset(x, y - drop / 2f), Offset(x + SELF_CALL_WIDTH, y - drop / 2f), 1.8f, pathEffect = effect)
    drawLine(color, Offset(x + SELF_CALL_WIDTH, y - drop / 2f), Offset(x + SELF_CALL_WIDTH, y + drop / 2f), 1.8f, pathEffect = effect)
    drawLine(color, Offset(x + SELF_CALL_WIDTH, y + drop / 2f), Offset(x + 6f, y + drop / 2f), 1.8f, pathEffect = effect)
    drawArrowHead(x + 6f, y + drop / 2f, -1f, head, color)
}

private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    topLeft: Offset,
    color: Color,
    sizeSp: Float,
    bold: Boolean = false
) {
    if (text.isBlank()) return
    val layout = measurer.measure(
        text = text,
        style = TextStyle(
            color = color,
            fontSize = sizeSp.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.SansSerif
        )
    )
    translate(topLeft.x, topLeft.y) { drawText(layout) }
}

private fun DrawScope.drawCenteredLabel(
    measurer: TextMeasurer,
    text: String,
    centerX: Float,
    y: Float,
    color: Color,
    maxWidth: Float,
    bold: Boolean = false,
    sizeSp: Float = 11f
) {
    if (text.isBlank() || maxWidth <= 12f) return
    val layout = measurer.measure(
        text = text,
        style = TextStyle(
            color = color,
            fontSize = sizeSp.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.SansSerif
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1))
    )
    translate(centerX - layout.size.width / 2f, y) { drawText(layout) }
}
