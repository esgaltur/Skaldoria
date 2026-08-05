package com.skaldoria.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeMeasureScope
import androidx.compose.ui.unit.Constraints
import com.skaldoria.core.layout.SlideCanvasFit
import kotlin.math.roundToInt

/**
 * How [FitToCanvas] discovers the size of the content it has to fit.
 *
 * The two strategies are not interchangeable — a caller must pick the one that matches the
 * *shape* of its content, because measuring content the wrong way yields a nonsense size:
 *
 *  - [Height] measures width-bounded so text, `FlowRow` and bullet lists wrap normally, and
 *    only genuine vertical overflow drives the scale down. Use for reflowing content.
 *  - [Contain] measures both axes unbounded to read the content's own intrinsic size, then
 *    scales it to fit inside the available box (the classic "contain" fit). Use **only** for
 *    content with a fixed intrinsic size — a diagram, a `Modifier.size(...)` box. Reflowing
 *    content measured this way reports one enormous single line and scales to nothing.
 */
enum class FitMode { Height, Contain }

/**
 * Shrinks its content uniformly until it fits, instead of letting it clip.
 *
 * ## Never wrap a fill/weight child
 *
 * Both strategies measure with `maxHeight = Constraints.Infinity` to read the content's
 * natural height. A child that sizes itself with `Modifier.weight(1f)` or `fillMaxHeight()`
 * gets **zero** height under an unbounded main axis and disappears. That is not hypothetical:
 * wrapping this around a whole slide (OVF-1) blanked every slide, because every layout sizes
 * its content area with `weight(1f)`. Wrap content that measures to its own natural size — a
 * diagram, a list of cards — never a layout designed to fill its parent. `SlideRenderingTest`
 * guards against the regression.
 *
 * Slides are authored against a fixed 1280x720 design canvas (see `SlideSurface`), so
 * available space does *not* grow with the display — a slide with too many bullets, or a
 * diagram with too many nodes, silently ran off the edge on every screen size. This wraps the
 * content once so every layout benefits without each one re-solving overflow.
 *
 * The scale itself is a plain calculation, not a heuristic: [SlideCanvasFit.contentScale]
 * returns the uniform "contain" ratio for the measured content against the available box.
 * Getting it right only depends on measuring the content honestly, which is what [FitMode]
 * selects.
 *
 * @param fitMode how to measure the content — see [FitMode]. Defaults to [FitMode.Height],
 *   the safe choice for reflowing content.
 * @param minScale legibility floor; below this the content is left clipped and
 *   [onScaleComputed] reports the overflow rather than shrinking into unreadability.
 * @param onScaleComputed invoked with the applied scale whenever it changes; use it to
 *   surface an authoring warning. Not invoked on every frame — only on scale change.
 */
