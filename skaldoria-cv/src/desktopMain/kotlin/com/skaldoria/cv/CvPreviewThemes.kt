package com.skaldoria.cv

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.skaldoria.cv.core.CvThemeId

data class CvPreviewTheme(
    val themeId: CvThemeId,
    val pageColor: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val divider: Color,
    val bodyFont: FontFamily,
    val headingFont: FontFamily,
    val uppercaseSections: Boolean
)

object CvPreviewThemes {
    val all: List<CvPreviewTheme> = listOf(
        CvPreviewTheme(
            themeId = CvThemeId.AtsClassic,
            pageColor = Color.White,
            primaryText = Color(0xFF161616),
            secondaryText = Color(0xFF454545),
            accent = Color(0xFF161616),
            divider = Color(0xFF737373),
            bodyFont = FontFamily.Serif,
            headingFont = FontFamily.Serif,
            uppercaseSections = false
        ),
        CvPreviewTheme(
            themeId = CvThemeId.ModernBlue,
            pageColor = Color.White,
            primaryText = Color(0xFF172B4D),
            secondaryText = Color(0xFF44546F),
            accent = Color(0xFF0755A3),
            divider = Color(0xFF8FB8E6),
            bodyFont = FontFamily.SansSerif,
            headingFont = FontFamily.SansSerif,
            uppercaseSections = true
        ),
        CvPreviewTheme(
            themeId = CvThemeId.Graphite,
            pageColor = Color(0xFFFCFCFC),
            primaryText = Color(0xFF202124),
            secondaryText = Color(0xFF4F5358),
            accent = Color(0xFF343A40),
            divider = Color(0xFFADB5BD),
            bodyFont = FontFamily.SansSerif,
            headingFont = FontFamily.Monospace,
            uppercaseSections = true
        ),
        CvPreviewTheme(
            themeId = CvThemeId.Forest,
            pageColor = Color(0xFFFEFFFE),
            primaryText = Color(0xFF19352B),
            secondaryText = Color(0xFF405B51),
            accent = Color(0xFF246B50),
            divider = Color(0xFF9DC8B6),
            bodyFont = FontFamily.Serif,
            headingFont = FontFamily.SansSerif,
            uppercaseSections = false
        ),
        CvPreviewTheme(
            themeId = CvThemeId.WarmMinimal,
            pageColor = Color(0xFFFFFEFC),
            primaryText = Color(0xFF382A25),
            secondaryText = Color(0xFF62504A),
            accent = Color(0xFF8A4828),
            divider = Color(0xFFD7AA91),
            bodyFont = FontFamily.SansSerif,
            headingFont = FontFamily.Serif,
            uppercaseSections = false
        )
    )

    fun resolve(themeId: CvThemeId): CvPreviewTheme =
        checkNotNull(all.firstOrNull { it.themeId == themeId }) {
            "No preview theme is wired for ${themeId.displayName}"
        }
}
