package com.skaldoria.theme

import androidx.compose.ui.graphics.Color

/**
 * THM-05: what colour the mouse pointer has to be so it survives the deck behind it.
 *
 * **The defect.** The pointer is the operating system's, drawn in whatever colour the user's
 * desktop set — usually a dark arrow. Present a light deck and it is fine; present a dark one
 * and it disappears, and on a light theme a user with an inverted (white) system cursor loses
 * it just as completely. Nothing in the app had any say in it.
 *
 * **Why a pair and not a colour.** A single themed colour still loses: the pointer does not sit
 * on the theme background, it sits on whatever pixel is under it — a code block, a screenshot,
 * a diagram. Every operating system solves this the same way, with a filled arrow *and* a
 * contrasting outline, so one of the two always shows. That is why this returns both.
 *
 * Pure, so the contrast guarantee is a unit assertion rather than something asserted by eye.
 */
object PointerContrast {

    /**
     * Contrast the arrow body must reach against the slide behind it.
     *
     * WCAG AA for text. A pointer is not text, but it is small and its shape carries meaning,
     * so the text threshold is the right floor rather than the 3:1 allowed for large shapes.
     */
    const val MIN_FILL_CONTRAST = 4.5f

    /**
     * Floor for the contrast between the body and its outline.
     *
     * This pair is what keeps the arrow legible over content the theme knows nothing about — a
     * screenshot, a code block, a diagram — because if the two are far enough apart, one of
     * them contrasts with whatever is underneath.
     *
     * **3:1, and it cannot usefully be higher.** WCAG 1.4.11 asks 3:1 of non-text graphics, and
     * here that is also close to the arithmetic ceiling: a fill sitting at [MIN_FILL_CONTRAST]
     * against a near-white background has relative luminance ≈ 0.173, so the *best any colour
     * can do* against it is 4.71:1 (pure white) or 4.46:1 (pure black). An earlier draft of this
     * asked for 8:1 and the guard failed on Sleek Light — not because the search was weak, but
     * because 8:1 does not exist there. The outline still takes the maximum available; this is
     * only the floor below which the arrow would stop having a silhouette at all.
     */
    const val MIN_SILHOUETTE_CONTRAST = 3.0f

    /**
     * The pointer's colours for a slide with [background], derived from [accent].
     *
     * Both are **solved**, not chosen from a table: hue and saturation are held and only
     * lightness moves, so a violet theme gets a violet pointer rather than a generic black one.
     *
     * Solved by [AdaptiveContrastEnforcer], the same machinery that keeps text legible — so a
     * palette whose text passes is a palette whose pointer passes. Deriving these colours is
     * what exposed the enforcer's one-direction search (it missed 5.3:1 on mid-grey by only
     * ever lightening); that is fixed there rather than worked around here.
     */
    fun forBackground(background: Color, accent: Color): PointerColors {
        val fill = AdaptiveContrastEnforcer.ensureContrast(accent, background, MIN_FILL_CONTRAST)
        return PointerColors(fill = fill, outline = outlineFor(fill))
    }

    /** The palette's own accent, made visible against its own background. */
    fun forTheme(theme: PresentationTheme): PointerColors =
        forBackground(background = theme.background, accent = theme.primary)

    /**
     * An outline for [fill]: the same hue, driven to the far end of the lightness axis.
     *
     * Mostly desaturated, so it reads as a border rather than a second colour fighting the
     * fill — but not entirely, because a trace of the hue is what stops the arrow looking like
     * a stock cursor pasted onto a themed deck.
     */
    private fun outlineFor(fill: Color): Color {
        val hsl = ColorScience.colorToHsl(fill)
        // Maximised, not merely sufficient. The fill wants to stay as close to the theme's own
        // colour as visibility allows; the outline has no such loyalty — its whole job is
        // separation, so it takes the most contrast the axis offers.
        return AdaptiveContrastEnforcer.maximiseContrast(
            source = ColorScience.hslToColor(hsl[0], hsl[1] * OUTLINE_SATURATION_RETAINED, hsl[2], 1f),
            against = fill
        )
    }

    /**
     * The contrast the derived fill achieves against [background].
     *
     * Exposed so guards assert the outcome rather than the branch taken — a test that checked
     * "a light theme gets the dark arrow" would pass just as happily if that arrow were
     * `#EEEEEE`.
     */
    fun fillContrast(background: Color, accent: Color): Float =
        ColorScience.contrastRatio(forBackground(background, accent).fill, background)

    /** Enough hue to belong to the theme, little enough to read as an outline. */
    private const val OUTLINE_SATURATION_RETAINED = 0.18f
}

/**
 * A pointer's two colours.
 *
 * @param fill the arrow body.
 * @param outline its border, which is what keeps it visible over content that is nothing like
 *   the theme background.
 */
data class PointerColors(
    val fill: Color,
    val outline: Color
)
