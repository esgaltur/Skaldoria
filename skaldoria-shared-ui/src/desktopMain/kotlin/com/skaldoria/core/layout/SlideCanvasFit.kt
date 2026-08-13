package com.skaldoria.core.layout

import kotlin.math.min

/**
 * Pure geometry for fitting slide content onto the projection surface.
 *
 * Deliberately free of any Compose dependency: this is the only part of the slide
 * sizing pipeline that can be unit tested, since the project has no Compose UI test
 * harness. Keeping the arithmetic here — rather than inline in `SlideSurface` — is
 * what makes the NaN/zero/overflow edge cases verifiable at all.
 *
 * Two distinct fits happen, in this order:
 *  1. [fitDesignCanvas] — place a fixed 16:9 design canvas inside the real window.
 *  2. [contentScale]    — shrink content that overflows that design canvas.
 */
object SlideCanvasFit {

    /** Result of fitting the fixed design canvas into the available window area. */
    data class Surface(
        val width: Float,
        val height: Float,
        /** Uniform scale applied to the design canvas to reach [width] x [height]. */
        val scale: Float
    )

    /**
     * Letterboxes a [designWidth] x [designHeight] canvas into the available area,
     * preserving the design aspect ratio.
     *
     * Falls back to the design size when handed unmeasured, zero, infinite, or NaN
     * bounds — Compose emits those transiently during the first measure pass.
     */
    fun fitDesignCanvas(
        availableWidth: Float,
        availableHeight: Float,
        designWidth: Float,
        designHeight: Float
    ): Surface {
        val availW = sanitize(availableWidth, designWidth)
        val availH = sanitize(availableHeight, designHeight)
        val designRatio = if (designHeight > 0f) designWidth / designHeight else 16f / 9f
        val availableRatio = availW / availH

        // Wider than the design ratio means height is the binding constraint, and vice versa.
        val heightBound = availableRatio >= designRatio
        val width = if (heightBound) availH * designRatio else availW
        val height = if (heightBound) availH else availW / designRatio

        val rawScale = min(width / designWidth, height / designHeight)
        val scale = if (rawScale.isNaN() || rawScale.isInfinite() || rawScale <= 0f) 1f else rawScale

        return Surface(width = width, height = height, scale = scale)
    }

    /**
     * Uniform scale that brings content of [contentWidth] x [contentHeight] inside a
     * [availableWidth] x [availableHeight] box.
     *
     * Never returns above 1: content that already fits is left at its authored size
     * rather than being blown up. Never returns below [minScale] — past that point
     * shrinking trades one unreadable slide for another, so the caller is expected to
     * warn the author instead (see the `onScaleComputed` hook on `FitToCanvas`).
     */
    fun contentScale(
        contentWidth: Int,
        contentHeight: Int,
        availableWidth: Int,
        availableHeight: Int,
        minScale: Float = DEFAULT_MIN_SCALE
    ): Float {
        if (contentWidth <= 0 || contentHeight <= 0) return 1f
        if (availableWidth <= 0 || availableHeight <= 0) return 1f

        val widthScale = availableWidth.toFloat() / contentWidth.toFloat()
        val heightScale = availableHeight.toFloat() / contentHeight.toFloat()
        val fitted = min(widthScale, heightScale)

        if (fitted.isNaN() || fitted.isInfinite()) return 1f
        return fitted.coerceIn(minScale, 1f)
    }

    /** True when content had to be shrunk past the legibility floor and will still clip. */
    fun isOverflowing(
        contentWidth: Int,
        contentHeight: Int,
        availableWidth: Int,
        availableHeight: Int,
        minScale: Float = DEFAULT_MIN_SCALE
    ): Boolean {
        if (contentWidth <= 0 || contentHeight <= 0) return false
        if (availableWidth <= 0 || availableHeight <= 0) return false
        val widthScale = availableWidth.toFloat() / contentWidth.toFloat()
        val heightScale = availableHeight.toFloat() / contentHeight.toFloat()
        return min(widthScale, heightScale) < minScale
    }

    private fun sanitize(value: Float, fallback: Float): Float =
        if (value.isNaN() || value.isInfinite() || value <= 0f) fallback else value

    /**
     * Floor on shrinking.
     *
     * Deliberately low. A higher floor (0.5 was tried) stops shrinking while the content
     * still overflows, so the slide clips anyway and the author is never told — the worst
     * of both outcomes. Shrinking far enough that everything stays on screen at least makes
     * the problem visible, and [isOverflowing] reports when the floor was reached so the
     * UI can warn rather than silently dropping content.
     */
    const val DEFAULT_MIN_SCALE = 0.25f
}
