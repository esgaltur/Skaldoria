package com.skaldoria.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * THM-02: the structural half of a theme — Beamer's *outer* and *inner* themes.
 *
 * LaTeX Beamer splits a presentation theme into four independent parts: **colour**, **outer**
 * (the bars, bands and navigation around the slide), **inner** (blocks, bullets, the title
 * page) and **font**. Skaldoria already had the colour half — [PresentationTheme] is 25 colour
 * tokens — and hardcoded the other three, which is why "can we have Warsaw?" had no answer
 * beyond a recolour.
 *
 * Colours deliberately do **not** appear here. A chrome describes *what is drawn and where*;
 * every colour it uses comes from the [PresentationTheme] it is composed with, so the two
 * axes stay independently swappable — which is the whole point of Beamer's model, and what
 * makes a preset a data declaration rather than a rendering change.
 *
 * See `docs/superpowers/specs/2026-08-04-beamer-like-themes-design.md`, Approach B.
 */
data class SlideChrome(
    val id: String,
    val name: String,
    /** The band behind a slide's title. Beamer's `frametitle`. */
    val frameTitle: FrameTitleStyle = FrameTitleStyle.PLAIN,
    /** The bar across the top. Beamer's `headline`. */
    val headline: BarStyle = BarStyle.NONE,
    /** The bar across the bottom. Beamer's `footline`. */
    val footline: BarStyle = BarStyle.PAGE_NUMBER,
    /** Beamer's navigation circles — one dot per slide, the current one filled. */
    val showNavDots: Boolean = false,
    /** Corner rounding for framed content. Beamer's inner theme, roughly. */
    val cornerRadiusDp: Int = 12
)

/** How a slide's title is presented. */
enum class FrameTitleStyle {
    /** No distinct treatment; the layout draws the title however it likes. Today's behaviour. */
    NONE,

    /** Title in the layout, with no band behind it. */
    PLAIN,

    /** A filled band spanning the slide, title inside it — the Madrid/Warsaw look. */
    BAND,

    /** A tab attached to the leading edge, as Berkeley does with its sidebar. */
    SIDEBAR_TAB
}

/** What a headline or footline bar carries. Not every value is meaningful in both positions. */
enum class BarStyle {
    /** The bar is not drawn at all. */
    NONE,

    /** A hairline rule and nothing else. */
    MINIMAL,

    /** `n / total`, as Skaldoria has always drawn in its footer. */
    PAGE_NUMBER,

    /** Deck title on the left, slide position on the right — Beamer's classic footline. */
    TITLE_AND_PAGE,

    /** The deck's section titles, current one emphasised. Beamer's `SECTION_NAV`. */
    SECTION_NAV,

    /** One dot per slide. Compact navigation for a short deck. */
    DOTS
}

/**
 * THM-01: the font half of a theme.
 *
 * Families are the platform's, not bundled TTFs. Shipping fonts means licence review and a
 * larger binary for a first cut; `FontFamily.Serif` already gives the visual break from
 * `SansSerif` that makes a serif preset read as a different theme.
 */
data class ThemeFonts(
    val id: String,
    val name: String,
    val titleFamily: FontFamily = FontFamily.SansSerif,
    val bodyFamily: FontFamily = FontFamily.SansSerif,
    val monoFamily: FontFamily = FontFamily.Monospace,
    val titleWeight: FontWeight = FontWeight.Bold
) {
    companion object {
        val Sans = ThemeFonts(id = "sans", name = "Sans")

        val Serif = ThemeFonts(
            id = "serif",
            name = "Serif",
            titleFamily = FontFamily.Serif,
            bodyFamily = FontFamily.Serif
        )

        /** Serif headings over a sans body — the common "professional report" pairing. */
        val SerifHeadings = ThemeFonts(
            id = "serif-headings",
            name = "Serif Headings",
            titleFamily = FontFamily.Serif
        )

        val all = listOf(Sans, Serif, SerifHeadings)
    }
}

/**
 * What a deck actually renders with: colour, chrome and fonts composed.
 *
 * A named preset — "Madrid", "Warsaw" — is exactly this triple and nothing more, so adding one
 * is a data declaration that never touches rendering code. That is the property Approach B was
 * chosen for, and it is what keeps Approach C (letting a manifest pick the three parts
 * independently) purely additive rather than a rewrite.
 */
data class DeckTheme(
    val id: String,
    val name: String,
    val colors: PresentationTheme,
    val chrome: SlideChrome,
    val fonts: ThemeFonts
)
