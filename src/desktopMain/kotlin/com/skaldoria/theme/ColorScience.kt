package com.skaldoria.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Mathematical Color Science utility implementing WCAG 2.1 specifications.
 * Provides pure mathematical calculations for sRGB linearization, relative luminance,
 * contrast ratio, and HSL color space conversions.
 */
object ColorScience {

    /**
     * Converts an 8-bit normalized sRGB component [0.0..1.0] to linear RGB space (WCAG 2.1).
     */
    fun sRgbToLinear(c: Float): Float {
        return if (c <= 0.04045f) {
            c / 12.92f
        } else {
            ((c + 0.055f) / 1.055f).pow(2.4f)
        }
    }

    /**
     * Computes the WCAG 2.1 Relative Luminance (L) of a color using ITU-R BT.709 coefficients.
     * Range: [0.0..1.0], where 0.0 is pure black and 1.0 is pure white.
     */
    fun relativeLuminance(color: Color): Float {
        val rLinear = sRgbToLinear(color.red)
        val gLinear = sRgbToLinear(color.green)
        val bLinear = sRgbToLinear(color.blue)

        return 0.2126f * rLinear + 0.7152f * gLinear + 0.0722f * bLinear
    }

    /**
     * Computes the WCAG 2.1 Contrast Ratio between two colors.
     * Formula: CR = (L1 + 0.05) / (L2 + 0.05), where L1 >= L2.
     * Range: [1.0..21.0].
     */
    fun contrastRatio(c1: Color, c2: Color): Float {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)

        val lighter = max(l1, l2)
        val darker = min(l1, l2)

        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /**
     * Checks if contrast ratio meets WCAG AA standard (CR >= 4.5 for normal text).
     */
    fun isWcagAa(foreground: Color, background: Color, minCr: Float = 4.5f): Boolean {
        return contrastRatio(foreground, background) >= minCr
    }

    /**
     * Checks if contrast ratio meets WCAG AAA standard (CR >= 7.0 for normal text).
     */
    fun isWcagAaa(foreground: Color, background: Color): Boolean {
        return contrastRatio(foreground, background) >= 7.0f
    }

    /**
     * Converts a Compose Color to HSL array: [0] = Hue in degrees [0..360], [1] = Saturation [0..1], [2] = Lightness [0..1].
     */
    fun colorToHsl(color: Color): FloatArray {
        val r = color.red
        val g = color.green
        val b = color.blue

        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        val delta = max - min

        var h = 0f
        var s = 0f
        val l = (max + min) / 2f

        if (delta != 0f) {
            s = if (l <= 0.5f) delta / (max + min) else delta / (2f - max - min)

            h = when (max) {
                r -> ((g - b) / delta + (if (g < b) 6f else 0f))
                g -> ((b - r) / delta + 2f)
                else -> ((r - g) / delta + 4f)
            }
            h *= 60f
        }

        return floatArrayOf(h, s, l)
    }

    /**
     * Converts HSL values back to a Compose Color.
     */
    fun hslToColor(h: Float, s: Float, l: Float, alpha: Float = 1.0f): Color {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f

        val (rPrime, gPrime, bPrime) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val r = (rPrime + m).coerceIn(0f, 1f)
        val g = (gPrime + m).coerceIn(0f, 1f)
        val b = (bPrime + m).coerceIn(0f, 1f)

        return Color(r, g, b, alpha)
    }
}
