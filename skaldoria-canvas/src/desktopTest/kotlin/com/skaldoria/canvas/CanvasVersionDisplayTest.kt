package com.skaldoria.canvas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.canvas.ui.CanvasToolbar
import com.skaldoria.theme.BuiltinThemes
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies the application version is surfaced in the UI, and that it is sourced from the
 * generated [BuildInfo] single source of truth (not a hard-coded literal).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class CanvasVersionDisplayTest {

    @Test
    fun buildInfoDisplayVersionIsSemanticAndPrefixed() {
        assertTrue(
            Regex("""^v\d+\.\d+\.\d+$""").matches(BuildInfo.DISPLAY_VERSION),
            "expected a v-prefixed semantic version, got '${BuildInfo.DISPLAY_VERSION}'"
        )
        assertTrue(BuildInfo.DISPLAY_VERSION == "v${BuildInfo.VERSION}")
    }

    @Test
    fun toolbarShowsAppVersion() = runComposeUiTest {
        val state = CanvasState()
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = BuiltinThemes.SkaldoriaDark.background) {
                CanvasToolbar(
                    state = state,
                    currentTheme = BuiltinThemes.SkaldoriaDark,
                    onThemeSelected = {},
                    showMinimap = true,
                    onToggleMinimap = {},
                    onExportDeck = {},
                    onExportDocument = {},
                    onNewDocument = {},
                    onSaveDocument = {},
                    onOpenDocument = {},
                    screenWidth = 1280f,
                    screenHeight = 820f
                )
            }
        }
        waitForIdle()

        onNodeWithText(BuildInfo.DISPLAY_VERSION).assertIsDisplayed()
    }
}
