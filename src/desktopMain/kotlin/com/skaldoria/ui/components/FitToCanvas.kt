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
 * ## Only wrap intrinsically-sized content
 *
 * This measures with `maxHeight = Constraints.Infinity` to discover the content's natural
 * height. Any child that sizes itself with `Modifier.weight(1f)` or `fillMaxHeight()` gets
 * **zero** height under an unbounded main axis, and disappears.
 *
 * That is not hypothetical: wrapping this around the whole slide (OVF-1) blanked every
 * slide in the app, because every layout sizes its content area with `weight(1f)`. Wrap
 * content that measures to its own natural size — a diagram, a list of cards — never a
 * layout designed to fill its parent. `SlideRenderingTest` guards against the regression.
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
        // An unbounded parent gives nothing to fit against, and passing Infinity to
        // layout() throws. Fall back to wrapping the content at scale 1 — degrading to
        // "no fitting" is correct here, whereas crashing mid-presentation is not.
        val boundedWidth = constraints.hasBoundedWidth
        val boundedHeight = constraints.hasBoundedHeight

        // Width-bound, height-free: let text and flow layouts wrap at the real width.
        fun measureAt(width: Int): List<androidx.compose.ui.layout.Placeable> =
            subcompose(width) { content() }.map {
                it.measure(
                    Constraints(
                        maxWidth = if (boundedWidth) width else Constraints.Infinity,
                        minHeight = 0,
                        maxHeight = Constraints.Infinity
                    )
                )
            }

        var placeables = measureAt(constraints.maxWidth)
        var contentWidth = placeables.maxOfOrNull { it.width } ?: 0
        var contentHeight = placeables.maxOfOrNull { it.height } ?: 0

        var availableWidth = if (boundedWidth) constraints.maxWidth else contentWidth
        var availableHeight = if (boundedHeight) constraints.maxHeight else contentHeight

        // Uniform scaling shrinks width as well as height, so a full-width list ends up as
        // a narrow strip with the sides unused. Re-measure at width/scale: laid out wider,
        // the content is shorter, and once scaled back down it fills the available width.
        val firstPass = SlideCanvasFit.contentScale(
            contentWidth, contentHeight, availableWidth, availableHeight, minScale
        )
        if (firstPass < 1f && boundedWidth && contentWidth > 0) {
            val widerWidth = (availableWidth / firstPass).roundToInt()
            val refined = measureAt(widerWidth)
            val refinedWidth = refined.maxOfOrNull { it.width } ?: 0
            val refinedHeight = refined.maxOfOrNull { it.height } ?: 0
            if (refinedWidth > 0 && refinedHeight > 0) {
                placeables = refined
                contentWidth = refinedWidth
                contentHeight = refinedHeight
                availableWidth = if (boundedWidth) constraints.maxWidth else contentWidth
                availableHeight = if (boundedHeight) constraints.maxHeight else contentHeight
            }
        }

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
                // Scale about the TOP-LEFT and offset by the *scaled* size to centre.
                //
                // Centring the unscaled placeable and scaling about its centre is the
                // obvious approach and it does not place correctly for oversized content —
                // the pre-scale offset goes far negative and the result lands off-centre.
                // Anchoring at the top-left keeps placement in scaled coordinates, where
                // the arithmetic is exact.
                val scaledWidth = placeable.width * scale
                val scaledHeight = placeable.height * scale
                val x = ((availableWidth - scaledWidth) / 2f).roundToInt()
                val y = ((availableHeight - scaledHeight) / 2f).roundToInt()
                placeable.placeWithLayer(x, y) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}
