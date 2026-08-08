package com.skaldoria.state

import com.skaldoria.PresentationStateTestBase
import com.skaldoria.core.models.AnnotationStroke
import com.skaldoria.theme.BuiltinThemes
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresentationStateTest : PresentationStateTestBase() {

    @Test
    fun testNavigationAndClamping() {
        val state = presentationState(
            initialMarkdown = """
                # Slide 1
                ---
                # Slide 2
                ---
                # Slide 3
            """.trimIndent()
        )

        assertEquals(3, state.slides.size)
        assertEquals(0, state.currentSlideIndex)
        assertTrue(state.hasNext)
        assertFalse(state.hasPrev)

        // Navigate forward
        state.next()
        assertEquals(1, state.currentSlideIndex)
        assertTrue(state.hasNext)
        assertTrue(state.hasPrev)

        state.next()
        assertEquals(2, state.currentSlideIndex)
        assertFalse(state.hasNext)

        // Clamping at end
        state.next()
        assertEquals(2, state.currentSlideIndex)

        // Navigate backwards
        state.prev()
        assertEquals(1, state.currentSlideIndex)
        state.prev()
        assertEquals(0, state.currentSlideIndex)

        // Clamping at beginning
        state.prev()
        assertEquals(0, state.currentSlideIndex)

        // Jump to index
        state.goToSlide(2)
        assertEquals(2, state.currentSlideIndex)
        state.goToSlide(999) // Out of bounds should not change
        assertEquals(2, state.currentSlideIndex)
        state.goToSlide(-5) // Out of bounds should not change
        assertEquals(2, state.currentSlideIndex)
    }

    @Test
    fun testEditorFontSizeScaling() {
        val state = presentationState()
        assertEquals(14, state.editorFontSize)

        state.increaseEditorFontSize()
        assertEquals(16, state.editorFontSize)

        state.increaseEditorFontSize()
        assertEquals(18, state.editorFontSize)

        state.decreaseEditorFontSize()
        assertEquals(16, state.editorFontSize)

        state.resetEditorFontSize()
        assertEquals(14, state.editorFontSize)
    }

    @Test
    fun testFormattingAtCaretPlacesCaretInsideMarkers() {
        val state = presentationState(initialMarkdown = "Hello")
        state.onEditorSelectionChanged(TextRange(5))

        state.formatSelection("**")

        assertEquals("Hello****", state.currentEditorText)
        assertEquals(TextRange(7), state.editorSelection)
    }

    @Test
    fun testFormattingSelectionTogglesMarkers() {
        val state = presentationState(initialMarkdown = "Hello world")
        state.onEditorSelectionChanged(TextRange(6, 11))

        state.formatSelection("**")
        assertEquals("Hello **world**", state.currentEditorText)
        assertEquals(TextRange(8, 13), state.editorSelection)

        state.formatSelection("**")
        assertEquals("Hello world", state.currentEditorText)
        assertEquals(TextRange(6, 11), state.editorSelection)
    }

    @Test
    fun testPresenterAndFullscreenToggles() {
        val state = presentationState()
        assertFalse(state.isFullscreen)
        assertFalse(state.isPresenterModeActive)

        state.startPresenting(presenterMode = false)
        assertTrue(state.isFullscreen)
        assertFalse(state.isPresenterModeActive)
        assertTrue(state.isTimerRunning)

        state.isFullscreen = false
        state.isPresenterModeActive = false

        state.startPresenting(presenterMode = true)
        assertTrue(state.isPresenterModeActive)
        assertTrue(state.isFullscreen)
    }

    @Test
    fun testThemeAssignment() {
        val state = presentationState()
        assertEquals("Skaldoria Dark", state.currentTheme.name)

        state.currentTheme = BuiltinThemes.CyberMidnight
        assertEquals("Cyber Midnight", state.currentTheme.name)
        assertTrue(state.currentTheme.isDark)
    }

    @Test
    fun testLaserAndPenToggles() {
        val state = presentationState()
        assertFalse(state.isLaserPointerActive)
        assertFalse(state.isPenDrawingActive)

        state.toggleLaserPointer()
        assertTrue(state.isLaserPointerActive)
        assertFalse(state.isPenDrawingActive)

        state.togglePenDrawing()
        assertTrue(state.isPenDrawingActive)
        assertFalse(state.isLaserPointerActive)
    }

    @Test
    fun testAnnotationsManagement() {
        val state = presentationState()
        assertEquals(0, state.currentSlideStrokes.size)

        val stroke = AnnotationStroke(points = emptyList())
        state.addStroke(stroke)
        assertEquals(1, state.currentSlideStrokes.size)

        state.undoStroke()
        assertEquals(0, state.currentSlideStrokes.size)

        state.addStroke(stroke)
        state.clearAnnotations()
        assertEquals(0, state.currentSlideStrokes.size)
    }

    @Test
    fun testTargetDurationAndPacingCalculations() {
        val state = presentationState(
            initialMarkdown = """
                # Slide 1
                ---
                # Slide 2
                ---
                # Slide 3
                ---
                # Slide 4
            """.trimIndent()
        )

        assertEquals(4, state.slides.size)
        assertEquals(com.skaldoria.markdown.models.PacingStatus.OFF, state.pacingStatus)

        // Set target talk duration: 20 minutes = 1200 seconds -> 300s per slide
        state.setTargetDuration(20)
        assertEquals(20, state.targetTalkDurationMinutes)
        assertEquals(1200L, state.targetTotalSeconds)
        assertEquals(300L, state.targetSecondsPerSlide)
        assertEquals(0L, state.idealElapsedSecondsAtCurrentSlide)

        // Move to slide 2 (index 1) -> ideal elapsed = 300s
        state.goToSlide(1)
        assertEquals(300L, state.idealElapsedSecondsAtCurrentSlide)

        // Move to slide 4 (index 3) -> ideal elapsed = 900s
        state.goToSlide(3)
        assertEquals(900L, state.idealElapsedSecondsAtCurrentSlide)
    }

    @Test
    fun testCorporateThemeUnlocking() {
        val state = presentationState()
        assertFalse(state.isCorporateThemeUnlocked)
        assertEquals(4, state.availableThemes.size)
        assertFalse(state.availableThemes.any { it.id == "deutsche-borse" })

        // Attempt invalid code
        val failed = state.unlockCorporateTheme("wrong_code")
        assertFalse(failed)
        assertFalse(state.isCorporateThemeUnlocked)

        // Attempt valid code
        val success = state.unlockCorporateTheme("DB_CORP_2026")
        assertTrue(success)
        assertTrue(state.isCorporateThemeUnlocked)
        assertEquals("Deutsche Börse", state.currentTheme.name)
        assertEquals(5, state.availableThemes.size)
        assertTrue(state.availableThemes.any { it.id == "deutsche-borse" })

        // Lock corporate themes again
        state.lockCorporateTheme()
        assertFalse(state.isCorporateThemeUnlocked)
        assertEquals("Skaldoria Dark", state.currentTheme.name)
        assertEquals(4, state.availableThemes.size)
    }
}
