package com.skaldoria.theme

/**
 * Result of validating a presentation theme against mathematical accessibility and contrast constraints.
 */
data class ThemeValidationResult(
    val isValid: Boolean,
    val warnings: List<String> = emptyList(),
    val textPrimaryContrast: Float,
    val textSecondaryContrast: Float,
    val codeTextContrast: Float
)

interface IThemeValidator {
    fun validate(theme: PresentationTheme): ThemeValidationResult
}

/**
 * Validates theme token combinations to prevent unreadable pairings (such as white on light gray).
 */
object ThemePaletteValidator : IThemeValidator {

    /** WCAG's relaxed threshold for large text, which is what secondary text is used for. */
    private const val LARGE_TEXT_MIN_CR = 3.0f

    /** Below this the card border is not distinguishable from the surface it sits on. */
    private const val BORDER_MIN_CR = 1.15f

    override fun validate(theme: PresentationTheme): ThemeValidationResult {
        val warnings = mutableListOf<String>()

        val textPrimaryCr = ColorScience.contrastRatio(theme.textPrimary, theme.surface)
        val textSecondaryCr = ColorScience.contrastRatio(theme.textSecondary, theme.surface)
        val codeTextCr = ColorScience.contrastRatio(theme.codeText, theme.codeBackground)
        val borderCr = ColorScience.contrastRatio(theme.cardBorder, theme.surface)

        // The AA threshold comes from ColorScience rather than being re-typed here. It was
        // written out as a bare `4.5f` at two sites while `ColorScience.isWcagAa` — which
        // exists precisely to answer this — went uncalled: two sources of truth for the one
        // number this product's accessibility claim rests on.
        if (!ColorScience.isWcagAa(theme.textPrimary, theme.surface)) {
            warnings.add("Theme '${theme.name}': Primary text contrast ratio ($textPrimaryCr:1) is below WCAG AA (4.5:1).")
        }

        // Large/secondary text is held to the 3:1 large-text threshold, not AA body text.
        if (!ColorScience.isWcagAa(theme.textSecondary, theme.surface, minCr = LARGE_TEXT_MIN_CR)) {
            warnings.add("Theme '${theme.name}': Secondary text contrast ratio ($textSecondaryCr:1) is below minimum readable threshold (3.0:1).")
        }

        if (!ColorScience.isWcagAa(theme.codeText, theme.codeBackground)) {
            warnings.add("Theme '${theme.name}': Code text on code background contrast ratio ($codeTextCr:1) is below WCAG AA (4.5:1).")
        }

        if (borderCr < BORDER_MIN_CR) {
            warnings.add("Theme '${theme.name}': Card border has insufficient separation from surface ($borderCr:1).")
        }

        return ThemeValidationResult(
            isValid = warnings.isEmpty(),
            warnings = warnings,
            textPrimaryContrast = textPrimaryCr,
            textSecondaryContrast = textSecondaryCr,
            codeTextContrast = codeTextCr
        )
    }
}
