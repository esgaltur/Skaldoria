package com.skaldoria.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.skaldoria.PresentationStateTestBase
import com.skaldoria.core.ports.FileDialogs
import com.skaldoria.ui.screens.PresenterView
import com.skaldoria.ui.screens.PresenterViewTags
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TalkReportButtonTest : PresentationStateTestBase() {

    /** Records the report and never reports a path, so nothing is handed to the OS to open. */
    private class CapturingDialogs : FileDialogs {
        var saved: String? = null
        override fun openFileOrProject(onChosen: (File) -> Unit) = error("not used")
        override fun saveMarkdownFile(currentPath: String?, content: String, onSaved: (String) -> Unit) = error("not used")
        override fun saveAsMarkdownFile(content: String, onSaved: (String) -> Unit) { saved = content }
    }

    @Test
    fun `the speaker console writes the talk report`() = runComposeUiTest {
        val dialogs = CapturingDialogs()
        val state = presentationState(initialMarkdown = "# Kickoff\n---\n# Next", fileDialogs = dialogs)
        setContent { PresenterView(state = state, onClose = {}) }

        onNodeWithTag(PresenterViewTags.TalkReportButton).performClick()

        assertTrue(dialogs.saved.orEmpty().startsWith("# Talk report — Kickoff"), dialogs.saved)
    }
}
