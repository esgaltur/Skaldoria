package com.skaldoria.canvas.model

import kotlin.math.sqrt

/**
 * How much of a node's bounding box the shape's outline eats, as a fraction of width and height.
 *
 * Sides are separate because a cylinder is not symmetric: its lid and base take different amounts.
 */
data class NodeContentInset(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * The largest axis-aligned rectangle that fits **inside** the drawn outline.
 *
 * **The bug this exists to remove.** Every shape laid its markdown out across the whole bounding
 * box and then clipped to the outline, so a diamond — whose inscribed rectangle is only a quarter
 * of its bounding box by area — sliced the corners off its own content. Padding was a flat `16.dp`
 * regardless of shape, which is the right answer only for a rectangle.
 *
 * The numbers are derived, not tuned:
 *
 * - **Diamond.** Points satisfy `|x−cx|/(w/2) + |y−cy|/(h/2) ≤ 1`. A centred rectangle with
 *   half-extents `a, b` touches that boundary when `a/(w/2) + b/(h/2) = 1`; area `4ab` is maximal
 *   at `a = w/4`, `b = h/4`. So the usable rectangle is **half the width and half the height**,
 *   leaving a quarter inset on each side.
 * - **Circle** (an ellipse in a `w × h` box). The largest inscribed axis-aligned rectangle has
 *   half-extents `w/(2√2)` and `h/(2√2)`, so each inset is `(1 − 1/√2)/2 ≈ 0.1464`.
 * - **Cylinder.** The lid ellipse occupies `y ∈ [0, 2·ry]` and the base ellipse `y ∈ [h − 2·ry, h]`,
 *   with `ry = 0.15h`. The flat body is the band strictly between them, so both insets are `2·ry`.
 *   Stopping at the base curve's *widest* point (`h − ry`) is not enough: the ellipse's topmost
 *   point is `2·ry` above the bottom, so a final line of text was drawn straight across the curve.
 * - **Card** and **Rectangle** have square corners, so their content box is the whole node; the
 *   card's title bar is a separate row rather than an inset.
 */
fun NodeShape.contentInset(): NodeContentInset = when (this) {
    NodeShape.Card, NodeShape.Rectangle -> NodeContentInset(0f, 0f, 0f, 0f)

    NodeShape.Diamond -> NodeContentInset(0.25f, 0.25f, 0.25f, 0.25f)

    NodeShape.Circle -> {
        val inset = (1f - 1f / sqrt(2f)) / 2f
        NodeContentInset(inset, inset, inset, inset)
    }

    NodeShape.Cylinder -> NodeContentInset(
        left = 0.04f,
        top = CYLINDER_RADIUS_FRACTION * 2f,
        right = 0.04f,
        bottom = CYLINDER_RADIUS_FRACTION * 2f
    )
}

/** Half-height of the cylinder's lid and base ellipses, as a fraction of the node's height. */
const val CYLINDER_RADIUS_FRACTION = 0.15f

/**
 * Whether [x], [y] is safe to draw content at, for a [NodeShape] occupying `0,0 .. width,height`.
 *
 * This is deliberately stricter than "inside the filled path". A cylinder's body rectangle extends
 * *under* both ellipses, so a point can be filled and still have the base curve drawn through it —
 * which is exactly how a bullet ended up struck through by its own outline. Content-safe means
 * clear of the drawn curves, not merely inside the fill.
 *
 * It exists so the insets above can be *verified* rather than trusted.
 */
fun NodeShape.containsPoint(x: Float, y: Float, width: Float, height: Float): Boolean {
    val cx = width / 2f
    val cy = height / 2f
    return when (this) {
        NodeShape.Card, NodeShape.Rectangle ->
            x in 0f..width && y in 0f..height

        NodeShape.Diamond -> {
            val normalized = kotlin.math.abs(x - cx) / cx + kotlin.math.abs(y - cy) / cy
            normalized <= 1f + TOLERANCE
        }

        NodeShape.Circle -> {
            val dx = (x - cx) / cx
            val dy = (y - cy) / cy
            dx * dx + dy * dy <= 1f + TOLERANCE
        }

        NodeShape.Cylinder -> {
            val lid = height * CYLINDER_RADIUS_FRACTION * 2f
            // The flat band between the lid and base ellipses — not the filled body, which runs
            // underneath both of them.
            x in -TOLERANCE..(width + TOLERANCE) && y >= lid - TOLERANCE && y <= height - lid + TOLERANCE
        }
    }
}

private const val TOLERANCE = 1e-4f
