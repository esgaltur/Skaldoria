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
import com.skaldoria.shared.ui.theme.Themes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /**
     * Focus mode used to hide the control that turned it on, leaving Esc and F11 as the only exits
     * and nothing on screen to say so.
     */
    @Test
    fun focusModeOffersAVisibleWayOut() = runComposeUiTest {
        val state = WriterState("Focus")
        setWriterContent(state)

        onNodeWithTag(WriterTestTags.FocusExit).assertDoesNotExist()

        onNodeWithTag(WriterTestTags.FocusToggle).performClick()
        waitForIdle()
        assertTrue(state.isFocusMode)

        onNodeWithTag(WriterTestTags.FocusExit).assertIsDisplayed()
        onNodeWithTag(WriterTestTags.FocusExit).performClick()
        waitForIdle()

        assertFalse(state.isFocusMode, "the exit chip must leave focus mode")
        onNodeWithTag(WriterTestTags.viewMode(ViewMode.Edit)).assertIsDisplayed()
    }

    /** The writing row is about the editor pane, so it has nothing to offer in Preview. */
    @Test
    fun previewHidesTheWritingRowButKeepsTheLayoutSwitch() = runComposeUiTest {
        val state = WriterState("# Title\n\nBody")
        setWriterContent(state)

        onNodeWithTag(WriterTestTags.format(WriterFormat.Bold)).assertIsDisplayed()
        onNodeWithTag(WriterTestTags.editingMode(EditingMode.Visual)).assertIsDisplayed()

        onNodeWithTag(WriterTestTags.viewMode(ViewMode.Preview)).performClick()
        waitForIdle()

        onNodeWithTag(WriterTestTags.format(WriterFormat.Bold)).assertDoesNotExist()
        onNodeWithTag(WriterTestTags.editingMode(EditingMode.Visual)).assertDoesNotExist()
        onNodeWithTag(WriterTestTags.viewMode(ViewMode.Edit)).assertIsDisplayed()
    }

    @Test
    fun themeCanBePickedDirectlyRatherThanCycled() {
        val state = WriterState("x")
        val first = state.theme.name

        state.selectTheme(Themes.all.lastIndex)
        assertEquals(Themes.all.last().name, state.theme.name)

        state.selectTheme(0)
        assertEquals(Themes.all.first().name, state.theme.name)
        assertEquals(first, state.theme.name, "index 0 is where the writer starts")

        // Out-of-range selections are ignored rather than crashing the toolbar.
        state.selectTheme(-1)
        state.selectTheme(Themes.all.size)
        assertEquals(Themes.all.first().name, state.theme.name)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setWriterContent(state: WriterState) {
        setContent { WriterEditor(state = state) }
        waitForIdle()
        onNodeWithTag(WriterTestTags.Root).assertIsDisplayed()
    }
}
