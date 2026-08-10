package com.skaldoria.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.skaldoria.PresentationStateTestBase
import com.skaldoria.core.ports.FileDialogs
import com.skaldoria.ui.screens.EditorWorkspace
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNull

/** Interaction guards for studio behavior that a state-only test cannot prove is reachable. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class EditorWorkspaceInteractionTest : PresentationStateTestBase() {

    @Test
    fun `a general application error is visible and dismissible`() = runComposeUiTest {
        val failingDialogs = object : FileDialogs {
            override fun openFileOrProject(onChosen: (File) -> Unit) = Unit
            override fun saveMarkdownFile(
                currentPath: String?,
                content: String,
                onSaved: (String) -> Unit
            ) = error("disk is read-only")
            override fun saveAsMarkdownFile(content: String, onSaved: (String) -> Unit) =
                error("disk is read-only")
        }
        val state = presentationState(fileDialogs = failingDialogs).apply {
            showWelcome = false
            saveFile()
        }
        setContent { EditorWorkspace(state) }

        onNodeWithText("Presentation error").assertIsDisplayed()
        onNodeWithText("Could not save the presentation: disk is read-only").assertIsDisplayed()
        onNodeWithText("Dismiss").performClick()
        waitForIdle()

        assertNull(state.lastError)
    }
}
