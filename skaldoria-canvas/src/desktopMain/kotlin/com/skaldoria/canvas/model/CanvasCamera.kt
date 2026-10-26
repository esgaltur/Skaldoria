package com.skaldoria.canvas.model

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

/**
 * Camera framing and flight for the presentation tour. Pure geometry, so it is unit-tested
 * without a window.
 */
object CanvasCamera {

    /** Share of the screen a framed region may fill; the rest is breathing room. */
    const val FILL = 0.82f

    /** How far out the camera pulls, at most, in the middle of a long flight. */
    private const val MAX_PULL_BACK = 0.55f

    /** The viewport that centres [region] and fits it inside [FILL] of the screen. */
    fun frame(region: CanvasRect, screenWidth: Float, screenHeight: Float): CanvasViewport {
        val zoom = min(
            screenWidth * FILL / region.width.coerceAtLeast(1f),
            screenHeight * FILL / region.height.coerceAtLeast(1f)
        ).coerceIn(CanvasViewport.MIN_ZOOM, CanvasViewport.MAX_ZOOM)
        return centredOn(
            CanvasPoint((region.left + region.right) / 2f, (region.top + region.bottom) / 2f),
            zoom, screenWidth, screenHeight
        )
    }

    /** The bounds of every node, or null for an empty board. */
    fun boundsOf(nodes: List<CanvasNode>): CanvasRect? {
        if (nodes.isEmpty()) return null
        return CanvasRect(
            left = nodes.minOf { it.x },
            top = nodes.minOf { it.y },
            right = nodes.maxOf { it.x + it.width },
            bottom = nodes.maxOf { it.y + it.height }
        )
    }

    /**
     * The camera [progress] of the way from [from] to [to], as a flight rather than a slide.
     *
     * The centre moves in a straight line and the zoom is interpolated in log space, so zooming
     * from 0.5× to 2× feels as even as 1× to 4×. On a long hop the camera also pulls back in the
     * middle — up to [MAX_PULL_BACK] — so the audience sees where it is going before it dives in.
     * A short hop barely pulls back at all.
     */
    fun fly(
        from: CanvasViewport,
        to: CanvasViewport,
        progress: Float,
        screenWidth: Float,
        screenHeight: Float
    ): CanvasViewport {
        val t = progress.coerceIn(0f, 1f)
        if (t == 0f) return from
        if (t == 1f) return to

        val start = centreOf(from, screenWidth, screenHeight)
        val end = centreOf(to, screenWidth, screenHeight)
        val centre = CanvasPoint(start.x + (end.x - start.x) * t, start.y + (end.y - start.y) * t)

        // Travel measured in screens at the closer of the two zooms: one screen-width away is a
        // short hop, four or more is a long one.
        val screensAway = hypot(end.x - start.x, end.y - start.y) *
            min(from.zoom, to.zoom) / screenWidth.coerceAtLeast(1f)
        val pullBack = MAX_PULL_BACK * (screensAway / 4f).coerceIn(0f, 1f)

        val zoom = exp(ln(from.zoom) + (ln(to.zoom) - ln(from.zoom)) * t) *
            (1f - pullBack * sin(Math.PI.toFloat() * t))
        return centredOn(
            centre,
            zoom.coerceIn(CanvasViewport.MIN_ZOOM, CanvasViewport.MAX_ZOOM),
            screenWidth, screenHeight
        )
    }

    /** Slow at both ends, quick in the middle. */
    fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun centreOf(viewport: CanvasViewport, screenWidth: Float, screenHeight: Float) =
        viewport.screenToCanvas(CanvasPoint(screenWidth / 2f, screenHeight / 2f))

    private fun centredOn(centre: CanvasPoint, zoom: Float, screenWidth: Float, screenHeight: Float) =
        CanvasViewport(
            panX = screenWidth / 2f - centre.x * zoom,
            panY = screenHeight / 2f - centre.y * zoom,
            zoom = zoom
        )
}
