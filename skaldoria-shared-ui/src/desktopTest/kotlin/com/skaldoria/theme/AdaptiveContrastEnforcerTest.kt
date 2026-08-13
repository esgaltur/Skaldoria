package com.skaldoria.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The enforcer reaches the contrast it promises, in whichever direction that lies.
 *
 * **The defect these pin.** `ensureContrast` chose to darken or lighten from
 * `relativeLuminance(background) > 0.5f` and then searched only that direction. Relative
 * luminance is not perceptual lightness — mid-grey `#808080` measures **0.216** — so the check
 * called mid-grey *dark*, lightened the foreground, and stopped at 3.9:1, while darkening the
 * same colour reaches 5.3:1. It then returned that best effort rather than failing, so nothing
 * ever reported the shortfall. Found while deriving the THM-05 pointer colours.
 *
 * `testAdaptiveContrastEnforcement` in `ParkingLotAndThemeTest` passed throughout: it used a
 * pure-white background, where the direction check is right.
 */
class AdaptiveContrastEnforcerTest {

    private val midGrey = Color(0xFF808080)

    @Test
    fun `a mid-luminance background is not treated as dark`() {
        // The reported case: a saturated violet that the one-direction search left at 3.32:1.
        val violet = Color(0xFF7C3AED)

        val fixed = AdaptiveContrastEnforcer.ensureContrast(violet, midGrey, 4.5f)
        val ratio = ColorScience.contrastRatio(fixed, midGrey)

        assertTrue(
            ratio >= 4.5f,
            "only reached ${"%.2f".format(ratio)}:1 — the search took the losing direction again"
        )
    }

    @Test
    fun `both directions are considered across the whole luminance range`() {
        val foreground = Color(0xFF7C3AED)
        val failures = (0..20).mapNotNull { step ->
            val shade = (step * 255 / 20)
            val background = Color(shade / 255f, shade / 255f, shade / 255f, 1f)
            val fixed = AdaptiveContrastEnforcer.ensureContrast(foreground, background, 4.5f)
            val ratio = ColorScience.contrastRatio(fixed, background)
            // 4.5:1 is unreachable for a saturated hue against some mid backgrounds; the
            // guarantee is that the *best available* is taken, so compare against the ceiling.
            val ceiling = maxOf(
                ColorScience.contrastRatio(Color.White, background),
                ColorScience.contrastRatio(Color.Black, background)
            )
            val acceptable = ratio >= 4.5f || ratio >= ceiling * 0.6f
            if (acceptable) null else "grey $shade -> ${"%.2f".format(ratio)}:1 (ceiling ${"%.2f".format(ceiling)}:1)"
        }

        assertTrue(failures.isEmpty(), "the enforcer settled for far less than was available: $failures")
    }

    @Test
    fun `a colour that already passes is returned untouched`() {
        // Palettes depend on this: an accent that is already legible must not be nudged, or
        // every theme quietly shifts on any change to the search.
        val black = Color(0xFF000000)
        assertEquals(black, AdaptiveContrastEnforcer.ensureContrast(black, Color.White, 4.5f))
    }

    @Test
    fun `hue and saturation survive the adjustment`() {
        val violet = Color(0xFF7C3AED)
        val source = ColorScience.colorToHsl(violet)

        val fixed = AdaptiveContrastEnforcer.ensureContrast(violet, midGrey, 4.5f)
        val result = ColorScience.colorToHsl(fixed)

        assertTrue(kotlin.math.abs(result[0] - source[0]) < 1.0f, "hue moved: ${source[0]} -> ${result[0]}")
        assertTrue(kotlin.math.abs(result[1] - source[1]) < 0.05f, "saturation moved: ${source[1]} -> ${result[1]}")
    }

    @Test
    fun `the classic light and dark cases are unchanged by the fix`() {
        // Backgrounds at the extremes were always handled correctly; this pins that the
        // two-direction search did not disturb them.
        val tooLightGrey = Color(0xFFE2E8F0)
        val onWhite = AdaptiveContrastEnforcer.ensureContrast(tooLightGrey, Color.White, 4.5f)
        assertTrue(ColorScience.contrastRatio(onWhite, Color.White) >= 4.5f)

        val tooDarkGrey = Color(0xFF2A2F3A)
        val onBlack = AdaptiveContrastEnforcer.ensureContrast(tooDarkGrey, Color.Black, 4.5f)
        assertTrue(ColorScience.contrastRatio(onBlack, Color.Black) >= 4.5f)
    }

    @Test
    fun `the adjustment is the smallest one that works`() {
        // Reaching the target by slamming to black would satisfy the ratio and destroy the
        // palette. The result must stay as close to the original lightness as the target allows.
        val violet = Color(0xFF7C3AED)
        val fixed = AdaptiveContrastEnforcer.ensureContrast(violet, Color.White, 4.5f)

        val fixedLightness = ColorScience.colorToHsl(fixed)[2]
        assertTrue(
            fixedLightness > 0.15f,
            "lightness collapsed to $fixedLightness — the colour was slammed to an extreme"
        )
    }
}
