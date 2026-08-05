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
 * ## Intrinsically-wide content must opt in
 *
 * A child that lays itself out through its own `SubcomposeLayout` (a flowchart, a sequence
 * diagram) can be genuinely wider than the canvas, yet Compose coerces its reported width to
 * the bounded width measured here — so the overflow is hidden and never scaled. Set
 * [discoverNaturalWidth] for those callers to re-measure unbounded and recover the honest
 * width. It is off by default because reflowing content (bullets, paragraphs) has no honest
 * "natural" width — unbounded it reports one enormous single line and would scale to nothing.
 *
 * @param minScale legibility floor; below this the content is left clipped and
 *   [onScaleComputed] reports the overflow rather than shrinking into unreadability.
 * @param discoverNaturalWidth re-measure the content unbounded to find an intrinsic width the
 *   bounded pass hid. Enable only for intrinsically-sized content (diagrams); never for text
 *   or lists, which merely refuse to wrap when unbounded and would then scale to nothing.
 * @param onScaleComputed invoked with the applied scale whenever it changes; use it to
 *   surface an authoring warning. Not invoked on every frame — only on scale change.
 */
@Composable
fun FitToCanvas(
    modifier: Modifier = Modifier,
    minScale: Float = SlideCanvasFit.DEFAULT_MIN_SCALE,
    discoverNaturalWidth: Boolean = false,
    onScaleComputed: ((scale: Float, overflowing: Boolean) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    SubcomposeLayout(modifier) { constraints ->
        val fit = measureFitted(constraints, minScale, discoverNaturalWidth, content)
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
 * Runs the whole fit: measure, recover the honest natural width, re-measure for width, then
 * derive the scale. Split out of [FitToCanvas] so the composable stays trivial and each step
 * of the measurement has a name.
 *
 * An unbounded parent gives nothing to fit against, and passing Infinity to `layout()` throws.
 * Falling back to "no fitting" (scale 1) is correct here — degrading beats crashing mid-talk.
 */
private fun SubcomposeMeasureScope.measureFitted(
    constraints: Constraints,
    minScale: Float,
    discoverNaturalWidth: Boolean,
    content: @Composable () -> Unit
): FitResult {
    val boundedWidth = constraints.hasBoundedWidth
    val boundedHeight = constraints.hasBoundedHeight
    val boundedMaxWidth = if (boundedWidth) constraints.maxWidth else Constraints.Infinity

    var measured = measureContent("bounded", boundedMaxWidth, content)
    if (discoverNaturalWidth) {
        measured = recoverNaturalWidth(boundedWidth, measured, content)
    }

    var availableWidth = if (boundedWidth) constraints.maxWidth else measured.width
    var availableHeight = if (boundedHeight) constraints.maxHeight else measured.height

    val refined = refineForWidth(boundedWidth, measured, availableWidth, availableHeight, minScale, content)
    if (refined !== measured) {
        measured = refined
        availableHeight = if (boundedHeight) constraints.maxHeight else measured.height
    }

    val scale = SlideCanvasFit.contentScale(
        measured.width, measured.height, availableWidth, availableHeight, minScale
    )
    val overflowing = SlideCanvasFit.isOverflowing(
        measured.width, measured.height, availableWidth, availableHeight, minScale
    )
    return FitResult(measured.placeables, availableWidth, availableHeight, scale, overflowing)
}

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
 * Recovers the true natural width for intrinsic content.
 *
 * A child laid out through its own SubcomposeLayout (a flowchart, a sequence diagram) reports
 * a size that Compose *coerces* to the bounded width — so a diagram wider than the canvas comes
 * back reporting the canvas width, FitToCanvas concludes it already fits, never scales it, and
 * the content overflows and clips. Measuring once more unbounded recovers the honest size.
 *
 * Reflowing text must NOT be treated this way: unbounded it reports an enormous single-line
 * width and would scale to nothing. The two are told apart by height — intrinsic content keeps
 * the same height when given more width, text gets shorter — so the natural measurement is
 * adopted only when the height did not shrink.
 */
private fun SubcomposeMeasureScope.recoverNaturalWidth(
    boundedWidth: Boolean,
    measured: Measured,
    content: @Composable () -> Unit
): Measured {
    if (!boundedWidth || measured.width <= 0) return measured
    val natural = measureContent("natural", Constraints.Infinity, content)
    val clamped = natural.width > measured.width && natural.height >= measured.height
    return if (clamped) natural else measured
}

/**
 * Re-measures at a wider width when the content overflows.
 *
 * Uniform scaling shrinks width as well as height, so a full-width list would end up a narrow
 * strip with the sides unused. Laid out wider the content is shorter, and once scaled back down
 * it fills the available width. Intrinsic content re-measures to the same size, so this is a
 * no-op for it — the natural measurement already found is kept.
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
