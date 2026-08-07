package com.skaldoria.theme

/**
 * THM-02: the named presets, as data.
 *
 * Each is a `(colour, chrome, fonts)` triple. Nothing here draws anything, so adding a preset
 * is a declaration — the property Approach B was chosen for.
 *
 * **Homage, not recreation.** These borrow the *shape* of the Beamer themes they are named
 * after — Madrid's title band and author/page footline, Warsaw's section navigation, Berkeley's
 * sidebar tab — over Skaldoria's own palettes. They are not pixel reproductions: Beamer's
 * originals are built on LaTeX's typographic model and copying them exactly would mean
 * importing decisions (Computer Modern metrics, `\beamer@` spacing) that fit nothing else here.
 * The point is that "Warsaw" gets you a talk that looks structured like Warsaw.
 */
object BuiltinDeckThemes {

    /**
     * Today's appearance, unchanged, and the fallback for every unrecognised name.
     *
     * `FrameTitleStyle.NONE` and `BarStyle.PAGE_NUMBER` reproduce exactly what `SlideSurface`
     * drew before chrome existed: no band, no headline, the layout-type pill and `n / total`
     * footer. `DefaultChromeIsUnchangedTest` renders it against that original and compares
     * pixels, because "backwards compatible" is a claim about what appears on screen and this
     * codebase has been bitten before by asserting it anywhere else.
     */
    val Default = DeckTheme(
        id = "skaldoria",
        name = "Skaldoria",
        colors = BuiltinThemes.SkaldoriaDark,
        chrome = SlideChrome(
            id = "skaldoria",
            name = "Skaldoria",
            frameTitle = FrameTitleStyle.NONE,
            headline = BarStyle.NONE,
            footline = BarStyle.PAGE_NUMBER,
            showNavDots = false
        ),
        fonts = ThemeFonts.Sans
    )

    /** Light, serif headings, title band, and the deck title beside the page number. */
    val Madrid = DeckTheme(
        id = "madrid",
        name = "Madrid",
        colors = BuiltinThemes.SleekLight,
        chrome = SlideChrome(
            id = "madrid",
            name = "Madrid",
            frameTitle = FrameTitleStyle.BAND,
            headline = BarStyle.NONE,
            footline = BarStyle.TITLE_AND_PAGE,
            showNavDots = true
        ),
        fonts = ThemeFonts.SerifHeadings
    )

    /** The busy one: section navigation above, title band, page number below. */
    val Warsaw = DeckTheme(
        id = "warsaw",
        name = "Warsaw",
        colors = BuiltinThemes.SkaldoriaDark,
        chrome = SlideChrome(
            id = "warsaw",
            name = "Warsaw",
            frameTitle = FrameTitleStyle.BAND,
            headline = BarStyle.SECTION_NAV,
            footline = BarStyle.TITLE_AND_PAGE,
            showNavDots = false
        ),
        fonts = ThemeFonts.Sans
    )

    /** Minimal: dots above, nothing below. Beamer's Singapore is the sparse one. */
    val Singapore = DeckTheme(
        id = "singapore",
        name = "Singapore",
        colors = BuiltinThemes.CyberMidnight,
        chrome = SlideChrome(
            id = "singapore",
            name = "Singapore",
            frameTitle = FrameTitleStyle.PLAIN,
            headline = BarStyle.DOTS,
            footline = BarStyle.NONE,
            showNavDots = false,
            cornerRadiusDp = 4
        ),
        fonts = ThemeFonts.Sans
    )

    /** Editorial serif with a sidebar tab instead of a band. */
    val Berkeley = DeckTheme(
        id = "berkeley",
        name = "Berkeley",
        colors = BuiltinThemes.MinimalistEditorial,
        chrome = SlideChrome(
            id = "berkeley",
            name = "Berkeley",
            frameTitle = FrameTitleStyle.SIDEBAR_TAB,
            headline = BarStyle.NONE,
            footline = BarStyle.MINIMAL,
            showNavDots = false,
            cornerRadiusDp = 0
        ),
        fonts = ThemeFonts.Serif
    )

    val all = listOf(Default, Madrid, Warsaw, Singapore, Berkeley)

    /**
     * Resolves a preset by id or display name, falling back to [Default].
     *
     * Falling back rather than failing is deliberate and matches `BuiltinThemes.getById`: a
     * manifest naming a theme this build does not have should open the deck, not refuse it —
     * discovering that on stage is worse than an unexpected palette.
     */
    fun getById(id: String?): DeckTheme {
        if (id.isNullOrBlank()) return Default
        return all.firstOrNull { it.id.equals(id, ignoreCase = true) }
            ?: all.firstOrNull { it.name.equals(id, ignoreCase = true) }
            ?: Default
    }

    /**
     * Wraps a bare colour palette in the default chrome.
     *
     * The bridge for everything that still selects a `PresentationTheme` — the `T` cycling
     * shortcut, the corporate unlock, the existing picker — so those keep working untouched
     * while chrome is adopted.
     */
    fun withDefaultChrome(colors: PresentationTheme): DeckTheme =
        Default.copy(id = colors.id, name = colors.name, colors = colors)
}
