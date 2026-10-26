package com.skaldoria.canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.ExperimentalComposeUiApi
import com.skaldoria.canvas.model.CanvasCamera
import com.skaldoria.canvas.state.CanvasPresentation
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.theme.PresentationTheme

/** How long one hop between stops takes. Long enough to follow, short enough not to wait for. */
private const val FLIGHT_MILLIS = 900L

/**
 * Everything the presentation adds on top of the board: the camera flight, the input shield and
 * the progress pill.
 *
 * The shield sits above the cards and takes every pointer event, so nothing on the board can be
 * dragged, resized or opened for editing mid-talk. A click advances; a right-click goes back.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BoxScope.CanvasPresentationLayer(
    presentation: CanvasPresentation,
    state: CanvasState,
    canvasSize: Size,
    theme: PresentationTheme
) {
    // Re-runs on every stop change and on resize (entering full screen is one). Starting from
    // the camera's current position means an interrupted flight turns smoothly, never jumps.
    LaunchedEffect(presentation, presentation.index, canvasSize) {
        val from = presentation.camera
        val to = presentation.targetCamera(state.nodes, canvasSize.width, canvasSize.height)
        val start = withFrameNanos { it }
        var progress = 0f
        while (progress < 1f) {
            progress = withFrameNanos { now -> ((now - start) / 1_000_000f / FLIGHT_MILLIS).coerceAtMost(1f) }
            presentation.camera = CanvasCamera.fly(
                from, to, CanvasCamera.ease(progress), canvasSize.width, canvasSize.height
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .testTag(CanvasTestTags.PresentationShield)
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.button == PointerButton.Secondary) presentation.previous()
            }
            .pointerInput(presentation) {
                detectTapGestures(onTap = { presentation.next() })
            }
    )

    Text(
        text = "${presentation.index + 1} / ${presentation.stops.size}   ·   ← →  move   ·   Esc  exit",
        color = theme.textSecondary,
        fontSize = 12.sp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 20.dp)
            .background(theme.surface.copy(alpha = 0.75f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag(CanvasTestTags.PresentationProgress)
    )
}
