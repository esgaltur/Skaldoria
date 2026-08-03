package com.markdownpres.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import com.markdownpres.core.models.AnnotationStroke
import com.markdownpres.state.PresentationState

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SlideAnnotationOverlay(
    state: PresentationState,
    modifier: Modifier = Modifier
) {
    var currentLaserPos by remember { mutableStateOf(Offset.Unspecified) }
    var activeStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPointerEvent(PointerEventType.Move) { event ->
                val change = event.changes.firstOrNull()
                if (change != null && state.isLaserPointerActive) {
                    currentLaserPos = change.position
                }
            }
            .then(
                if (state.isPenDrawingActive) {
                    Modifier.pointerInput(state.currentSlideIndex) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                activeStrokePoints = listOf(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                activeStrokePoints = activeStrokePoints + change.position
                            },
                            onDragEnd = {
                                if (activeStrokePoints.size > 1) {
                                    state.addStroke(
                                        AnnotationStroke(
                                            points = activeStrokePoints,
                                            color = state.currentPenColor,
                                            strokeWidth = 4f
                                        )
                                    )
                                }
                                activeStrokePoints = emptyList()
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        val slideStrokes = state.currentSlideStrokes

        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. Draw saved annotation strokes for current slide
            slideStrokes.forEach { stroke ->
                if (stroke.points.size > 1) {
                    val path = Path().apply {
                        moveTo(stroke.points.first().x, stroke.points.first().y)
                        for (i in 1 until stroke.points.size) {
                            lineTo(stroke.points[i].x, stroke.points[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = stroke.color,
                        style = Stroke(
                            width = stroke.strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            // 2. Draw live active stroke being drawn
            if (activeStrokePoints.size > 1) {
                val livePath = Path().apply {
                    moveTo(activeStrokePoints.first().x, activeStrokePoints.first().y)
                    for (i in 1 until activeStrokePoints.size) {
                        lineTo(activeStrokePoints[i].x, activeStrokePoints[i].y)
                    }
                }
                drawPath(
                    path = livePath,
                    color = state.currentPenColor,
                    style = Stroke(
                        width = 4f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 3. Draw Laser Pointer Dot & Glow
            if (state.isLaserPointerActive && currentLaserPos != Offset.Unspecified) {
                // Outer glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF1744).copy(alpha = 0.8f),
                            Color(0xFFFF5252).copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        center = currentLaserPos,
                        radius = 24f
                    ),
                    radius = 24f,
                    center = currentLaserPos
                )
                // Core bright center
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = currentLaserPos
                )
            }
        }
    }
}
