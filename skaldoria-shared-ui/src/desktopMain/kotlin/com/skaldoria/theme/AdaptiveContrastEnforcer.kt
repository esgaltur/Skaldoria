package com.skaldoria.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Interface defining contrast enforcement operations (Interface Segregation & Dependency Inversion).
 */
interface IContrastEnforcer {
    fun ensureContrast(
        foreground: Color,
        background: Color,
        minContrastRatio: Float = 4.5f
    ): Color
}

/**
 * Dynamic contrast enforcer that adjusts color lightness along the HSL axis
 * to guarantee WCAG compliance while preserving original hue and saturation.
 */
object AdaptiveContrastEnforcer : IContrastEnforcer {

    /**
     * Returns [foreground] adjusted until it reaches [minContrastRatio] against [background].
     *
     * Hue and saturation are preserved; only lightness moves, and only as far as it must — the
     * result should still look like the colour the palette asked for.
     *
     * **This used to search one direction and could pick the losing one.** It chose darken or
     * lighten from `relativeLuminance(background) > 0.5f` and then searched only that way. But
     * relative luminance is not perceptual lightness: mid-grey `#808080` measures **0.216**, so
     * the check called it *dark*, lightened the foreground, and topped out at 3.9:1 — while
     * darkening the very same colour reaches 5.3:1. Anything on a mid-luminance background was
     * silently given the worse of the two options, and because the function still returned its
     * best effort rather than failing, nothing reported it. Found while deriving THM-05's
     * pointer colours, where a violet accent on mid-grey came back at 3.32:1.
     *
     * Now both directions are considered and the better result wins. On a background that is
     * clearly light or clearly dark the winner is the direction the old code would have picked,
     * so palettes at those extremes are unaffected.
     */
    override fun ensureContrast(
        foreground: Color,
        background: Color,
        minContrastRatio: Float
    ): Color {
        if (ColorScience.contrastRatio(foreground, background) >= minContrastRatio) {
            return foreground
        }
        return solveLightness(foreground, background, minContrastRatio)
    }

    /**
     * The lightness of [source]'s hue that best satisfies [target] against [against].
     *
     * Sweeps the whole axis instead of committing to a direction. Among the candidates that
     * clear [target] it keeps the one closest to the original lightness, so a colour is changed
     * as little as visibility allows. When none clears it — a saturated hue on a mid-grey
     * background genuinely cannot reach 4.5:1 — it returns the highest contrast available,
     * which is the best answer that exists rather than a silent failure.
     *
     * Shared with [PointerContrast] deliberately: two implementations of "make this legible"
     * is how the two would drift, and this codebase has paid for that with fences, tables and
     * directive keys already.
     */
    fun solveLightness(source: Color, against: Color, target: Float): Color {
        val hsl = ColorScience.colorToHsl(source)
        val originalLightness = hsl[2]

        var best = source
        var bestRatio = ColorScience.contrastRatio(source, against)
        var bestDistance = if (bestRatio >= target) 0f else Float.MAX_VALUE

        for (step in 0..LIGHTNESS_STEPS) {
            val lightness = step.toFloat() / LIGHTNESS_STEPS
            val candidate = ColorScience.hslToColor(hsl[0], hsl[1], lightness, source.alpha)
            val ratio = ColorScience.contrastRatio(candidate, against)
            val distance = abs(lightness - originalLightness)

            val clears = ratio >= target
            val bestClears = bestRatio >= target

            val better = when {
                clears && bestClears -> distance < bestDistance
                clears -> true
                bestClears -> false
                else -> ratio > bestRatio
            }

            if (better) {
                best = candidate
                bestRatio = ratio
                bestDistance = distance
            }
        }
        return best
    }

    /** The lightness of [source]'s hue that contrasts *most* with [against]. */
    fun maximiseContrast(source: Color, against: Color): Color {
        val hsl = ColorScience.colorToHsl(source)
        var best = source
        var bestRatio = ColorScience.contrastRatio(source, against)

        for (step in 0..LIGHTNESS_STEPS) {
            val candidate = ColorScience.hslToColor(
                hsl[0], hsl[1], step.toFloat() / LIGHTNESS_STEPS, source.alpha
            )
            val ratio = ColorScience.contrastRatio(candidate, against)
            if (ratio > bestRatio) {
                best = candidate
                bestRatio = ratio
            }
        }
        return best
    }

    /** Resolution of the lightness sweep. Finer than 8-bit sRGB can express. */
    private const val LIGHTNESS_STEPS = 200
}
