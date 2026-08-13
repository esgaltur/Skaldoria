package com.skaldoria.canvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.theme.PresentationTheme
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive floating minimap radar overview.
 */
@Composable
fun CanvasMinimap(
    state: CanvasState,
    theme: PresentationTheme,
    screenWidth: Float,
    screenHeight: Float,
    modifier: Modifier = Modifier
) {
    if (state.nodes.isEmpty()) return

    val minimapShape = RoundedCornerShape(8.dp)
    val minimapWidthDp = 180.dp
    val minimapHeightDp = 120.dp
    val density = LocalDensity.current
    val minimapWidth = with(density) { minimapWidthDp.toPx() }
    val minimapHeight = with(density) { minimapHeightDp.toPx() }

    // Calculate canvas bounding box
    var minX = state.nodes.minOf { it.x } - 200f
    var minY = state.nodes.minOf { it.y } - 200f
    var maxX = state.nodes.maxOf { it.x + it.width } + 200f
    var maxY = state.nodes.maxOf { it.y + it.height } + 200f

    val vpRect = state.viewport.visibleCanvasRect(screenWidth, screenHeight)
    minX = min(minX, vpRect.left)
    minY = min(minY, vpRect.top)
    maxX = max(maxX, vpRect.right)
    maxY = max(maxY, vpRect.bottom)

    val canvasW = (maxX - minX).coerceAtLeast(100f)
    val canvasH = (maxY - minY).coerceAtLeast(100f)

    val scaleX = minimapWidth / canvasW
    val scaleY = minimapHeight / canvasH
    val miniScale = min(scaleX, scaleY)

    val offsetX = (minimapWidth - canvasW * miniScale) / 2f
    val offsetY = (minimapHeight - canvasH * miniScale) / 2f

    fun canvasToMinimap(cPos: Offset): Offset {
        return Offset(
            (cPos.x - minX) * miniScale + offsetX,
            (cPos.y - minY) * miniScale + offsetY
        )
    }

    fun minimapToCanvas(mPos: Offset): Offset {
        return Offset(
            (mPos.x - offsetX) / miniScale + minX,
            (mPos.y - offsetY) / miniScale + minY
        )
    }

    Box(
        modifier = modifier
            .size(minimapWidthDp, minimapHeightDp)
            .shadow(8.dp, minimapShape)
            .clip(minimapShape)
            .background(theme.surface.copy(alpha = 0.85f))
            .border(1.dp, theme.cardBorder, minimapShape)
            .pointerInput(Unit) {
                detectTapGestures { tapPos ->
                    val targetCanvas = minimapToCanvas(tapPos)
                    val newPanX = screenWidth / 2f - targetCanvas.x * state.viewport.zoom
                    val newPanY = screenHeight / 2f - targetCanvas.y * state.viewport.zoom
                    state.panBy(Offset(newPanX - state.viewport.panX, newPanY - state.viewport.panY))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val deltaCanvas = Offset(dragAmount.x / miniScale, dragAmount.y / miniScale)
                    state.panBy(Offset(-deltaCanvas.x * state.viewport.zoom, -deltaCanvas.y * state.viewport.zoom))
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw mini nodes
            state.nodes.forEach { node ->
                val miniPos = canvasToMinimap(Offset(node.x, node.y))
                val miniW = max(3f, node.width * miniScale)
                val miniH = max(3f, node.height * miniScale)

                drawRoundRect(
                    color = node.color.accent(theme.isDark).copy(alpha = 0.7f),
                    topLeft = miniPos,
                    size = Size(miniW, miniH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                )
            }

            // Draw mini viewport rectangle
            val vpTopLeft = canvasToMinimap(Offset(vpRect.left, vpRect.top))
            val vpW = vpRect.width * miniScale
            val vpH = vpRect.height * miniScale

            drawRoundRect(
                color = theme.primary.copy(alpha = 0.15f),
                topLeft = vpTopLeft,
                size = Size(vpW, vpH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
            )
            drawRoundRect(
                color = theme.primary,
                topLeft = vpTopLeft,
                size = Size(vpW, vpH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                style = Stroke(width = 1.5f)
            )
        }
    }
}