@Composable
fun FitToCanvas(
    modifier: Modifier = Modifier,
    minScale: Float = SlideCanvasFit.DEFAULT_MIN_SCALE,
    fitMode: FitMode = FitMode.Height,
    onScaleComputed: ((scale: Float, overflowing: Boolean) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    SubcomposeLayout(modifier) { constraints ->
        val fit = measureFitted(constraints, minScale, fitMode, content)
        onScaleComputed?.invoke(fit.scale, fit.overflowing)

        layout(fit.availableWidth, fit.availableHeight) {
            fit.placeables.forEach { placeCentredAndScaled(it, fit) }
        }
    }
}

/** A measured, scaled, ready-to-place result of the fit pipeline. */
private class FitResult(
    val placeables: List<Placeable>,
    val availableWidth: Int,
    val availableHeight: Int,
    val scale: Float,
    val overflowing: Boolean
)

/** Placeables plus the max width/height across them, in one bundle. */
private class Measured(val placeables: List<Placeable>, val width: Int, val height: Int)

/**
 * Measures [content] the way [fitMode] dictates, then derives the fit against the available
 * box. Split out of [FitToCanvas] so the composable stays trivial and each step has a name.
 */
private fun SubcomposeMeasureScope.measureFitted(
    constraints: Constraints,
    minScale: Float,
    fitMode: FitMode,
    content: @Composable () -> Unit
): FitResult {
    val measured = when (fitMode) {
        FitMode.Height ->
            measureContent("bounded", boundedOrInfinite(constraints.hasBoundedWidth, constraints.maxWidth), content)
        FitMode.Contain ->
            measureContent("intrinsic", Constraints.Infinity, content)
    }

    val availableWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else measured.width
    var availableHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else measured.height

    var sized = measured
    if (fitMode == FitMode.Height) {
        val refined = refineForWidth(constraints.hasBoundedWidth, measured, availableWidth, availableHeight, minScale, content)
        if (refined !== measured) {
            sized = refined
            availableHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else refined.height
        }
    }

    val scale = SlideCanvasFit.contentScale(sized.width, sized.height, availableWidth, availableHeight, minScale)
    val overflowing = SlideCanvasFit.isOverflowing(sized.width, sized.height, availableWidth, availableHeight, minScale)
    return FitResult(sized.placeables, availableWidth, availableHeight, scale, overflowing)
}

private fun boundedOrInfinite(bounded: Boolean, maxWidth: Int): Int =
    if (bounded) maxWidth else Constraints.Infinity

/** Subcomposes and measures [content] at [maxWidth], height-free so text/flow layouts wrap. */
private fun SubcomposeMeasureScope.measureContent(
    slotId: Any,
    maxWidth: Int,
    content: @Composable () -> Unit
): Measured {
    val placeables = subcompose(slotId) { content() }.map {
        it.measure(Constraints(maxWidth = maxWidth, minHeight = 0, maxHeight = Constraints.Infinity))
    }
    return Measured(placeables, placeables.maxOfOrNull { it.width } ?: 0, placeables.maxOfOrNull { it.height } ?: 0)
}

/**
 * Re-measures reflowing content at a wider width when it overflows.
 *
 * Uniform scaling shrinks width as well as height, so a full-width list would end up a narrow
 * strip with the sides unused. Laid out wider the content is shorter, and once scaled back
 * down it fills the available width. Only meaningful for [FitMode.Height]; fixed-size content
 * re-measures to the same size, so it is never asked to.
 */
private fun SubcomposeMeasureScope.refineForWidth(
    boundedWidth: Boolean,
    measured: Measured,
    availableWidth: Int,
    availableHeight: Int,
    minScale: Float,
    content: @Composable () -> Unit
): Measured {
    val firstPass = SlideCanvasFit.contentScale(
        measured.width, measured.height, availableWidth, availableHeight, minScale
    )
    if (firstPass >= 1f || !boundedWidth || measured.width <= 0) return measured
    val widerWidth = (availableWidth / firstPass).roundToInt()
    val refined = measureContent("refined", widerWidth, content)
    return if (refined.width > 0 && refined.height > 0) refined else measured
}

/**
 * Places one placeable centred and uniformly scaled.
 *
 * Scale about the TOP-LEFT and offset by the *scaled* size to centre. Centring the unscaled
 * placeable and scaling about its centre is the obvious approach and does not place correctly
 * for oversized content — the pre-scale offset goes far negative and lands off-centre.
 * Anchoring at the top-left keeps placement in scaled coordinates, where the arithmetic is exact.
 */
private fun Placeable.PlacementScope.placeCentredAndScaled(placeable: Placeable, fit: FitResult) {
    val scaledWidth = placeable.width * fit.scale
    val scaledHeight = placeable.height * fit.scale
    val x = ((fit.availableWidth - scaledWidth) / 2f).roundToInt()
    val y = ((fit.availableHeight - scaledHeight) / 2f).roundToInt()
    placeable.placeWithLayer(x, y) {
        scaleX = fit.scale
        scaleY = fit.scale
        transformOrigin = TransformOrigin(0f, 0f)
    }
}
