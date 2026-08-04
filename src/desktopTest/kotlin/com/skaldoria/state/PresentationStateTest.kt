package com.skaldoria.state

import com.skaldoria.core.models.AnnotationStroke
import com.skaldoria.theme.BuiltinThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresentationStateTest {

    @Test
    fun testNavigationAndClamping() {
        val state = PresentationState(
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
        val state = PresentationState()
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
    fun testPresenterAndFullscreenToggles() {
        val state = PresentationState()
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
        val state = PresentationState()
        assertEquals("Nord Dark", state.currentTheme.name)

        state.currentTheme = BuiltinThemes.CyberMidnight
        assertEquals("Cyber Midnight", state.currentTheme.name)
        assertTrue(state.currentTheme.isDark)
    }

    @Test
    fun testLaserAndPenToggles() {
        val state = PresentationState()
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
        val state = PresentationState()
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
        val state = PresentationState(
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
        assertEquals(com.skaldoria.core.models.PacingStatus.OFF, state.pacingStatus)

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
}
