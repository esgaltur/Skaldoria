package com.skaldoria.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import com.skaldoria.core.layout.SlideCanvasFit
import kotlin.math.roundToInt

/**
 * Shrinks its content uniformly until it fits, instead of letting it clip.
 *
 * Slides are authored against a fixed 1280x720 design canvas (see `SlideSurface`), so
 * available space does *not* grow with the display — a slide with too many bullets, or a
 * diagram with too many nodes, silently ran off the edge on every screen size. This wraps
 * the content once, at the top of the slide tree, so every layout benefits without each
 * one re-solving overflow.
 *
 * Measurement is width-constrained and height-free on purpose. Measuring unbounded in both
 * axes would stop text from ever wrapping, so a paragraph would report an enormous natural
 * width and get scaled to nothing. Constraining width lets text and `FlowRow` wrap normally,
 * and only genuine vertical overflow drives the scale down.
 *
 * @param minScale legibility floor; below this the content is left clipped and
 *   [onScaleComputed] reports the overflow rather than shrinking into unreadability.
 * @param onScaleComputed invoked with the applied scale whenever it changes; use it to
 *   surface an authoring warning. Not invoked on every frame — only on scale change.
 */
@Composable
fun FitToCanvas(
    modifier: Modifier = Modifier,
    minScale: Float = SlideCanvasFit.DEFAULT_MIN_SCALE,
    onScaleComputed: ((scale: Float, overflowing: Boolean) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    SubcomposeLayout(modifier) { constraints ->
        // Width-bound, height-free: let text and flow layouts wrap at the real width.
        val measureConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = subcompose(Unit, content).map { it.measure(measureConstraints) }

        val contentWidth = placeables.maxOfOrNull { it.width } ?: 0
        val contentHeight = placeables.maxOfOrNull { it.height } ?: 0

        // An unbounded parent gives nothing to fit against, and passing Infinity to
        // layout() throws. Fall back to wrapping the content at scale 1 — degrading to
        // "no fitting" is correct here, whereas crashing mid-presentation is not.
        val availableWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else contentWidth
        val availableHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else contentHeight

        val scale = SlideCanvasFit.contentScale(
            contentWidth = contentWidth,
            contentHeight = contentHeight,
            availableWidth = availableWidth,
            availableHeight = availableHeight,
            minScale = minScale
        )
        val overflowing = SlideCanvasFit.isOverflowing(
            contentWidth = contentWidth,
            contentHeight = contentHeight,
            availableWidth = availableWidth,
            availableHeight = availableHeight,
            minScale = minScale
        )
        onScaleComputed?.invoke(scale, overflowing)

        layout(availableWidth, availableHeight) {
            placeables.forEach { placeable ->
                // Centre the placeable, then scale about its own centre. For oversized
                // content the offset goes negative, which keeps the visual centre pinned
                // to the container centre so the shrink reads as symmetric.
                val x = ((availableWidth - placeable.width) / 2f).roundToInt()
                val y = ((availableHeight - placeable.height) / 2f).roundToInt()
                placeable.placeWithLayer(x, y) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                }
            }
        }
    }
}
