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

    override fun validate(theme: PresentationTheme): ThemeValidationResult {
        val warnings = mutableListOf<String>()

        val textPrimaryCr = ColorScience.contrastRatio(theme.textPrimary, theme.surface)
        val textSecondaryCr = ColorScience.contrastRatio(theme.textSecondary, theme.surface)
        val codeTextCr = ColorScience.contrastRatio(theme.codeText, theme.codeBackground)
        val borderCr = ColorScience.contrastRatio(theme.cardBorder, theme.surface)

        if (textPrimaryCr < 4.5f) {
            warnings.add("Theme '${theme.name}': Primary text contrast ratio ($textPrimaryCr:1) is below WCAG AA (4.5:1).")
        }

        if (textSecondaryCr < 3.0f) {
            warnings.add("Theme '${theme.name}': Secondary text contrast ratio ($textSecondaryCr:1) is below minimum readable threshold (3.0:1).")
        }

        if (codeTextCr < 4.5f) {
            warnings.add("Theme '${theme.name}': Code text on code background contrast ratio ($codeTextCr:1) is below WCAG AA (4.5:1).")
        }

        if (borderCr < 1.15f) {
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
