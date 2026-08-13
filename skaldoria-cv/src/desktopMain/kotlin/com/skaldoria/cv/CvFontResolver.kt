package com.skaldoria.cv

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import com.skaldoria.cv.core.CvFontId
import java.awt.Font
import java.awt.GraphicsEnvironment

data class ResolvedCvFont(
    val family: FontFamily,
    val resolvedName: String,
    val isFallback: Boolean,
    val isBundled: Boolean = false
)

object CvFontResolver {
    private val bundledRoboto: ResolvedCvFont by lazy { loadBundledRoboto() }

    private val installedFamilies: Map<String, String> by lazy {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
            .associateBy { it.lowercase() }
    }

    @OptIn(ExperimentalTextApi::class)
    fun resolve(fontId: CvFontId): ResolvedCvFont {
        if (fontId == CvFontId.Roboto) return bundledRoboto
        if (fontId == CvFontId.SystemSans) {
            return ResolvedCvFont(FontFamily.SansSerif, "System Sans", isFallback = false)
        }
        if (fontId == CvFontId.SystemSerif) {
            return ResolvedCvFont(FontFamily.Serif, "System Serif", isFallback = false)
        }
        val installedName = fontId.systemFamilyCandidates.firstNotNullOfOrNull { candidate ->
            installedFamilies[candidate.lowercase()]
        }
        if (installedName != null) {
            return ResolvedCvFont(FontFamily(installedName), installedName, isFallback = false)
        }
        val fallback = if (fontId.serifFallback) FontFamily.Serif else FontFamily.SansSerif
        return ResolvedCvFont(fallback, if (fontId.serifFallback) "System Serif" else "System Sans", isFallback = true)
    }

    @OptIn(ExperimentalTextApi::class)
    private fun loadBundledRoboto(): ResolvedCvFont {
        val graphics = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val fonts = listOf("/fonts/Roboto.ttf", "/fonts/Roboto-Italic.ttf").map { resource ->
            val stream = checkNotNull(CvFontResolver::class.java.getResourceAsStream(resource)) {
                "Bundled font resource is missing: $resource"
            }
            stream.use { Font.createFont(Font.TRUETYPE_FONT, it) }
        }
        fonts.forEach(graphics::registerFont)
        val familyName = fonts.first().family
        return ResolvedCvFont(
            family = FontFamily(familyName),
            resolvedName = "$familyName (bundled)",
            isFallback = false,
            isBundled = true
        )
    }
}
