package com.skaldoria.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.PointerIcon
import com.skaldoria.theme.PointerColors
import com.skaldoria.theme.PointerContrast
import com.skaldoria.theme.PresentationTheme
import java.awt.BasicStroke
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.geom.GeneralPath
import java.awt.image.BufferedImage
import java.awt.Color as AwtColor

/**
 * THM-05: the standard arrow pointer, drawn in colours the current theme can survive.
 *
 * **Same shape, different colour** — deliberately. A presentation tool that invents its own
 * pointer glyph makes the audience wonder what the cursor is; the arrow is the one piece of
 * chrome everyone already reads instantly. Only its fill and outline change.
 *
 * The mechanism is the one `SlideAnnotationOverlay` already uses for its blank laser cursor:
 * an AWT custom cursor wrapped in a Compose [PointerIcon].
 *
 * **Not verifiable by the render harness.** The pointer is composited by the window system, not
 * by Skia, so it never appears in an `ImageComposeScene` frame — the same class of limitation
 * recorded for EDT-7's focus. What *is* guarded is the colour choice, which is pure
 * ([PointerContrast]); that the arrow is drawn is a manual check.
 */
object ThemedPointerIcon {

    /** The design grid the arrow path is authored against, then scaled to the OS cursor size. */
    private const val DESIGN_SIZE = 32.0

    /**
     * A classic arrow, authored on a 32×32 grid with its tip at the origin.
     *
     * The tip must be exactly `(0, 0)` because that is the hot spot handed to AWT — an arrow
     * whose visual point is not where the click lands is worse than no custom cursor at all.
     */
    private val ARROW_POINTS = listOf(
        0.0 to 0.0,
        0.0 to 19.0,
        5.0 to 14.6,
        8.2 to 21.6,
        11.6 to 20.0,
        8.6 to 13.3,
        14.6 to 13.3
    )

    /**
     * Builds a pointer for [colors], or null when this platform will not make one.
     *
     * Null rather than a throw: a headless box or a window system that refuses custom cursors
     * should fall back to the ordinary arrow, not fail to present.
     */
    fun create(colors: PointerColors): PointerIcon? = try {
        val toolkit = Toolkit.getDefaultToolkit()
        // The OS dictates cursor dimensions; anything else is rescaled or rejected outright.
        val best = toolkit.getBestCursorSize(DESIGN_SIZE.toInt(), DESIGN_SIZE.toInt())
        val size = if (best.width <= 0 || best.height <= 0) DESIGN_SIZE.toInt() else best.width

        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

            val scale = size / DESIGN_SIZE
            val path = GeneralPath()
            ARROW_POINTS.forEachIndexed { index, (x, y) ->
                val px = (x * scale).toFloat()
                val py = (y * scale).toFloat()
                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.closePath()

            g.color = colors.fill.toAwt()
            g.fill(path)

            // Stroked after the fill so the outline sits on the silhouette rather than under it.
            g.color = colors.outline.toAwt()
            g.stroke = BasicStroke((1.6 * scale).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(path)
        } finally {
            g.dispose()
        }

        PointerIcon(toolkit.createCustomCursor(image, Point(0, 0), "skaldoriaThemedPointer"))
    } catch (_: Exception) {
        null
    }

    private fun androidx.compose.ui.graphics.Color.toAwt(): AwtColor =
        AwtColor(red, green, blue, alpha)
}

/**
 * The themed pointer for [theme], rebuilt only when the palette's contrast decision changes.
 *
 * Keyed on the resolved colours rather than the theme: every dark palette yields the same
 * pointer, so switching between them costs nothing.
 */
@Composable
fun rememberThemedPointerIcon(theme: PresentationTheme): PointerIcon? {
    val colors = remember(theme.background) { PointerContrast.forTheme(theme) }
    return remember(colors) { ThemedPointerIcon.create(colors) }
}
