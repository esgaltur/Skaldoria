package com.skaldoria.cv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.skaldoria.cv.core.CvFontId
import com.skaldoria.cv.core.CvMarkdownAdapter
import com.skaldoria.cv.core.CvTemplateId
import com.skaldoria.cv.core.CvThemeId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class CvFontRenderingTest {
    @Test
    fun `font selection renders an installed face or the documented bundled fallback`() {
        val document = CvMarkdownAdapter().parse(
            """# Typography WWW iii
                |
                |## Experience
                |
                |### Senior Software Engineer
                |
                |- Built reliable Kotlin systems with measurable customer outcomes.
            """.trimMargin()
        )

        val installedAlternative = CvFontId.entries
            .asSequence()
            .filterNot { it == CvFontId.Roboto }
            .map { it to CvFontProgram.load(it) }
            .firstOrNull { (_, loadedFont) -> loadedFont.notice == null }
        val alternativeFontId = installedAlternative?.first ?: CvFontId.Georgia

        val selectedFont = mutableStateOf(CvFontId.Roboto)
        val scene = ImageComposeScene(width = 900, height = 1000, density = Density(1f)) {
            CvPreview(
                document = document,
                templateId = CvTemplateId.SoftwareEngineerAts,
                themeId = CvThemeId.ModernBlue,
                fontId = selectedFont.value,
                modifier = Modifier.fillMaxSize()
            )
        }
        val roboto: ByteArray
        val alternative: ByteArray
        try {
            roboto = scene.render(0L).encodeToData()?.bytes ?: error("Could not encode Roboto preview")
            selectedFont.value = alternativeFontId
            alternative = scene.render(16_000_000L).encodeToData()?.bytes
                ?: error("Could not encode ${alternativeFontId.displayName} preview")
        } finally {
            scene.close()
        }

        if (installedAlternative != null) {
            assertFalse(
                roboto.contentEquals(alternative),
                "Bundled Roboto and installed ${alternativeFontId.displayName} rendered identically"
            )
        } else {
            val fallbackNotice = CvFontProgram.load(alternativeFontId).notice
            assertNotNull(fallbackNotice, "The unavailable font must explain its Roboto substitution")
            assertTrue(
                roboto.contentEquals(alternative),
                "An unavailable font selection must render with the bundled Roboto fallback"
            )
        }
    }
}
