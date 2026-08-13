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

@OptIn(ExperimentalComposeUiApi::class)
class CvFontRenderingTest {
    @Test
    fun `bundled and installed font selections change rendered pixels`() {
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
        val georgia: ByteArray
        try {
            roboto = scene.render(0L).encodeToData()?.bytes ?: error("Could not encode Roboto preview")
            selectedFont.value = CvFontId.Georgia
            georgia = scene.render(16_000_000L).encodeToData()?.bytes ?: error("Could not encode Georgia preview")
        } finally {
            scene.close()
        }

        assertFalse(roboto.contentEquals(georgia), "Bundled Roboto and Georgia rendered identically")
    }
}
