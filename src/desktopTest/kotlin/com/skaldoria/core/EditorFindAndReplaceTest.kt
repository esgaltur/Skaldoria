package com.skaldoria.core

import com.skaldoria.state.PresentationState
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.ui.editor.MarkdownVisualTransformation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorFindAndReplaceTest {

    @Test
    fun testFindMatchesCaseInsensitiveAndSensitive() {
        val state = PresentationState()
        state.updateMarkdown("# Kotlin Engine\nKotlin is fast, kotlin is modern.\nKOTLIN rocks!")

        state.findQuery = "kotlin"
        state.isFindCaseSensitive = false
        assertEquals(4, state.findMatches.size)

        state.isFindCaseSensitive = true
        assertEquals(1, state.findMatches.size) // only lowercase "kotlin"
    }

    @Test
    fun testFindMatchesWholeWord() {
        val state = PresentationState()
        state.updateMarkdown("cat concatenate caterpillar cat")

        state.findQuery = "cat"
        state.isFindWholeWord = false
        assertEquals(4, state.findMatches.size)

        state.isFindWholeWord = true
        assertEquals(2, state.findMatches.size)
    }

    @Test
    fun testFindMatchesRegex() {
        val state = PresentationState()
        state.updateMarkdown("Slide 1: Alpha\nSlide 2: Beta\nSlide 42: Omega")

        state.findQuery = "Slide \\d+"
        state.isFindRegex = true
        assertEquals(3, state.findMatches.size)
    }

    @Test
    fun testFindNextAndPreviousCycling() {
        val state = PresentationState()
        state.updateMarkdown("alpha beta alpha gamma alpha")
        state.findQuery = "alpha"

        assertEquals(3, state.findMatches.size)
        assertEquals(0, state.currentMatchIndex)

        state.findNext()
        assertEquals(1, state.currentMatchIndex)

        state.findNext()
        assertEquals(2, state.currentMatchIndex)

        state.findNext() // cycles back to 0
        assertEquals(0, state.currentMatchIndex)

        state.findPrevious() // cycles back to 2
        assertEquals(2, state.currentMatchIndex)
    }

    @Test
    fun testReplaceCurrentAndReplaceAll() {
        val state = PresentationState()
        state.updateMarkdown("apple banana apple cherry apple")
        state.findQuery = "apple"
        state.replaceQuery = "orange"

        assertEquals(3, state.findMatches.size)
        state.replaceCurrent() // replaces first apple

        assertEquals("orange banana apple cherry apple", state.currentEditorText)
        assertEquals(2, state.findMatches.size)

        state.replaceAll() // replaces remaining apples
        assertEquals("orange banana orange cherry orange", state.currentEditorText)
        assertEquals(0, state.findMatches.size)
    }

    @Test
    fun testMarkdownVisualTransformationWithSearchHighlight() {
        val theme = BuiltinThemes.SkaldoriaDark
        val text = "Find this word in the editor"
        val query = "word"
        val matchStart = text.indexOf(query)
        val matchEnd = matchStart + query.length - 1
        val matches = listOf(matchStart..matchEnd)

        val annotated = MarkdownVisualTransformation.highlightMarkdown(
            text = text,
            theme = theme,
            searchMatches = matches,
            activeMatchIndex = 0
        )

        assertEquals(text, annotated.text)
        assertTrue(annotated.spanStyles.isNotEmpty())
    }
}
