package com.skaldoria.writer

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class WriterEditorInteractionTest {

    @Test
    fun editorAcceptsRealComposeTextInput() = runComposeUiTest {
        val state = WriterState("")
        setWriterContent(state)

        onNodeWithTag(WriterTestTags.Editor).performClick()
        onNodeWithTag(WriterTestTags.Editor).performTextInput("Working editor")
        waitForIdle()

        assertEquals("Working editor", state.text)
        assertTrue(state.isDirty)
    }

    @Test
    fun formattingToolbarMutatesSelectedMarkdown() = runComposeUiTest {
        val state = WriterState("bold me")
        state.updateText(TextFieldValue("bold me", TextRange(0, 7)))
        setWriterContent(state)

        onNodeWithTag(WriterTestTags.format(WriterFormat.Bold)).performClick()
        waitForIdle()

        assertEquals("**bold me**", state.text)
    }

    @Test
    fun splitAndPreviewModesRenderParsedDocument() = runComposeUiTest {
        val state = WriterState("# Rendered heading\n\nBody")
        setWriterContent(state)

        onNodeWithTag(WriterTestTags.viewMode(ViewMode.Split)).performClick()
        waitForIdle()
        onNodeWithTag(WriterTestTags.Editor).assertIsDisplayed()
        onNodeWithTag(WriterTestTags.Preview).assertIsDisplayed()

        onNodeWithTag(WriterTestTags.viewMode(ViewMode.Preview)).performClick()
        waitForIdle()
        onNodeWithTag(WriterTestTags.Editor).assertDoesNotExist()
        onNodeWithTag(WriterTestTags.Preview).assertIsDisplayed()
    }

    @Test
    fun clickingOutlineHeadingMovesSelectionIntoSource() = runComposeUiTest {
        val state = WriterState("# First\n\n## Target heading\n\nBody")
        setWriterContent(state)

        onNodeWithTag(WriterTestTags.heading(1)).performClick()
        waitForIdle()

        assertEquals(
            "Target heading",
            state.textValue.annotatedString.subSequence(
                state.textValue.selection.start,
                state.textValue.selection.end
            ).text
        )
    }

    @Test
    fun focusModeKeepsEditorAvailableAndRemovesApplicationChrome() = runComposeUiTest {
        val state = WriterState("Focus")
        setWriterContent(state)

        onNodeWithTag(WriterTestTags.FocusToggle).performClick()
        waitForIdle()

        assertTrue(state.isFocusMode)
        onNodeWithTag(WriterTestTags.Editor).assertIsDisplayed()
        onNodeWithTag(WriterTestTags.Outline).assertDoesNotExist()
        onNodeWithTag(WriterTestTags.viewMode(ViewMode.Edit)).assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setWriterContent(state: WriterState) {
        setContent { WriterEditor(state = state) }
        waitForIdle()
        onNodeWithTag(WriterTestTags.Root).assertIsDisplayed()
    }
}
