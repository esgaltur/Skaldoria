package com.skaldoria.cv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
class CvZoomRenderingTest {
    @Test
    fun `zoom changes the page inside one stable preview scene`() {
        val document = CvMarkdownAdapter().parse("# Candidate\n\n## Skills\n\n- Kotlin\n- Compose")
        val zoomPercent = mutableStateOf(100)
        val scene = ImageComposeScene(width = 1000, height = 1000, density = Density(1f)) {
            CvPreview(
                layout = remember {
                    resolveCvLayout(
                        document = document,
                        templateId = CvTemplateId.SoftwareEngineerAts,
                        themeId = CvThemeId.ModernBlue,
                        fontId = CvFontId.Roboto
                    )
                },
                zoomPercent = zoomPercent.value,
                modifier = Modifier.fillMaxSize()
            )
        }
        val actualSize: ByteArray
        val zoomed: ByteArray
        try {
            actualSize = scene.render(0L).encodeToData()?.bytes ?: error("Could not encode 100% preview")
            zoomPercent.value = 150
            zoomed = scene.render(16_000_000L).encodeToData()?.bytes ?: error("Could not encode 150% preview")
        } finally {
            scene.close()
        }

        assertFalse(actualSize.contentEquals(zoomed), "100% and 150% previews rendered identically")
    }
}
