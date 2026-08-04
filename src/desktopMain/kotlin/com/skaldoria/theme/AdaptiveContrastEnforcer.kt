package com.skaldoria.theme

import androidx.compose.ui.graphics.Color

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

    override fun ensureContrast(
        foreground: Color,
        background: Color,
        minContrastRatio: Float
    ): Color {
        val currentRatio = ColorScience.contrastRatio(foreground, background)
        if (currentRatio >= minContrastRatio) {
            return foreground
        }

        val bgLuminance = ColorScience.relativeLuminance(background)
        val hsl = ColorScience.colorToHsl(foreground)
        val h = hsl[0]
        val s = hsl[1]
        var l = hsl[2]

        // Binary search / iterative refinement for optimal lightness that achieves target CR
        val isBackgroundLight = bgLuminance > 0.5f

        if (isBackgroundLight) {
            // Darken foreground
            var low = 0.0f
            var high = l
            var bestColor = ColorScience.hslToColor(h, s, 0.05f, foreground.alpha)

            for (step in 0..12) {
                val mid = (low + high) / 2f
                val candidate = ColorScience.hslToColor(h, s, mid, foreground.alpha)
                val ratio = ColorScience.contrastRatio(candidate, background)

                if (ratio >= minContrastRatio) {
                    bestColor = candidate
                    low = mid // Try to keep as bright as permissible
                } else {
                    high = mid // Need to go darker
                }
            }
            return bestColor
        } else {
            // Lighten foreground
            var low = l
            var high = 1.0f
            var bestColor = ColorScience.hslToColor(h, s, 0.95f, foreground.alpha)

            for (step in 0..12) {
                val mid = (low + high) / 2f
                val candidate = ColorScience.hslToColor(h, s, mid, foreground.alpha)
                val ratio = ColorScience.contrastRatio(candidate, background)

                if (ratio >= minContrastRatio) {
                    bestColor = candidate
                    high = mid // Try to keep as close to original as permissible
                } else {
                    low = mid // Need to go brighter
                }
            }
            return bestColor
        }
    }

    /**
     * Enforces minimum visible border separation for surfaces.
     */
    fun computeSurfaceBorder(surface: Color): Color {
        val luminance = ColorScience.relativeLuminance(surface)
        val hsl = ColorScience.colorToHsl(surface)

        return if (luminance > 0.5f) {
            // Light surface: Darken border
            ColorScience.hslToColor(hsl[0], hsl[1], (hsl[2] - 0.18f).coerceAtLeast(0.1f))
        } else {
            // Dark surface: Lighten border
            ColorScience.hslToColor(hsl[0], hsl[1], (hsl[2] + 0.14f).coerceAtMost(0.9f))
        }
    }
}
