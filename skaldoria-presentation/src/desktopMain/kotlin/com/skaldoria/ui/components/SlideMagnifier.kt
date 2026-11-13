package com.skaldoria.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp

private val LENS_RADIUS = 160.dp
private val LENS_BORDER = 3.dp

/**
 * A magnifying lens that follows the pointer over [content].
 *
 * The content is drawn once into a graphics layer and the lens redraws that same layer scaled
 * around the pointer, so the slide is not composed twice. [overlay] (laser, pen strokes) is
 * drawn above the lens at normal size, so annotations keep matching the pointer.
 *
 * @param onZoomSteps mouse wheel while active: +1 per notch up (zoom in), -1 per notch down.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SlideMagnifier(
    active: Boolean,
    zoom: Float,
    lensBackground: Color,
    lensBorder: Color,
    onZoomSteps: (Int) -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    var pointer by remember { mutableStateOf(Offset.Unspecified) }
    val layer = rememberGraphicsLayer()

    Box(
        modifier = modifier
            .onPointerEvent(PointerEventType.Move) { event ->
                event.changes.firstOrNull()?.let { pointer = it.position }
            }
            .onPointerEvent(PointerEventType.Exit) { pointer = Offset.Unspecified }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                if (!active) return@onPointerEvent
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val dy = change.scrollDelta.y
                if (dy != 0f) {
                    onZoomSteps(if (dy < 0f) 1 else -1)
                    change.consume()
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)

                    if (!active || pointer == Offset.Unspecified) return@drawWithContent
                    val radius = LENS_RADIUS.toPx()
                    val lens = Path().apply { addOval(Rect(center = pointer, radius = radius)) }
                    clipPath(lens) {
                        drawRect(lensBackground)
                        // Scale about the pointer: the point under the cursor stays under it.
                        translate(pointer.x * (1 - zoom), pointer.y * (1 - zoom)) {
                            scale(zoom, zoom, pivot = Offset.Zero) { drawLayer(layer) }
                        }
                    }
                    drawCircle(lensBorder, radius, pointer, style = Stroke(LENS_BORDER.toPx()))
                }
        ) {
            content()
        }
        overlay()
    }
}
