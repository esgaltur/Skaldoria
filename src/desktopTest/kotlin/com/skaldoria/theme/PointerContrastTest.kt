package com.skaldoria.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * THM-05: the pointer stays visible on every palette the app ships.
 *
 * The colour choice is pure, so it is asserted on the *outcome* — the contrast ratio actually
 * achieved — rather than on which branch was taken. A test that checked "light theme gets the
 * dark arrow" would pass just as happily if the dark arrow were `#EEEEEE`.
 *
 * What this cannot cover is that the arrow is drawn at all: the pointer is composited by the
 * window system, never by Skia, so it appears in no `ImageComposeScene` frame. Recorded in
 * `RENDERING_STATUS.md` beside the focus limitation it resembles.
 */
class PointerContrastTest {

    /** The floor the derivation targets. Asserted here so a weakened target fails a test. */
    private val minimumContrast = PointerContrast.MIN_FILL_CONTRAST

    @Test
    fun `every built-in palette gets a pointer that contrasts with its background`() {
        val failures = BuiltinThemes.all.mapNotNull { theme ->
            val ratio = PointerContrast.fillContrast(theme.background, theme.primary)
            if (ratio < minimumContrast) "${theme.name} (${"%.2f".format(ratio)}:1)" else null
        }

        assertTrue(
            failures.isEmpty(),
            "the pointer would be hard to see on: $failures"
        )
    }

    @Test
    fun `every Beamer preset gets one too`() {
        // Madrid and Berkeley are built on the light palettes, which is the reported case.
        val failures = BuiltinDeckThemes.all.mapNotNull { deckTheme ->
            val ratio = PointerContrast.fillContrast(deckTheme.colors.background, deckTheme.colors.primary)
            if (ratio < minimumContrast) "${deckTheme.name} (${"%.2f".format(ratio)}:1)" else null
        }

        assertTrue(failures.isEmpty(), "the pointer would be hard to see on: $failures")
    }

    @Test
    fun `the extremes are covered, not just the shipped palettes`() {
        // Mid-grey is the interesting one: it is the background a light-or-dark decision
        // handles worst, and the case a solved lightness has to get right.
        val backgrounds = listOf(Color.White, Color.Black, Color(0xFF808080), Color(0xFF7F8C8D))
        val accents = listOf(Color(0xFF7C3AED), Color(0xFF10B981), Color(0xFF808080), Color.White)

        for (background in backgrounds) {
            for (accent in accents) {
                val ratio = PointerContrast.fillContrast(background, accent)
                assertTrue(
                    ratio >= minimumContrast,
                    "accent $accent on $background achieved only ${"%.2f".format(ratio)}:1"
                )
            }
        }
    }

    @Test
    fun `fill and outline are opposites, so one of them always shows`() {
        // The outline is what keeps the pointer visible over a screenshot or code block, which
        // is nothing like the theme background. It is worthless if it matches the fill.
        for (theme in BuiltinThemes.all) {
            val colors = PointerContrast.forTheme(theme)
            assertNotEquals(
                colors.fill, colors.outline,
                "${theme.name}: fill and outline are identical, so the arrow has no silhouette"
            )
            assertTrue(
                ColorScience.contrastRatio(colors.fill, colors.outline) >= PointerContrast.MIN_SILHOUETTE_CONTRAST,
                "${theme.name}: fill and outline are too close to separate the arrow from content"
            )
        }
    }

    @Test
    fun `a light background gets a darker arrow and a dark background a lighter one`() {
        val accent = Color(0xFF7C3AED)
        val onLight = PointerContrast.forBackground(Color(0xFFF8FAFC), accent)
        val onDark = PointerContrast.forBackground(Color(0xFF11151C), accent)

        assertTrue(
            ColorScience.relativeLuminance(onLight.fill) < ColorScience.relativeLuminance(onDark.fill),
            "the same accent did not adapt between a light and a dark deck"
        )
    }

    @Test
    fun `the arrow keeps the theme's hue instead of falling back to black or white`() {
        // The point of solving lightness rather than picking ink-or-paper: a violet theme gets a
        // violet pointer. This is the property the previous threshold-plus-two-constants
        // implementation could not satisfy at all.
        val accent = Color(0xFF7C3AED)
        val accentHue = ColorScience.colorToHsl(accent)[0]

        for (background in listOf(Color(0xFFF8FAFC), Color(0xFF11151C))) {
            val fill = PointerContrast.forBackground(background, accent).fill
            val hsl = ColorScience.colorToHsl(fill)

            assertTrue(
                kotlin.math.abs(hsl[0] - accentHue) < 1.0f,
                "hue drifted from ${'$'}accentHue to ${'$'}{hsl[0]} — the arrow is no longer the theme's colour"
            )
            assertTrue(hsl[1] > 0.2f, "saturation collapsed to ${'$'}{hsl[1]}: the arrow went greyscale")
        }
    }

    @Test
    fun `a greyscale accent still yields a usable pointer`() {
        // No hue to preserve, so this exercises the path where only lightness can move.
        val ratio = PointerContrast.fillContrast(Color(0xFFF8FAFC), Color(0xFF9AA0A6))
        assertTrue(ratio >= minimumContrast, "greyscale accent achieved only ${"%.2f".format(ratio)}:1")
    }
}
